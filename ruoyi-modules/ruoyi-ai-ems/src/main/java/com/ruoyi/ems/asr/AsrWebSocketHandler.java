package com.ruoyi.ems.asr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.asr.model.AsrClient;
import com.ruoyi.ems.dispatcher.SessionCommandDispatcher;
import com.ruoyi.ems.domain.dto.CommandTask;
import com.ruoyi.ems.service.EvaluationService;
import com.ruoyi.ems.service.SessionService;
import com.ruoyi.ems.tts.KokoroTtsClient;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ServerEndpoint("/ws/asr/{sessionId}")
public class AsrWebSocketHandler {

    // ---------- 静态注入的 Spring Bean ----------
    private static AsrClient asrClient;
    private static SessionService sessionService;
    private static EvaluationService evaluationService;
    private static SessionCommandDispatcher dispatcher;
    private static KokoroTtsClient ttsClient;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 房间模型：sessionId -> (clientId -> WebSocket Session)
     */
    static final Map<String, Map<String, Session>> SESSIONS = new ConcurrentHashMap<>();

    // 用户属性 key
    private static final String ATTR_SESSION_ID = "sessionId";
    private static final String ATTR_CLIENT_ID  = "clientId";
    private static final String ATTR_ROLE       = "role";
    private static final String ATTR_TASK_ID    = "taskId";

    // 角色
    private static final String ROLE_SPEAKER = "speaker";
    private static final String ROLE_VIEWER  = "viewer";

    // ---------- 静态依赖注入 ----------
    public static void injectBeans(AsrClient ac,
                                   SessionService ss,
                                   EvaluationService es,
                                   SessionCommandDispatcher d,
                                   KokoroTtsClient tc) {
        asrClient = ac;
        sessionService = ss;
        evaluationService = es;
        dispatcher = d;
        ttsClient = tc;
    }

    // ---------- 对外暴露的 Session 访问方法 ----------

    public static Session getSession(String sessionId) {
        if (sessionId == null) return null;
        Map<String, Session> clients = SESSIONS.get(sessionId);
        if (clients == null || clients.isEmpty()) return null;

        Session fallback = null;
        for (Session s : clients.values()) {
            if (s == null || !s.isOpen()) continue;
            Object role = s.getUserProperties().get(ATTR_ROLE);
            if (ROLE_SPEAKER.equals(role)) {
                return s;
            }
            if (fallback == null) fallback = s;
        }
        return fallback;
    }

    public static Map<String, Session> getClients(String sessionId) {
        Map<String, Session> clients = SESSIONS.get(sessionId);
        return clients != null ? clients : Map.of();
    }

    public static boolean isSessionOpen(String sessionId) {
        Map<String, Session> clients = SESSIONS.get(sessionId);
        if (clients == null || clients.isEmpty()) return false;
        for (Session s : clients.values()) {
            if (s != null && s.isOpen()) return true;
        }
        return false;
    }

    // ---------- WebSocket 生命周期 ----------
    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId) {
        log.info("WebSocket连接建立: sessionId={}, clientId={}, uri={}",
            sessionId, session.getId(), session.getRequestURI());

        // ---------- 1. 基础依赖检查 ----------
        if (asrClient == null) {
            log.error("❌ asrClient 未注入，请检查 AsrWebSocketConfig 是否生效");
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "服务未就绪");
            return;
        }

        // ---------- 2. 解析角色并写入用户属性 ----------
        String role = parseRole(session);

        session.getUserProperties().put(ATTR_SESSION_ID, sessionId);
        session.getUserProperties().put(ATTR_CLIENT_ID, session.getId());
        session.getUserProperties().put(ATTR_ROLE, role);

        // ---------- 3. 加入房间 ----------
        AtomicBoolean firstClient = new AtomicBoolean(false);
        SESSIONS.compute(sessionId, (k, clients) -> {
            if (clients == null) {
                clients = new ConcurrentHashMap<>();
                firstClient.set(true);
            }
            clients.put(session.getId(), session);
            return clients;
        });

        log.info("客户端加入房间: sessionId={}, clientId={}, role={}, 当前房间人数={}",
            sessionId, session.getId(), role, SESSIONS.get(sessionId).size());

        // ---------- 4. 尝试启动 ASR ----------
        // 说明：ASR 启动失败时，不关闭 WebSocket，仅记录 INFO 并保持连接。
        //       连接可用于 ping / manual_transcript / hint / interrupt 等文本功能。
        String taskId = null;
        boolean asrReady = false;
        try {
            if (firstClient.get()) {
                // 房间首个连接：负责启动 ASR
                if (!asrClient.isSessionActive(sessionId)) {
                    taskId = asrClient.startRealtimeRecognition(sessionId);
                    log.info("房间首个连接建立，新建 ASR 连接: sessionId={}, taskId={}", sessionId, taskId);
                } else {
                    taskId = "asr-task-" + sessionId;
                    log.info("复用 startSession 已建立的 ASR 连接: sessionId={}", sessionId);
                }

                if (taskId == null || taskId.isBlank()) {
                    throw new IllegalStateException("ASR 服务未返回有效 taskId");
                }

                // 注册广播回调（只注册一次，final 结果广播给房间所有人）
                asrClient.registerCallback(sessionId, (text, isFinal) -> {
                    if (!isFinal) return;
                    broadcast(sessionId, Map.of(
                        "type", "sentence",
                        "text", text,
                        "timestamp", System.currentTimeMillis()
                    ));
                });

                asrReady = true;

            } else {
                // 非首个连接：尝试复用房间已有的 ASR 连接
                if (asrClient.isSessionActive(sessionId)) {
                    taskId = "asr-task-" + sessionId;
                    asrReady = true;
                    log.info("复用房间已有 ASR 连接: sessionId={}, taskId={}", sessionId, taskId);
                } else {
                    log.info("ℹ️ 房间已有连接但 ASR 会话不可用，本连接保留: sessionId={}", sessionId);
                }
            }

            // ★ 只有拿到有效 taskId 才写入，保证 onAudioData 里 taskId != null 意味着 ASR 可用
            if (taskId != null) {
                session.getUserProperties().put(ATTR_TASK_ID, taskId);
            }

        } catch (AsrUnavailableException e) {
            // ★ ASR 网关不可达 —— 只 INFO，不关闭 WebSocket
            log.info("ℹ️ ASR 服务当前不可用，WebSocket 保持连接: sessionId={}, clientId={}, 原因: {}",
                sessionId, session.getId(), e.getMessage());
            // taskId 保持 null；asrReady 保持 false；回调未注册；
            // 后续 onAudioData 会静默丢弃音频帧

        } catch (Exception e) {
            // ★ 真正的异常才打 ERROR，但同样不关闭连接
            log.error("启动 ASR 会话失败，WebSocket 保持连接: sessionId={}, clientId={}",
                sessionId, session.getId(), e);
            // 同上：不写 taskId，不注册回调
        }

        // ---------- 5. 告知前端当前状态 ----------
        sendMessage(session, "connected", Map.of(
            "role", role,
            "sessionId", sessionId,
            "asrReady", asrReady,
            "taskId", taskId != null ? taskId : ""
        ));
    }

    @OnClose
    public void onClose(Session session) {
        String sessionId = (String) session.getUserProperties().get(ATTR_SESSION_ID);
        log.info("WebSocket连接关闭: sessionId={}, clientId={}", sessionId, session.getId());

        if (sessionId == null) return;

        Map<String, Session> clients = SESSIONS.get(sessionId);
        if (clients == null) return;

        clients.remove(session.getId());

        // ★ 房间空了才做资源清理
        if (clients.isEmpty()) {
            SESSIONS.remove(sessionId);
            log.info("房间已空，清理会话资源: sessionId={}", sessionId);

            try {
                asrClient.unregisterCallback(sessionId);
            } catch (Exception e) {
                log.error("注销回调失败: sessionId={}", sessionId, e);
            }

            if (dispatcher != null) {
                dispatcher.evictSession(sessionId);
            }
        } else {
            log.info("客户端离开房间: sessionId={}, clientId={}, 剩余人数={}",
                sessionId, session.getId(), clients.size());
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String sessionId = (String) session.getUserProperties().get(ATTR_SESSION_ID);
        log.error("WebSocket错误: sessionId={}, clientId={}, error={}",
            sessionId, session.getId(), error.getMessage(), error);
        sendError(session, "连接异常: " + error.getMessage());
    }

    // ---------- 消息处理 ----------
    @OnMessage
    public void onAudioData(byte[] audioData, Session session) {
        if (!isSpeaker(session)) {
            // 只 debug，避免刷屏
            if (log.isDebugEnabled()) {
                log.debug("非主控端尝试发送音频，已忽略: clientId={}", session.getId());
            }
            return;
        }

        String taskId = (String) session.getUserProperties().get(ATTR_TASK_ID);
        if (taskId == null) {
            // ★ 降为 debug，避免 ASR 未就绪时被音频帧刷爆日志
            if (log.isDebugEnabled()) {
                log.debug("ASR 未就绪，丢弃音频帧: sessionId={}, clientId={}, size={}",
                    session.getUserProperties().get(ATTR_SESSION_ID),
                    session.getId(),
                    audioData == null ? 0 : audioData.length);
            }
            return;
        }

        try {
            asrClient.sendAudio(taskId, audioData);
        } catch (Exception e) {
            log.error("发送音频到ASR失败: {}", e.getMessage());
            sendError(session, "ASR服务异常: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @OnMessage
    public void onTextMessage(String message, Session session) {
        String sessionId = (String) session.getUserProperties().get(ATTR_SESSION_ID);
        log.debug("收到文本消息: sessionId={}, clientId={}, message={}",
            sessionId, session.getId(), message);

        if ("END".equals(message)) {
            if (!isSpeaker(session)) {
                log.warn("非主控端发送 END，已忽略: clientId={}", session.getId());
                return;
            }
            String taskId = (String) session.getUserProperties().get(ATTR_TASK_ID);
            if (taskId != null) {
                log.info("收到 END 信号，停止 ASR: sessionId={}", sessionId);
                try {
                    asrClient.stopRealtimeRecognition(taskId);
                } catch (Exception e) {
                    log.error("停止 ASR 失败", e);
                }
            }
            return;
        }

        try {
            Map<String, Object> msg = objectMapper.readValue(message, Map.class);
            String type = (String) msg.get("type");
            if (type == null) {
                log.warn("消息缺少 type 字段: {}", message);
                return;
            }

            switch (type) {
                case "ping" -> sendMessage(session, "pong", null);

                case "control" -> {
                    if (!isSpeaker(session)) {
                        log.warn("非主控端尝试发送 control，已忽略: clientId={}", session.getId());
                        return;
                    }
                    String action = (String) msg.get("action");
                    handleControl(session, action);
                }

                case "hint" -> {
                    String state = (String) msg.get("state");
                    if (state != null && !state.isEmpty()) {
                        String hintText = resolveHintText(state);
                        broadcast(sessionId, Map.of(
                            "type", "hint",
                            "data", Map.of("text", hintText, "state", state),
                            "timestamp", System.currentTimeMillis()
                        ));
                    } else {
                        log.warn("hint 消息缺少 state 参数");
                    }
                }

                case "manual_transcript" -> {
                    if (!isSpeaker(session)) {
                        log.warn("非主控端尝试发送 manual_transcript，已忽略: clientId={}", session.getId());
                        return;
                    }
                    String text = (String) msg.get("text");
                    if (text == null || text.trim().isEmpty()) {
                        log.warn("manual_transcript 消息缺少文本内容");
                        sendError(session, "手动转写文本为空");
                        break;
                    }

                    CommandTask task = new CommandTask(
                        sessionId,
                        text,
                        session,
                        System.currentTimeMillis(),
                        0
                    );

                    if (dispatcher != null) {
                        dispatcher.submit(task, t -> {
                            try {
                                return sessionService.executeCommandSync(t);
                            } catch (Exception e) {
                                log.error("执行指令异常: sessionId={}", t.getSessionId(), e);
                                return "ERROR: " + e.getMessage();
                            }
                        });
                        broadcast(sessionId, Map.of(
                            "type", "queued",
                            "data", Map.of(
                                "status", "指令已接收，正在处理",
                                "queueSize", getQueueSize(sessionId),
                                "text", text
                            ),
                            "timestamp", System.currentTimeMillis()
                        ));
                    } else {
                        log.error("调度器未初始化，无法处理指令");
                        sendError(session, "系统未就绪");
                    }
                }

                case "interrupt" -> {
                    if (!isSpeaker(session)) {
                        log.warn("非主控端尝试发送 interrupt，已忽略: clientId={}", session.getId());
                        return;
                    }
                    if (dispatcher != null) {
                        boolean interrupted = dispatcher.interruptSession(sessionId);
                        if (ttsClient != null) {
                            ttsClient.stop(sessionId);
                        }
                        broadcast(sessionId, Map.of(
                            "type", "interrupted",
                            "data", Map.of(
                                "status", interrupted ? "已打断当前流程" : "无正在执行的任务"
                            ),
                            "timestamp", System.currentTimeMillis()
                        ));
                        log.info("🛑 会话 {} 被打断，结果: {}", sessionId, interrupted);
                    } else {
                        sendError(session, "调度器未就绪");
                    }
                }

                default -> log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理文本消息失败", e);
            sendError(session, "消息解析失败");
        }
    }

    // ---------- 内部辅助 ----------

    private static String parseRole(Session session) {
        try {
            Map<String, List<String>> params = session.getRequestParameterMap();
            if (params != null) {
                List<String> roles = params.get("role");
                if (roles != null && !roles.isEmpty()) {
                    String r = roles.get(0);
                    if (ROLE_VIEWER.equalsIgnoreCase(r)) return ROLE_VIEWER;
                    if (ROLE_SPEAKER.equalsIgnoreCase(r)) return ROLE_SPEAKER;
                }
            }
        } catch (Exception ignored) {}
        return ROLE_SPEAKER;
    }

    private static boolean isSpeaker(Session session) {
        Object role = session.getUserProperties().get(ATTR_ROLE);
        return role == null || ROLE_SPEAKER.equals(role);
    }

    private static void removeFromRoom(String sessionId, String clientId) {
        Map<String, Session> clients = SESSIONS.get(sessionId);
        if (clients == null) return;
        clients.remove(clientId);
        if (clients.isEmpty()) {
            SESSIONS.remove(sessionId);
        }
    }

    /** 静默关闭，忽略关闭过程中的异常 */
    private static void closeQuietly(Session session, CloseReason.CloseCode code, String reason) {
        if (session == null || !session.isOpen()) return;
        try {
            session.close(new CloseReason(code, reason));
        } catch (IOException ignored) {}
    }

    /**
     * 广播给房间内所有在线连接。
     */
    public static void broadcast(String sessionId, Map<String, Object> message) {
        if (sessionId == null || message == null) return;

        Map<String, Session> clients = SESSIONS.get(sessionId);
        if (clients == null || clients.isEmpty()) {
            log.debug("广播跳过：房间不存在或为空: sessionId={}", sessionId);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化广播消息失败", e);
            return;
        }

        for (Session s : clients.values()) {
            if (s == null) continue;
            if (!s.isOpen()) {
                clients.remove(s.getId());
                continue;
            }
            try {
                synchronized (s) {
                    s.getAsyncRemote().sendText(json, result -> {
                        if (!result.isOK()) {
                            log.error("广播失败: sessionId={}, clientId={}",
                                sessionId, s.getId(), result.getException());
                        }
                    });
                }
            } catch (Exception e) {
                log.error("广播异常: sessionId={}, clientId={}", sessionId, s.getId(), e);
            }
        }
    }

    private void sendMessage(Session session, String type, Object data) {
        try {
            Map<String, Object> message = Map.of(
                "type", type,
                "data", data != null ? data : "",
                "timestamp", System.currentTimeMillis()
            );
            synchronized (session) {
                session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
            }
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }

    private void sendError(Session session, String error) {
        sendMessage(session, "error", Map.of("message", error));
    }

    private static String resolveHintText(String state) {
        return switch (state) {
            case "INITIAL_ASSESSMENT" -> "请评估患者心率和脉搏状态";
            case "STABILITY_CHECK" -> "请评估患者血流动力学是否稳定";
            case "QRS_EVALUATION" -> "请判断QRS宽度是否小于0.12秒";
            case "VAGAL_MANEUVER" -> "建议尝试迷走神经刺激";
            case "ADENOSINE_6MG" -> "准备腺苷6mg快速IV推注";
            case "ADENOSINE_12MG" -> "准备腺苷12mg快速IV推注";
            case "SYNCHRONIZED_CARDIOVERSION" -> "患者不稳定，准备同步电复律";
            default -> "请继续执行当前步骤";
        };
    }

    private void handleControl(Session session, String action) {
        String sessionId = (String) session.getUserProperties().get(ATTR_SESSION_ID);
        log.info("收到控制指令: sessionId={}, action={}", sessionId, action);
        switch (action) {
            case "pause" -> asrClient.pause(sessionId);
            case "resume" -> asrClient.resume(sessionId);
            case "reset" -> asrClient.reset(sessionId);
            default -> log.warn("未知控制指令: {}", action);
        }
    }

    private int getQueueSize(String sessionId) {
        if (dispatcher == null) return 0;
        return dispatcher.getQueueSize(sessionId);
    }

    // ---------- 静态推送方法 ----------

    public static void pushTeamStatus(String sessionId, String statusType, Map<String, Object> data) {
        log.info("📤 [pushTeamStatus] 广播: sessionId={}, statusType={}", sessionId, statusType);
        broadcast(sessionId, Map.of(
            "type", "team_status",
            "statusType", statusType,
            "data", data != null ? data : Map.of(),
            "timestamp", System.currentTimeMillis()
        ));
    }

    public static void pushHint(String sessionId, String hint) {
        log.info("📤 [pushHint] 广播: sessionId={}, hint={}", sessionId, hint);
        broadcast(sessionId, Map.of(
            "type", "teamMsg",
            "text", hint,
            "timestamp", System.currentTimeMillis()
        ));
    }

    public static void pushStateChange(String sessionId, String from, String to, String event) {
        broadcast(sessionId, Map.of(
            "type", "state_change",
            "from", from,
            "to", to,
            "event", event,
            "timestamp", System.currentTimeMillis()
        ));
    }
}

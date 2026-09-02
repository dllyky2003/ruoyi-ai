package com.ruoyi.ems.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.engine.asr.AsrClient;
import com.ruoyi.ems.service.EvaluationService;
import com.ruoyi.ems.service.SessionService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/ws/asr/{sessionId}")
public class AsrWebSocketHandler {

    private static AsrClient asrClient;
    private static SessionService sessionService;
    private static EvaluationService evaluationService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    @Autowired
    public static void setAsrClient(AsrClient asrClient) {
        AsrWebSocketHandler.asrClient = asrClient;
    }

    @Autowired
    @Lazy
    public void setSessionService(SessionService sessionService) {
        AsrWebSocketHandler.sessionService = sessionService;
    }

    @Autowired
    @Lazy
    public void setEvaluationService(EvaluationService evaluationService) {
        AsrWebSocketHandler.evaluationService = evaluationService;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId) {
        log.info("WebSocket连接建立: sessionId={}, 客户端={}", sessionId, session.getRequestURI());
        SESSIONS.put(sessionId, session);
        session.getUserProperties().put("sessionId", sessionId);

        String taskId = asrClient.startRealtimeRecognition(sessionId);
        session.getUserProperties().put("taskId", taskId);

        asrClient.registerCallback(sessionId, (text, isFinal) -> {
            try {
                sendTranscript(session, text, isFinal);
            } catch (IOException e) {
                log.error("发送转写结果失败", e);
            }
        });

        sendMessage(session, "connected", "ASR服务已就绪");
    }

    // ★ 增强调试日志的推送方法
    public static void pushTeamStatus(String sessionId, String statusType, Map<String, Object> data) {
        log.info("📤 [pushTeamStatus] 尝试推送: sessionId={}, statusType={}, data={}",
            sessionId, statusType, data != null ? data : "null");

        Session session = SESSIONS.get(sessionId);
        if (session == null) {
            log.warn("⚠️ [pushTeamStatus] 会话不存在: sessionId={}, 当前在线会话: {}",
                sessionId, SESSIONS.keySet());
            return;
        }

        if (!session.isOpen()) {
            log.warn("⚠️ [pushTeamStatus] 会话已关闭: sessionId={}", sessionId);
            return;
        }

        try {
            Map<String, Object> message = Map.of(
                "type", "team_status",
                "statusType", statusType,
                "data", data != null ? data : Map.of(),
                "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(message);
            session.getBasicRemote().sendText(json);
            log.info("✅ [pushTeamStatus] 推送成功: sessionId={}, statusType={}, json={}",
                sessionId, statusType, json);
        } catch (IOException e) {
            log.error("❌ [pushTeamStatus] 推送失败: sessionId={}, statusType={}", sessionId, statusType, e);
        }
    }

    // ★ 增强调试日志的推送提示方法
    public static void pushHint(String sessionId, String hint) {
        log.info("📤 [pushHint] 尝试推送: sessionId={}, hint={}", sessionId, hint);

        Session session = SESSIONS.get(sessionId);
        if (session == null) {
            log.warn("⚠️ [pushHint] 会话不存在: sessionId={}", sessionId);
            return;
        }

        if (!session.isOpen()) {
            log.warn("⚠️ [pushHint] 会话已关闭: sessionId={}", sessionId);
            return;
        }

        try {
            Map<String, Object> message = Map.of(
                "type", "teamMsg",
                "text", hint,
                "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(message);
            session.getBasicRemote().sendText(json);
            log.info("✅ [pushHint] 推送成功: sessionId={}, json={}", sessionId, json);
        } catch (IOException e) {
            log.error("❌ [pushHint] 推送失败: sessionId={}", sessionId, e);
        }
    }

    public static void pushStateChange(String sessionId, String from, String to, String event) {
        Session session = SESSIONS.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> message = Map.of(
                    "type", "state_change",
                    "from", from,
                    "to", to,
                    "event", event,
                    "timestamp", System.currentTimeMillis()
                );
                session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
            } catch (IOException e) {
                log.error("推送状态变更失败", e);
            }
        }
    }

    public static boolean isSessionOpen(String sessionId) {
        Session session = SESSIONS.get(sessionId);
        boolean open = session != null && session.isOpen();
        log.debug("🔍 [isSessionOpen] sessionId={}, open={}", sessionId, open);
        return open;
    }

    // ========== 其他原有方法（保持不变） ==========

    @OnMessage
    public void onAudioData(byte[] audioData, Session session) {
        String taskId = (String) session.getUserProperties().get("taskId");
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
        String sessionId = (String) session.getUserProperties().get("sessionId");
        log.debug("收到文本消息: sessionId={}, message={}", sessionId, message);

        try {
            Map<String, Object> msg = objectMapper.readValue(message, Map.class);
            String type = (String) msg.get("type");

            switch (type) {
                case "ping" -> sendMessage(session, "pong", null);
                case "control" -> {
                    String action = (String) msg.get("action");
                    handleControl(session, action);
                }
                case "hint" -> {
                    String state = (String) msg.get("state");
                    sendHint(session, state);
                }
                case "reloadAll" -> {
                    sessionService.RunTest();
                }
                case "manual_transcript" -> {
                    String text = (String) msg.get("text");
                    if (text == null || text.trim().isEmpty()) {
                        log.warn("manual_transcript 消息缺少文本内容");
                        sendError(session, "手动转写文本为空");
                        break;
                    }
                    String result = sessionService.processTranscript(session, sessionId, text);
                }
                default -> log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理文本消息失败", e);
            sendError(session, "消息解析失败");
        }
    }

    @OnClose
    public void onClose(Session session) {
        String sessionId = (String) session.getUserProperties().get("sessionId");
        String taskId = (String) session.getUserProperties().get("taskId");

        log.info("WebSocket连接关闭: sessionId={}", sessionId);

        if (taskId != null) {
            asrClient.stopRealtimeRecognition(taskId);
        }
        SESSIONS.remove(sessionId);
        asrClient.unregisterCallback(sessionId);

        try {
            String status = sessionService.getSessionStatus(sessionId);
            if ("RUNNING".equals(status)) {
                sessionService.endSession(sessionId);
                evaluationService.triggerFinalEvaluation(sessionId);
            }
        } catch (Exception e) {
            log.error("会话结束处理失败", e);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String sessionId = (String) session.getUserProperties().get("sessionId");
        log.error("WebSocket错误: sessionId={}, error={}", sessionId, error.getMessage(), error);
        sendError(session, "连接异常: " + error.getMessage());
    }

    private void sendTranscript(Session session, String text, boolean isFinal) throws IOException {
        Map<String, Object> message = Map.of(
            "type", "transcript",
            "text", text,
            "isFinal", isFinal,
            "timestamp", System.currentTimeMillis()
        );
        session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
    }

    private void sendMessage(Session session, String type, Object data) {
        try {
            Map<String, Object> message = Map.of(
                "type", type,
                "data", data != null ? data : "",
                "timestamp", System.currentTimeMillis()
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }

    private void sendError(Session session, String error) {
        sendMessage(session, "error", Map.of("message", error));
    }

    private void sendHint(Session session, String state) {
        String hint = switch (state) {
            case "INITIAL_ASSESSMENT" -> "请评估患者心率和脉搏状态";
            case "STABILITY_CHECK" -> "请评估患者血流动力学是否稳定";
            case "QRS_EVALUATION" -> "请判断QRS宽度是否小于0.12秒";
            case "VAGAL_MANEUVER" -> "建议尝试迷走神经刺激";
            case "ADENOSINE_6MG" -> "准备腺苷6mg快速IV推注";
            case "ADENOSINE_12MG" -> "准备腺苷12mg快速IV推注";
            case "SYNCHRONIZED_CARDIOVERSION" -> "患者不稳定，准备同步电复律";
            default -> "请继续执行当前步骤";
        };
        sendMessage(session, "hint", Map.of("text", hint, "state", state));
    }

    private void handleControl(Session session, String action) {
        String sessionId = (String) session.getUserProperties().get("sessionId");
        log.info("收到控制指令: sessionId={}, action={}", sessionId, action);
        switch (action) {
            case "pause" -> asrClient.pause(sessionId);
            case "resume" -> asrClient.resume(sessionId);
            case "reset" -> asrClient.reset(sessionId);
            default -> log.warn("未知控制指令: {}", action);
        }
    }
}

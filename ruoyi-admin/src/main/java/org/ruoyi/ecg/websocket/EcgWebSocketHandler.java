package org.ruoyi.ecg.websocket;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.Timer;
import java.util.TimerTask;
/**
 * ECG WebSocket 处理器 - 生产级增强版
 * 支持：房间管理、消息确认、心跳检测、限流、会话管理、监控
 *
 * @author ruoyi
 */
@Component
public class EcgWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EcgWebSocketHandler.class);

    // =============================================
    // 配置常量
    // =============================================

    /** 心跳超时时间（秒） */
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 90;

    /** 心跳检测间隔（秒） */
    private static final long HEARTBEAT_CHECK_INTERVAL_SECONDS = 30;

    /** 最大消息大小（字节） */
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB

    /** 每秒最大消息数 */
    private static final int MAX_MESSAGES_PER_SECOND = 20;

    /** 每分钟最大消息数 */
    private static final int MAX_MESSAGES_PER_MINUTE = 300;

    /** 消息确认超时（毫秒） */
    private static final long ACK_TIMEOUT_MS = 30000;

    /** 消息最大重试次数 */
    private static final int MAX_RETRY_COUNT = 3;

    /** 会话超时（分钟） */
    private static final long SESSION_TIMEOUT_MINUTES = 30;


    private static class RoomInfo {
        String roomId;
        String roomName;
        Set<String> userIds = ConcurrentHashMap.newKeySet();

        RoomInfo(String roomId, String roomName) {
            this.roomId = roomId;
            this.roomName = roomName != null ? roomName : roomId;
        }
    }




    // =============================================
    // 数据存储
    // =============================================

    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, UserInfo> USER_INFO_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, RoomInfo> THEME_ROOMS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> USER_SESSION_MAP = new ConcurrentHashMap<>();

    // =============================================
    // 新增：会话管理
    // =============================================

    /** 会话信息（包含心跳时间） */
    private static final ConcurrentHashMap<String, SessionContext> SESSION_CONTEXT = new ConcurrentHashMap<>();

    private static class SessionContext {
        String sessionId;
        String userId;
        Instant connectedTime;
        Instant lastHeartbeatTime;
        Instant lastActivityTime;
        int messageCount;
        String clientAddress;
        String clientInfo;

        SessionContext(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.connectedTime = Instant.now();
            this.lastHeartbeatTime = Instant.now();
            this.lastActivityTime = Instant.now();
            this.messageCount = 0;
        }

        void updateHeartbeat() {
            this.lastHeartbeatTime = Instant.now();
            this.lastActivityTime = Instant.now();
        }

        void updateActivity() {
            this.lastActivityTime = Instant.now();
            this.messageCount++;
        }

        boolean isHeartbeatTimeout() {
            return Duration.between(lastHeartbeatTime, Instant.now()).getSeconds() > HEARTBEAT_TIMEOUT_SECONDS;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("sessionId", sessionId);
            json.put("userId", userId);
            json.put("connectedTime", connectedTime.toString());
            json.put("lastHeartbeatTime", lastHeartbeatTime.toString());
            json.put("lastActivityTime", lastActivityTime.toString());
            json.put("messageCount", messageCount);
            json.put("isOnline", true);
            return json;
        }
    }

    // =============================================
    // 新增：消息确认管理
    // =============================================

    private static final ConcurrentHashMap<String, PendingMessage> PENDING_MESSAGES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService ACK_SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private static class PendingMessage {
        String messageId;
        String sessionId;
        String type;
        JSONObject data;
        JSONObject originalMessage;
        Instant sendTime;
        int retryCount;
        ScheduledFuture<?> timeoutTask;

        PendingMessage(String messageId, String sessionId, String type, JSONObject data, JSONObject originalMessage) {
            this.messageId = messageId;
            this.sessionId = sessionId;
            this.type = type;
            this.data = data;
            this.originalMessage = originalMessage;
            this.sendTime = Instant.now();
            this.retryCount = 0;
        }
    }

    // =============================================
    // 新增：限流管理
    // =============================================

    /** 会话消息时间戳队列（滑动窗口） */
    private static final ConcurrentHashMap<String, List<Long>> MESSAGE_TIMESTAMPS = new ConcurrentHashMap<>();

    /** 全局统计 */
    private static final AtomicLong TOTAL_MESSAGES_RECEIVED = new AtomicLong(0);
    private static final AtomicLong TOTAL_MESSAGES_REJECTED = new AtomicLong(0);
    private static final AtomicLong TOTAL_MESSAGES_SENT = new AtomicLong(0);

    // =============================================
    // 新增：定时任务
    // =============================================

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // 启动心跳检测定时任务
        SCHEDULER.scheduleAtFixedRate(this::checkHeartbeats,
            HEARTBEAT_CHECK_INTERVAL_SECONDS,
            HEARTBEAT_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS);

        log.info("✅ WebSocket 心跳检测已启动，间隔 {} 秒", HEARTBEAT_CHECK_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void destroy() {
        log.info("🛑 WebSocket 处理器正在关闭...");
        SCHEDULER.shutdown();
        ACK_SCHEDULER.shutdown();
        try {
            if (!SCHEDULER.awaitTermination(10, TimeUnit.SECONDS)) {
                SCHEDULER.shutdownNow();
            }
            if (!ACK_SCHEDULER.awaitTermination(10, TimeUnit.SECONDS)) {
                ACK_SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("✅ WebSocket 处理器已关闭");
    }

    // =============================================
    // 用户信息类（完整修复版）
    // =============================================

    private static class UserInfo {
        String userId;
        String themeId;
        String nickname;
        String role;
        String username;
        String token;
        long loginTime;

        // ✅ 构造函数1：4参数（兼容原有调用）
        UserInfo(String userId, String themeId, String nickname, String role) {
            this(userId, themeId, nickname, role, null, null);
        }

        // ✅ 构造函数2：5参数（兼容部分调用）
        UserInfo(String userId, String themeId, String nickname, String role, String username) {
            this(userId, themeId, nickname, role, username, null);
        }

        // ✅ 构造函数3：6参数（完整版）
        UserInfo(String userId, String themeId, String nickname, String role,
                 String username, String token) {
            this.userId = userId != null ? userId : "unknown";
            this.themeId = themeId != null ? themeId : null; // ✅ 修复：不再默认使用 "default"
            this.nickname = nickname != null ? nickname : this.userId;
            this.role = role != null ? role : "viewer";
            this.username = username;
            this.token = token;
            this.loginTime = System.currentTimeMillis();
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("userId", userId);
            json.put("themeId", themeId != null ? themeId : "");
            json.put("nickname", nickname);
            json.put("role", role);
            json.put("username", username != null ? username : userId);
            json.put("loginTime", loginTime);
            return json;
        }
    }

    // =============================================
    // 辅助方法
    // =============================================

    private JSONObject createSafeJson() {
        return new JSONObject();
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void safeSend(WebSocketSession session, JSONObject message) throws IOException {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String jsonString = JSON.toJSONString(message);
            session.sendMessage(new TextMessage(jsonString));
            TOTAL_MESSAGES_SENT.incrementAndGet();
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage());
        }
    }

    private void safeSendWithAck(WebSocketSession session, String type, JSONObject data) throws IOException {
        String messageId = generateMessageId();

        JSONObject message = createSafeJson();
        message.put("type", type);
        message.put("messageId", messageId);
        message.put("needAck", true);
        if (data != null) {
            message.put("data", data);
        } else {
            message.put("data", createSafeJson());
        }
        message.put("timestamp", System.currentTimeMillis());

        PendingMessage pending = new PendingMessage(messageId, session.getId(), type, data, message);
        pending.timeoutTask = ACK_SCHEDULER.schedule(() -> {
            retryPendingMessage(messageId);
        }, ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        PENDING_MESSAGES.put(messageId, pending);
        safeSend(session, message);
        log.debug("📤 发送需确认消息: {} ({})", messageId, type);
    }

    private void retryPendingMessage(String messageId) {
        PendingMessage pending = PENDING_MESSAGES.get(messageId);
        if (pending == null) {
            return;
        }

        if (pending.retryCount >= MAX_RETRY_COUNT) {
            PENDING_MESSAGES.remove(messageId);
            log.warn("❌ 消息确认超时，已达最大重试次数: {}", messageId);
            return;
        }

        pending.retryCount++;
        WebSocketSession session = SESSIONS.get(pending.sessionId);
        if (session != null && session.isOpen()) {
            try {
                safeSend(session, pending.originalMessage);
                log.info("🔄 重发消息: {} (第{}次)", messageId, pending.retryCount);

                pending.timeoutTask = ACK_SCHEDULER.schedule(() -> {
                    retryPendingMessage(messageId);
                }, ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (IOException e) {
                log.error("重发消息失败: {}", messageId, e);
            }
        } else {
            PENDING_MESSAGES.remove(messageId);
        }
    }

    public void handleAck(String messageId, String sessionId) {
        PendingMessage pending = PENDING_MESSAGES.remove(messageId);
        if (pending != null) {
            if (pending.timeoutTask != null) {
                pending.timeoutTask.cancel(false);
            }
            log.debug("✅ 消息确认: {}", messageId);
        }
    }

    // =============================================
    // 限流检查
    // =============================================

    private boolean checkRateLimit(String sessionId) {
        List<Long> timestamps = MESSAGE_TIMESTAMPS.computeIfAbsent(sessionId,
            k -> new ArrayList<>());

        long now = System.currentTimeMillis();
        long oneSecondAgo = now - 1000;
        long oneMinuteAgo = now - 60000;

        timestamps.removeIf(t -> t < oneMinuteAgo);

        if (timestamps.size() >= MAX_MESSAGES_PER_MINUTE) {
            log.warn("⚠️ 消息频率超限(分钟): session={}, count={}", sessionId, timestamps.size());
            TOTAL_MESSAGES_REJECTED.incrementAndGet();
            return false;
        }

        long countInSecond = timestamps.stream().filter(t -> t >= oneSecondAgo).count();
        if (countInSecond >= MAX_MESSAGES_PER_SECOND) {
            log.warn("⚠️ 消息频率超限(秒): session={}, count={}", sessionId, countInSecond);
            TOTAL_MESSAGES_REJECTED.incrementAndGet();
            return false;
        }

        timestamps.add(now);
        TOTAL_MESSAGES_RECEIVED.incrementAndGet();
        return true;
    }

    // =============================================
    // 心跳检测
    // =============================================

    private void checkHeartbeats() {
        List<String> timeoutSessions = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, SessionContext> entry : SESSION_CONTEXT.entrySet()) {
            SessionContext ctx = entry.getValue();
            if (ctx.isHeartbeatTimeout()) {
                timeoutSessions.add(entry.getKey());
            }
        }

        for (String sessionId : timeoutSessions) {
            SessionContext ctx = SESSION_CONTEXT.get(sessionId);
            if (ctx != null) {
                log.warn("⏰ 会话心跳超时: {} ({}秒无心跳)", ctx.userId, HEARTBEAT_TIMEOUT_SECONDS);
                WebSocketSession session = SESSIONS.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        JSONObject timeoutMsg = createSafeJson();
                        timeoutMsg.put("type", "system");
                        JSONObject data = createSafeJson();
                        data.put("message", "心跳超时，连接已断开");
                        timeoutMsg.put("data", data);
                        safeSend(session, timeoutMsg);
                        session.close(CloseStatus.SESSION_NOT_RELIABLE);
                    } catch (IOException e) {
                        log.error("关闭超时会话失败", e);
                    }
                }
                cleanupSession(sessionId);
            }
        }
    }

    private void cleanupSession(String sessionId) {
        SESSION_CONTEXT.remove(sessionId);
        SESSIONS.remove(sessionId);
        MESSAGE_TIMESTAMPS.remove(sessionId);
        UserInfo userInfo = USER_INFO_MAP.remove(sessionId);
        if (userInfo != null) {
            USER_SESSION_MAP.remove(userInfo.userId);
        }
        removeFromTheme(sessionId);
        log.info("🧹 清理会话: {}", sessionId);
    }

    // =============================================
    // 连接建立（增强）
    // =============================================

@Override
public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String sessionId = session.getId();

    Map<String, Object> attributes = session.getAttributes();
    Boolean authenticated = (Boolean) attributes.getOrDefault("authenticated", false);

    Object userIdObj = attributes.get("userId");
    String userId = userIdObj != null ? userIdObj.toString() : null;

    String username = (String) attributes.get("username");
    String nickname = (String) attributes.get("nickname");
    String token = (String) attributes.get("token");

    log.info("🔍 认证信息: authenticated={}, userId={}, username={}, nickname={}",
            authenticated, userId, username, nickname);

    // =============================================
    // ✅ 修复：放行 system-server 用户
    // =============================================
    if (userId != null && userId.startsWith("system-server")) {
        log.info("✅ system-server 用户放行: {}", userId);
        authenticated = true;
        if (nickname == null || nickname.isEmpty()) {
            nickname = "系统服务";
        }
        if (username == null || username.isEmpty()) {
            username = "system";
        }
        // 更新 attributes
        attributes.put("authenticated", true);
        attributes.put("nickname", nickname);
        attributes.put("username", username);
    }

    // =============================================
    // 认证检查
    // =============================================
    if (!authenticated || userId == null) {
        log.warn("❌ 未认证的 WebSocket 连接，拒绝: {}", sessionId);
        session.close(CloseStatus.POLICY_VIOLATION);
        return;
    }

    // =============================================
    // 从查询参数获取 themeId
    // =============================================
    String query = session.getUri().getQuery();
    String requestedThemeId = null;
    if (query != null) {
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && "themeId".equals(kv[0])) {
                requestedThemeId = java.net.URLDecoder.decode(kv[1], "UTF-8");
                break;
            }
        }
    }

    if (requestedThemeId == null || requestedThemeId.isEmpty()) {
        log.info("📌 用户 {} 未指定房间，连接后处于无房间状态", userId);
    } else {
        log.info("📌 用户 {} 请求的房间: {}", userId, requestedThemeId);
    }

    // =============================================
    // 踢出旧连接
    // =============================================
    if (USER_SESSION_MAP.containsKey(userId)) {
        String oldSessionId = USER_SESSION_MAP.get(userId);
        WebSocketSession oldSession = SESSIONS.get(oldSessionId);
        if (oldSession != null && oldSession.isOpen()) {
            JSONObject kickMsg = createSafeJson();
            kickMsg.put("type", "system");
            JSONObject kickData = createSafeJson();
            kickData.put("message", "您在其他设备登录，当前连接已断开");
            kickData.put("code", "DUPLICATE_LOGIN");
            kickMsg.put("data", kickData);
            try {
                safeSend(oldSession, kickMsg);
                oldSession.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                log.warn("踢出旧连接失败: {}", e.getMessage());
            }
        }
        SESSIONS.remove(oldSessionId);
        USER_INFO_MAP.remove(oldSessionId);
        USER_SESSION_MAP.remove(userId);
        removeFromTheme(oldSessionId);
    }

    // =============================================
    // 创建用户信息
    // =============================================
    UserInfo userInfo = new UserInfo(
        userId,
        requestedThemeId,
        nickname != null ? nickname : userId,
        "viewer",
        username
    );

    SESSIONS.put(sessionId, session);
    USER_INFO_MAP.put(sessionId, userInfo);
    USER_SESSION_MAP.put(userId, sessionId);

    // 如果有 themeId，自动加入房间
    if (requestedThemeId != null && !requestedThemeId.isEmpty()) {
        RoomInfo roomInfo = THEME_ROOMS.get(requestedThemeId);
        if (roomInfo == null) {
            roomInfo = new RoomInfo(requestedThemeId, requestedThemeId);
            THEME_ROOMS.put(requestedThemeId, roomInfo);
        }
        roomInfo.userIds.add(sessionId);
        log.info("✅ 用户 {} 自动加入房间: {}", userId, requestedThemeId);
    }

    // =============================================
    // 创建会话上下文
    // =============================================
    SessionContext ctx = new SessionContext(sessionId, userId);
    SESSION_CONTEXT.put(sessionId, ctx);

    log.info("✅ WebSocket 连接成功！用户：{} ({}), 当前在线：{}",
            userId, username != null ? username : "未知", SESSIONS.size());

    // =============================================
    // 发送连接成功消息
    // =============================================
    JSONObject connectMsg = createSafeJson();
    connectMsg.put("type", "connect");
    connectMsg.put("data", userInfo.toJson());
    connectMsg.put("onlineCount", SESSIONS.size());
    connectMsg.put("authenticated", true);
    connectMsg.put("timestamp", System.currentTimeMillis());
    safeSend(session, connectMsg);

    // =============================================
    // 广播更新
    // =============================================
    broadcastUserList();
    broadcastRoomList();

    if (requestedThemeId != null && !requestedThemeId.isEmpty()) {
        broadcastThemeUserList(requestedThemeId);
    }
}

//添加消息缓存（在类中定义）
private static final Set<String> PROCESSED_MESSAGES = ConcurrentHashMap.newKeySet();
private static final long MESSAGE_CACHE_DURATION = 3000; // 3秒内去重

    // =============================================
    // 消息处理（增强）
    // =============================================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();
        UserInfo userInfo = USER_INFO_MAP.get(sessionId);

        if (userInfo == null) {
            log.warn("未知会话: {}", sessionId);
            return;
        }



        String messageHash = sessionId + "_" + payload.hashCode();
        if (PROCESSED_MESSAGES.contains(messageHash)) {
            log.debug("🔄 重复消息已忽略: {}", payload.substring(0, Math.min(50, payload.length())));
            return;
        }
        PROCESSED_MESSAGES.add(messageHash);

        // 定时清理缓存
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                PROCESSED_MESSAGES.remove(messageHash);
            }
        }, MESSAGE_CACHE_DURATION);


        SessionContext ctx = SESSION_CONTEXT.get(sessionId);
        if (ctx != null) {
            ctx.updateActivity();
        }

        if (!checkRateLimit(sessionId)) {
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", "消息频率超限，请稍后再试");
            errorData.put("code", "RATE_LIMIT");
            error.put("data", errorData);
            safeSend(session, error);
            return;
        }



        try {
            JSONObject json = JSONObject.parseObject(payload);
            String type = json.getString("type");
            JSONObject data = json.getJSONObject("data");

            if (data == null) {
                data = new JSONObject();
            }

            String ackMessageId = json.getString("ackMessageId");
            if (ackMessageId != null) {
                handleAck(ackMessageId, sessionId);
                return;
            }

            if ("ping".equals(type)) {
                if (ctx != null) {
                    ctx.updateHeartbeat();
                }
                handlePing(session);
                return;
            }
            log.debug("📨 收到消息：{}，来自：{}", payload, userInfo.userId);
            switch (type) {
                case "auth":
                    handleAuth(session, userInfo, data);
                    break;

                case "getUsers":
                    sendUserList(session);
                    break;

                case "getThemeUsers":
                    String themeId = data.getString("themeId");
                    if (themeId == null) themeId = userInfo.themeId;
                    sendThemeUserList(session, themeId);
                    break;

                case "joinTheme":
                    handleJoinTheme(session, userInfo, data);
                    break;

                case "leaveTheme":
                    handleLeaveTheme(session, userInfo, data); // ✅ 修复：传递 data
                    break;

                case "createTheme":
                    handleCreateTheme(session, userInfo, data);
                    break;

                case "broadcast":
                    handleBroadcast(userInfo, data);
                    break;

                case "private":
                    handlePrivate(userInfo, data);
                    break;

                case "themeBroadcast":
                    handleThemeBroadcast(userInfo, data);
                    break;

                case "stateChange":
                    handleStateChange(userInfo, data);
                    break;

                case "eventTrigger":
                    handleEventTrigger(userInfo, data);
                    break;

                case "alarm":
                    handleAlarm(userInfo, data);
                    break;

                case "heartRateUpdate":
                    handleHeartRateUpdate(userInfo, data);
                    break;

                case "getRoomList":
                    sendRoomList(session);
                    break;

                case "kick":
                    handleKick(userInfo, data);
                    break;

                case "getStatistics":
                    sendStatistics(session);
                    break;

                default:
                    JSONObject ack = createSafeJson();
                    ack.put("type", "ack");
                    JSONObject ackData = createSafeJson();
                    ackData.put("message", "收到消息: " + safeString(type));
                    ackData.put("receivedType", type);
                    ack.put("data", ackData);
                    ack.put("timestamp", System.currentTimeMillis());
                    safeSend(session, ack);
                    break;
            }
        } catch (Exception e) {
            log.error("处理消息异常", e);
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", safeString(e.getMessage()));
            errorData.put("code", "PROCESS_ERROR");
            error.put("data", errorData);
            safeSend(session, error);
        }
    }

    // =============================================
    // 新增：获取统计信息
    // =============================================

    private void sendStatistics(WebSocketSession session) throws IOException {
        JSONObject stats = createSafeJson();
        stats.put("onlineCount", SESSIONS.size());
        stats.put("roomCount", THEME_ROOMS.size());
        stats.put("totalMessagesReceived", TOTAL_MESSAGES_RECEIVED.get());
        stats.put("totalMessagesRejected", TOTAL_MESSAGES_REJECTED.get());
        stats.put("totalMessagesSent", TOTAL_MESSAGES_SENT.get());
        stats.put("pendingAckCount", PENDING_MESSAGES.size());
        stats.put("timestamp", System.currentTimeMillis());

        JSONObject response = createSafeJson();
        response.put("type", "statistics");
        response.put("data", stats);
        response.put("timestamp", System.currentTimeMillis());
        safeSend(session, response);
    }

    // =============================================
    // 新增：获取所有会话（管理端）
    // =============================================

    public JSONObject getAllSessions() {
        JSONObject result = createSafeJson();
        List<JSONObject> sessionList = new ArrayList<>();
        for (Map.Entry<String, SessionContext> entry : SESSION_CONTEXT.entrySet()) {
            sessionList.add(entry.getValue().toJson());
        }
        result.put("sessions", sessionList);
        result.put("count", sessionList.size());
        return result;
    }

    public JSONObject getAllRooms() {
        JSONObject result = createSafeJson();
        List<JSONObject> roomList = new ArrayList<>();
        for (Map.Entry<String, RoomInfo> entry : THEME_ROOMS.entrySet()) {
            RoomInfo roomInfo = entry.getValue();
            JSONObject room = new JSONObject();
            room.put("roomId", entry.getKey());
            // ✅ 添加房间名称
            room.put("roomName", roomInfo.roomName != null ? roomInfo.roomName : entry.getKey());
            room.put("userCount", roomInfo.userIds.size());
            List<String> users = new ArrayList<>();
            for (String sid : roomInfo.userIds) {
                UserInfo info = USER_INFO_MAP.get(sid);
                if (info != null) {
                    users.add(info.userId);
                }
            }
            room.put("users", users);
            roomList.add(room);
        }
        result.put("rooms", roomList);
        return result;
    }

    public boolean kickUser(String userId, String reason) {
        String sessionId = USER_SESSION_MAP.get(userId);
        if (sessionId == null) {
            return false;
        }

        WebSocketSession session = SESSIONS.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                JSONObject kickMsg = createSafeJson();
                kickMsg.put("type", "system");
                JSONObject data = createSafeJson();
                data.put("message", "您已被管理员踢出" + (reason != null ? "：" + reason : ""));
                kickMsg.put("data", data);
                safeSend(session, kickMsg);
                session.close(CloseStatus.POLICY_VIOLATION);
                cleanupSession(sessionId);
                log.info("👢 管理员踢出用户: {}，原因: {}", userId, reason);
                return true;
            } catch (IOException e) {
                log.error("踢出用户失败: {}", userId, e);
            }
        }
        return false;
    }

    // =============================================
    // 消息处理器
    // =============================================

    private void handlePing(WebSocketSession session) throws IOException {
        JSONObject pong = createSafeJson();
        pong.put("type", "pong");
        pong.put("timestamp", System.currentTimeMillis());
        safeSend(session, pong);
    }

 private void handleAuth(WebSocketSession session, UserInfo userInfo, JSONObject data) throws IOException {
    try {
        log.info("🔐 开始处理认证请求，用户: {}", userInfo.userId);

        String userId = data.getString("userId");
        String nickname = data.getString("nickname");
        String role = data.getString("role");

        log.info("📝 认证数据: userId={}, nickname={}, role={}", userId, nickname, role);

        // ✅ 更新用户信息
        if (userId != null && !userId.isEmpty()) {
            // 更新 USER_SESSION_MAP
            String oldSessionId = USER_SESSION_MAP.get(userInfo.userId);
            if (oldSessionId != null) {
                USER_SESSION_MAP.remove(userInfo.userId);
            }
            userInfo.userId = userId;
            USER_SESSION_MAP.put(userId, session.getId());
        }

        if (nickname != null && !nickname.isEmpty()) {
            userInfo.nickname = nickname;
        }
        if (role != null && !role.isEmpty()) {
            userInfo.role = role;
        }

        // ✅ 发送认证成功响应
        JSONObject response = createSafeJson();
        response.put("type", "auth");
        response.put("success", true);
        response.put("data", userInfo.toJson());
        response.put("timestamp", System.currentTimeMillis());

        log.info("📤 发送认证响应: {}", response.toJSONString());
        safeSend(session, response);

        broadcastUserList();
        if (userInfo.themeId != null && !userInfo.themeId.isEmpty()) {
            broadcastThemeUserList(userInfo.themeId);
        }

        log.info("✅ 用户 {} 认证成功", userId);

    } catch (Exception e) {
        log.error("❌ handleAuth 异常: {}", e.getMessage(), e);
        // ✅ 发送认证失败响应
        JSONObject error = createSafeJson();
        error.put("type", "auth");
        error.put("success", false);
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        safeSend(session, error);
    }
}

    private void sendRoomList(WebSocketSession session) throws IOException {
        List<JSONObject> roomList = new ArrayList<>();
        for (Map.Entry<String, RoomInfo> entry: THEME_ROOMS.entrySet()) {
        	RoomInfo roomInfo = entry.getValue();
            JSONObject room = new JSONObject();
            room.put("roomId", entry.getKey());
            room.put("roomName", roomInfo.roomName != null ? roomInfo.roomName : entry.getKey());
            room.put("userCount", roomInfo.userIds.size());
            roomList.add(room);
        }

        JSONObject response = new JSONObject();
        response.put("type", "roomList");
        response.put("data", roomList);
        response.put("timestamp", System.currentTimeMillis());
        safeSend(session, response);

        log.debug("📋 发送房间列表：{} 个房间", roomList.size());
    }

    /**
     * ✅ 修复：处理加入房间
     */
    private void handleJoinTheme(WebSocketSession session, UserInfo userInfo, JSONObject data) throws IOException {
        String newThemeId = data.getString("themeId");
        if (newThemeId == null || newThemeId.isEmpty()) {
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", "房间ID不能为空");
            errorData.put("code", "INVALID_ROOM_ID");
            error.put("data", errorData);
            safeSend(session, error);
            return;
        }

        // 如果已经在同一个房间，直接返回成功
        if (newThemeId.equals(userInfo.themeId)) {
            JSONObject response = createSafeJson();
            response.put("type", "joinTheme");
            response.put("success", true);
            response.put("data", newThemeId);
            response.put("message", "已在房间中");
            response.put("timestamp", System.currentTimeMillis());
            safeSend(session, response);
            return;
        }

        // 离开旧房间
        String oldThemeId = userInfo.themeId;
        if (oldThemeId != null && !oldThemeId.isEmpty()) {
            removeFromTheme(session.getId(), oldThemeId);
        }

        // ✅ 修复：先检查房间是否存在，不存在才创建
        RoomInfo roomInfo = THEME_ROOMS.get(newThemeId);
        if (roomInfo == null) {
            roomInfo = new RoomInfo(newThemeId, newThemeId);
            THEME_ROOMS.put(newThemeId, roomInfo);
            log.info("房间不存在，自动创建: {}", newThemeId);
        }

        // 加入新房间
        roomInfo.userIds.add(session.getId());
        userInfo.themeId = newThemeId;

        log.info("✅ 用户 {} 加入房间: {} (从 {} 切换)",
            userInfo.userId, newThemeId, oldThemeId != null ? oldThemeId : "无");

        JSONObject response = createSafeJson();
        response.put("type", "joinTheme");
        response.put("success", true);
        response.put("data", newThemeId);
        response.put("themeId", newThemeId);
        response.put("timestamp", System.currentTimeMillis());
        safeSend(session, response);

        // 广播更新
        broadcastUserList();
        broadcastRoomList();

        // 广播用户加入房间
        JSONObject joinMsg = createSafeJson();
        joinMsg.put("type", "userJoined");
        joinMsg.put("data", userInfo.toJson());
        joinMsg.put("timestamp", System.currentTimeMillis());
        broadcastToTheme(newThemeId, joinMsg);

        // 更新旧房间的用户列表
        if (oldThemeId != null && !oldThemeId.isEmpty()) {
            broadcastThemeUserList(oldThemeId);
        }
        broadcastThemeUserList(newThemeId);
    }

    /**
     * ✅ 修复：处理离开房间（带 roomId 参数）
     */
private void handleLeaveTheme(WebSocketSession session, UserInfo userInfo, JSONObject data) throws IOException {
    String themeId = data.getString("roomId");

    if (themeId == null || themeId.isEmpty()) {
        themeId = userInfo.themeId;
    }

    if (themeId == null || themeId.isEmpty()) {
        JSONObject error = createSafeJson();
        error.put("type", "error");
        JSONObject errorData = createSafeJson();
        errorData.put("message", "您不在任何房间中");
        errorData.put("code", "NOT_IN_ROOM");
        error.put("data", errorData);
        safeSend(session, error);
        return;
    }

    RoomInfo roomInfo = THEME_ROOMS.get(themeId);
    if (roomInfo == null || !roomInfo.userIds.contains(session.getId())) {
        JSONObject error = createSafeJson();
        error.put("type", "error");
        JSONObject errorData = createSafeJson();
        errorData.put("message", "您不在房间 " + themeId + " 中");
        errorData.put("code", "NOT_IN_ROOM");
        error.put("data", errorData);
        safeSend(session, error);
        return;
    }

    // 从房间移除当前用户
    roomInfo.userIds.remove(session.getId());

    // ✅ 检查房间中是否还有其他活跃用户
    int activeUserCount = 0;
    for (String sid : roomInfo.userIds) {
        WebSocketSession wsSession = SESSIONS.get(sid);
        if (wsSession != null && wsSession.isOpen()) {
            activeUserCount++;
        }
    }

    if (activeUserCount == 0) {
        THEME_ROOMS.remove(themeId);
        log.info("房间 {} 已无活跃用户，自动删除", themeId);
    } else {
        // ✅ 打印剩余用户信息，方便调试
        log.info("用户 {} 离开房间: {}，剩余 {} 个活跃用户: {}",
            userInfo.userId, themeId, activeUserCount, roomInfo.userIds);
    }

    userInfo.themeId = null;

    JSONObject response = createSafeJson();
    response.put("type", "leaveTheme");
    response.put("success", true);
    response.put("data", themeId);
    response.put("timestamp", System.currentTimeMillis());
    safeSend(session, response);

    // 广播更新
    broadcastUserList();
    broadcastRoomList();
    broadcastThemeUserList(themeId);

    JSONObject leaveMsg = createSafeJson();
    leaveMsg.put("type", "userLeft");
    leaveMsg.put("data", userInfo.toJson());
    leaveMsg.put("timestamp", System.currentTimeMillis());
    broadcastToTheme(themeId, leaveMsg);
}
    // =============================================
    // 广播方法
    // =============================================

    private void broadcastRoomList() {
        List<JSONObject> roomList = new ArrayList<>();
        for (Map.Entry<String, RoomInfo> entry : THEME_ROOMS.entrySet()) {
            RoomInfo roomInfo = entry.getValue();
            JSONObject room = new JSONObject();
            room.put("roomId", entry.getKey());
            // ✅ 使用存储的房间名称
            room.put("roomName", roomInfo.roomName != null ? roomInfo.roomName : entry.getKey());
            room.put("userCount", roomInfo.userIds.size());
            roomList.add(room);
        }

        JSONObject response = createSafeJson();
        response.put("type", "roomList");
        response.put("data", roomList);
        response.put("timestamp", System.currentTimeMillis());

        String jsonString = JSON.toJSONString(response);
        broadcast(jsonString);
        log.debug("📋 广播房间列表：{} 个房间", roomList.size());
    }







    private void handleCreateTheme(WebSocketSession session, UserInfo userInfo, JSONObject data) throws IOException {
        String newThemeId = data.getString("themeId");
        String themeName = data.getString("themeName");

        if (newThemeId == null || newThemeId.isEmpty()) {
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", "主题ID不能为空");
            errorData.put("code", "INVALID_THEME_ID");
            error.put("data", errorData);
            safeSend(session, error);
            return;
        }

        if (THEME_ROOMS.containsKey(newThemeId)) {
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", "主题已存在");
            errorData.put("code", "THEME_EXISTS");
            error.put("data", errorData);
            safeSend(session, error);
            return;
        }

        // ✅ 离开旧房间
        String oldThemeId = userInfo.themeId;
        if (oldThemeId != null && !oldThemeId.isEmpty()) {
            removeFromTheme(session.getId(), oldThemeId);
        }

        // ✅ 创建房间
        RoomInfo roomInfo = new RoomInfo(newThemeId, themeName);
        THEME_ROOMS.put(newThemeId, roomInfo);

        // ✅ 自动加入新创建的房间
        roomInfo.userIds.add(session.getId());
        userInfo.themeId = newThemeId;

        log.info("用户 {} 创建并加入主题: {} ({})", userInfo.userId, newThemeId, themeName != null ? themeName : newThemeId);

        JSONObject response = createSafeJson();
        response.put("type", "createTheme");
        response.put("success", true);
        response.put("data", newThemeId);
        if (themeName != null && !themeName.isEmpty()) {
            response.put("themeName", themeName);
        }
        response.put("timestamp", System.currentTimeMillis());
        safeSend(session, response);

        // 广播更新
        broadcastUserList();
        broadcastRoomList();

        // ✅ 广播用户加入房间事件
        JSONObject joinMsg = createSafeJson();
        joinMsg.put("type", "userJoined");
        joinMsg.put("data", userInfo.toJson());
        joinMsg.put("timestamp", System.currentTimeMillis());
        broadcastToTheme(newThemeId, joinMsg);

        // 更新旧房间的用户列表
        if (oldThemeId != null && !oldThemeId.isEmpty()) {
            broadcastThemeUserList(oldThemeId);
        }
        broadcastThemeUserList(newThemeId);
    }

    private void handleBroadcast(UserInfo userInfo, JSONObject data) {
        String message = data.getString("message");
        if (message == null || message.isEmpty()) {
            return;
        }

        JSONObject response = createSafeJson();
        response.put("type", "broadcast");
        JSONObject msgData = createSafeJson();
        msgData.put("from", safeString(userInfo.userId));
        msgData.put("nickname", safeString(userInfo.nickname));
        msgData.put("message", safeString(message));
        msgData.put("timestamp", System.currentTimeMillis());
        response.put("data", msgData);

        String jsonString = JSON.toJSONString(response);
        broadcast(jsonString);
        log.info("📢 广播消息：{} -> {}", userInfo.userId, message);
    }

    private void handlePrivate(UserInfo userInfo, JSONObject data) throws IOException {
        String targetUserId = data.getString("to");
        String message = data.getString("message");

        if (targetUserId == null || message == null) {
            return;
        }

        String targetSessionId = USER_SESSION_MAP.get(targetUserId);
        if (targetSessionId == null) {
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", "用户 " + targetUserId + " 不在线");
            errorData.put("code", "USER_OFFLINE");
            error.put("data", errorData);
            safeSend(SESSIONS.get(USER_SESSION_MAP.get(userInfo.userId)), error);
            return;
        }

        JSONObject response = createSafeJson();
        response.put("type", "private");
        JSONObject msgData = createSafeJson();
        msgData.put("from", safeString(userInfo.userId));
        msgData.put("nickname", safeString(userInfo.nickname));
        msgData.put("to", safeString(targetUserId));
        msgData.put("message", safeString(message));
        msgData.put("timestamp", System.currentTimeMillis());
        response.put("data", msgData);

        String jsonString = JSON.toJSONString(response);
        sendToUser(targetUserId, jsonString);
        log.info("🔒 私信：{} -> {}：{}", userInfo.userId, targetUserId, message);
    }

    private void handleThemeBroadcast(UserInfo userInfo, JSONObject data) {
        String message = data.getString("message");
        if (message == null || message.isEmpty()) {
            return;
        }

        // 如果用户不在任何房间
        if (userInfo.themeId == null || userInfo.themeId.isEmpty()) {
            log.warn("用户 {} 不在任何房间，无法发送房间广播", userInfo.userId);
            return;
        }

        JSONObject response = createSafeJson();
        response.put("type", "themeBroadcast");
        JSONObject msgData = createSafeJson();
        msgData.put("from", safeString(userInfo.userId));
        msgData.put("nickname", safeString(userInfo.nickname));
        msgData.put("themeId", safeString(userInfo.themeId));
        msgData.put("message", safeString(message));
        msgData.put("timestamp", System.currentTimeMillis());
        response.put("data", msgData);

        broadcastToTheme(userInfo.themeId, response);
        log.info("📢 主题广播：{} -> {}：{}", userInfo.userId, userInfo.themeId, message);
    }

    private void handleStateChange(UserInfo userInfo, JSONObject data) {
        String stateId = data.getString("stateId");
        String stateName = data.getString("stateName");

        JSONObject response = createSafeJson();
        response.put("type", "stateUpdate");
        JSONObject msgData = createSafeJson();
        msgData.put("from", safeString(userInfo.userId));
        msgData.put("nickname", safeString(userInfo.nickname));
        msgData.put("stateId", safeString(stateId));
        msgData.put("stateName", safeString(stateName));
        msgData.put("timestamp", System.currentTimeMillis());
        response.put("data", msgData);

        broadcastToTheme(userInfo.themeId, response);
        log.info("📌 状态更新：{} -> {}", userInfo.userId, stateName);
    }

    private void handleEventTrigger(UserInfo userInfo, JSONObject data) {
        String eventId = data.getString("eventId");
        String eventName = data.getString("eventName");

        JSONObject response = createSafeJson();
        response.put("type", "eventTrigger");
        JSONObject msgData = createSafeJson();
        msgData.put("from", safeString(userInfo.userId));
        msgData.put("nickname", safeString(userInfo.nickname));
        msgData.put("eventId", safeString(eventId));
        msgData.put("eventName", safeString(eventName));
        msgData.put("timestamp", System.currentTimeMillis());
        response.put("data", msgData);

        broadcastToTheme(userInfo.themeId, response);
        log.info("⚡ 事件触发：{} -> {}", userInfo.userId, eventName);
    }

    private void handleAlarm(UserInfo userInfo, JSONObject data) {
        String alarmType = data.getString("alarmType");
        String alarmMessage = data.getString("alarmMessage");

        JSONObject response = createSafeJson();
        response.put("type", "alarm");
        JSONObject msgData = createSafeJson();
        msgData.put("from", safeString(userInfo.userId));
        msgData.put("nickname", safeString(userInfo.nickname));
        msgData.put("alarmType", safeString(alarmType));
        msgData.put("alarmMessage", safeString(alarmMessage));
        msgData.put("timestamp", System.currentTimeMillis());
        response.put("data", msgData);

        broadcastToTheme(userInfo.themeId, response);
        log.info("🚨 报警：{} -> {}", userInfo.userId, alarmMessage);
    }

    private void handleHeartRateUpdate(UserInfo userInfo, JSONObject data) {
        Integer heartRate = data.getInteger("heartRate");
        if (heartRate == null) heartRate = 0;

        JSONObject response = createSafeJson();
        response.put("type", "heartRateUpdate");
        JSONObject msgData = createSafeJson();
        msgData.put("from", safeString(userInfo.userId));
        msgData.put("nickname", safeString(userInfo.nickname));
        msgData.put("heartRate", heartRate);
        msgData.put("timestamp", System.currentTimeMillis());
        response.put("data", msgData);

        broadcastToTheme(userInfo.themeId, response);
        log.info("💓 心率更新：{} -> {} bpm", userInfo.userId, heartRate);
    }

    private void handleKick(UserInfo userInfo, JSONObject data) throws IOException {
        if (!"admin".equals(userInfo.role)) {
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", "权限不足");
            errorData.put("code", "PERMISSION_DENIED");
            error.put("data", errorData);
            safeSend(SESSIONS.get(USER_SESSION_MAP.get(userInfo.userId)), error);
            return;
        }

        String targetUserId = data.getString("userId");
        if (targetUserId == null) return;

        if (targetUserId.equals(userInfo.userId)) {
            JSONObject error = createSafeJson();
            error.put("type", "error");
            JSONObject errorData = createSafeJson();
            errorData.put("message", "不能踢出自己");
            errorData.put("code", "CANNOT_KICK_SELF");
            error.put("data", errorData);
            safeSend(SESSIONS.get(USER_SESSION_MAP.get(userInfo.userId)), error);
            return;
        }

        String targetSessionId = USER_SESSION_MAP.get(targetUserId);
        if (targetSessionId != null) {
            WebSocketSession targetSession = SESSIONS.get(targetSessionId);
            if (targetSession != null && targetSession.isOpen()) {
                JSONObject kickMsg = createSafeJson();
                kickMsg.put("type", "system");
                JSONObject kickData = createSafeJson();
                kickData.put("message", "您已被管理员 " + userInfo.userId + " 踢出");
                kickMsg.put("data", kickData);
                safeSend(targetSession, kickMsg);
                targetSession.close(CloseStatus.POLICY_VIOLATION);
                cleanupSession(targetSessionId);
            }
        }

        log.info("👢 用户被踢出：{}", targetUserId);
        broadcastUserList();
        broadcastRoomList();
    }

    // =============================================
    // 发送列表方法
    // =============================================

    private void sendUserList(WebSocketSession session) throws IOException {
        List<JSONObject> userList = new ArrayList<>();
        for (String sid : SESSIONS.keySet()) {
            UserInfo info = USER_INFO_MAP.get(sid);
            if (info != null) {
                userList.add(info.toJson());
            }
        }

        JSONObject response = createSafeJson();
        response.put("type", "userList");
        response.put("data", userList);
        response.put("timestamp", System.currentTimeMillis());
        safeSend(session, response);
    }

    private void sendThemeUserList(WebSocketSession session, String themeId) throws IOException {
        List<JSONObject> userList = new ArrayList<>();
        if (themeId != null && !themeId.isEmpty()) {
            RoomInfo roomInfo = THEME_ROOMS.get(themeId);
            if (roomInfo != null) {
                for (String sid : roomInfo.userIds) {
                    UserInfo info = USER_INFO_MAP.get(sid);
                    if (info != null) {
                        userList.add(info.toJson());
                    }
                }
            }
        }

        JSONObject response = createSafeJson();
        response.put("type", "themeUserList");
        response.put("themeId", safeString(themeId));
        response.put("data", userList);
        response.put("timestamp", System.currentTimeMillis());
        safeSend(session, response);
    }
    // =============================================
    // 广播方法
    // =============================================

    private void broadcast(String message) {
        for (WebSocketSession session : SESSIONS.values()) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                    TOTAL_MESSAGES_SENT.incrementAndGet();
                }
            } catch (IOException e) {
                log.error("广播消息失败", e);
            }
        }
    }

    private void broadcastToTheme(String themeId, JSONObject message) {
        if (themeId == null || themeId.isEmpty()) {
            log.warn("主题ID为空，跳过广播");
            return;
        }

        String jsonString = JSON.toJSONString(message);
        RoomInfo roomInfo = THEME_ROOMS.get(themeId);
        if (roomInfo != null) {
            for (String sid : roomInfo.userIds) {
                WebSocketSession session = SESSIONS.get(sid);
                try {
                    if (session != null && session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonString));
                        TOTAL_MESSAGES_SENT.incrementAndGet();
                    }
                } catch (IOException e) {
                    log.error("广播到主题失败", e);
                }
            }
        }
    }
    private void sendToUser(String userId, String message) {
        String sid = USER_SESSION_MAP.get(userId);
        if (sid != null) {
            WebSocketSession session = SESSIONS.get(sid);
            try {
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                    TOTAL_MESSAGES_SENT.incrementAndGet();
                }
            } catch (IOException e) {
                log.error("发送消息给用户失败: {}", userId, e);
            }
        }
    }

    private void broadcastUserList() {
        List<JSONObject> userList = new ArrayList<>();
        for (String sid : SESSIONS.keySet()) {
            UserInfo info = USER_INFO_MAP.get(sid);
            if (info != null) {
                userList.add(info.toJson());
            }
        }

        JSONObject response = createSafeJson();
        response.put("type", "userList");
        response.put("data", userList);
        response.put("timestamp", System.currentTimeMillis());

        String jsonString = JSON.toJSONString(response);
        broadcast(jsonString);
    }

    private void broadcastThemeUserList(String themeId) {
        if (themeId == null || themeId.isEmpty()) {
            return;
        }

        List<JSONObject> userList = new ArrayList<>();
        RoomInfo roomInfo = THEME_ROOMS.get(themeId);
        if (roomInfo != null) {
            for (String sid : roomInfo.userIds) {
                UserInfo info = USER_INFO_MAP.get(sid);
                if (info != null) {
                    userList.add(info.toJson());
                }
            }
        }

        JSONObject response = createSafeJson();
        response.put("type", "themeUserList");
        response.put("themeId", safeString(themeId));
        response.put("data", userList);
        response.put("timestamp", System.currentTimeMillis());

        String jsonString = JSON.toJSONString(response);
        if (roomInfo != null) {
            for (String sid : roomInfo.userIds) {
                WebSocketSession session = SESSIONS.get(sid);
                try {
                    if (session != null && session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonString));
                        TOTAL_MESSAGES_SENT.incrementAndGet();
                    }
                } catch (IOException e) {
                    log.error("广播主题用户列表失败", e);
                }
            }
        }
    }

    // =============================================
    // 辅助方法
    // =============================================

    private void removeFromTheme(String sessionId) {
        for (Map.Entry<String, RoomInfo> entry : THEME_ROOMS.entrySet()) {
            RoomInfo roomInfo = entry.getValue();
            roomInfo.userIds.remove(sessionId);
            if (roomInfo.userIds.isEmpty()) {
                THEME_ROOMS.remove(entry.getKey());
                log.debug("房间 {} 已空，自动删除", entry.getKey());
            }
        }
    }

    private void removeFromTheme(String sessionId, String themeId) {
        if (themeId == null || themeId.isEmpty()) {
            return;
        }
        RoomInfo roomInfo = THEME_ROOMS.get(themeId);
        if (roomInfo != null) {
            roomInfo.userIds.remove(sessionId);
            if (roomInfo.userIds.isEmpty()) {
                THEME_ROOMS.remove(themeId);
                log.debug("房间 {} 已空，自动删除", themeId);
            }
        }
    }
    // =============================================
    // 连接关闭（增强）
    // =============================================

  @Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    String sid = session.getId();
    UserInfo userInfo = USER_INFO_MAP.get(sid);

    if (userInfo != null) {
        String themeId = userInfo.themeId;
        String userId = userInfo.userId;
        String nickname = userInfo.nickname;

        // ✅ 先从所有数据结构中移除用户
        removeFromTheme(sid);
        USER_SESSION_MAP.remove(userId);
        USER_INFO_MAP.remove(sid);
        SESSION_CONTEXT.remove(sid);
        MESSAGE_TIMESTAMPS.remove(sid);

        PENDING_MESSAGES.entrySet().removeIf(entry -> entry.getValue().sessionId.equals(sid));

        // ✅ 先从 SESSIONS 中移除，避免广播时发给自己
        SESSIONS.remove(sid);

        // ✅ 广播更新（此时用户已被移除，不会再广播给自己）
        try {
            broadcastUserList();
        } catch (Exception e) {
            log.debug("广播用户列表失败: {}", e.getMessage());
        }

        if (themeId != null && !themeId.isEmpty()) {
            try {
                broadcastThemeUserList(themeId);
                JSONObject leaveMsg = createSafeJson();
                leaveMsg.put("type", "userLeft");
                leaveMsg.put("data", userInfo.toJson());
                leaveMsg.put("timestamp", System.currentTimeMillis());
                broadcastToTheme(themeId, leaveMsg);
            } catch (Exception e) {
                log.debug("广播主题用户列表失败: {}", e.getMessage());
            }
        }

        try {
            broadcastRoomList();
        } catch (Exception e) {
            log.debug("广播房间列表失败: {}", e.getMessage());
        }

        log.info("❌ WebSocket 连接断开！用户: {} ({}), 主题: {}, 当前在线: {}",
                userId, nickname, themeId != null ? themeId : "无", SESSIONS.size());
    } else {
        // ✅ 如果 userInfo 为 null，仍然清理 SESSIONS
        SESSIONS.remove(sid);
        log.info("❌ WebSocket 连接断开（未知用户），当前在线：{}", SESSIONS.size());
    }
}
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sid = session.getId();
        log.error("WebSocket 传输错误：{}", exception.getMessage());

        UserInfo userInfo = USER_INFO_MAP.get(sid);
        if (userInfo != null) {
            USER_SESSION_MAP.remove(userInfo.userId);
            USER_INFO_MAP.remove(sid);
            SESSION_CONTEXT.remove(sid);
            MESSAGE_TIMESTAMPS.remove(sid);
            removeFromTheme(sid);
            broadcastUserList();
            broadcastRoomList();
        }
        SESSIONS.remove(sid);
    }

    // =============================================
    // 对外接口（增强）
    // =============================================

    public static int getOnlineCount() {
        return SESSIONS.size();
    }

    public static List<String> getOnlineUsers() {
        return new ArrayList<>(USER_SESSION_MAP.keySet());
    }

    public JSONObject getFullStatistics() {
        JSONObject stats = createSafeJson();

        stats.put("onlineCount", SESSIONS.size());
        stats.put("userCount", USER_SESSION_MAP.size());
        stats.put("roomCount", THEME_ROOMS.size());
        stats.put("totalMessagesReceived", TOTAL_MESSAGES_RECEIVED.get());
        stats.put("totalMessagesRejected", TOTAL_MESSAGES_REJECTED.get());
        stats.put("totalMessagesSent", TOTAL_MESSAGES_SENT.get());
        stats.put("pendingAckCount", PENDING_MESSAGES.size());

        List<JSONObject> roomDetails = new ArrayList<>();
        for (Map.Entry<String, RoomInfo> entry : THEME_ROOMS.entrySet()) {
            RoomInfo roomInfo = entry.getValue();
            JSONObject room = new JSONObject();
            room.put("roomId", entry.getKey());
            room.put("roomName", roomInfo.roomName != null ? roomInfo.roomName : entry.getKey());
            room.put("userCount", roomInfo.userIds.size());
            List<String> users = new ArrayList<>();
            for (String sid : roomInfo.userIds) {
                UserInfo info = USER_INFO_MAP.get(sid);
                if (info != null) {
                    users.add(info.userId);
                }
            }
            room.put("users", users);
            roomDetails.add(room);
        }
        stats.put("rooms", roomDetails);

        List<JSONObject> sessionDetails = new ArrayList<>();
        for (Map.Entry<String, SessionContext> entry : SESSION_CONTEXT.entrySet()) {
            sessionDetails.add(entry.getValue().toJson());
        }
        stats.put("sessions", sessionDetails);

        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }
    public JSONObject getSessionInfo(String userId) {
        String sessionId = USER_SESSION_MAP.get(userId);
        if (sessionId == null) {
            JSONObject result = createSafeJson();
            result.put("found", false);
            return result;
        }

        SessionContext ctx = SESSION_CONTEXT.get(sessionId);
        UserInfo userInfo = USER_INFO_MAP.get(sessionId);

        JSONObject result = createSafeJson();
        result.put("found", true);
        if (ctx != null) {
            result.put("session", ctx.toJson());
        }
        if (userInfo != null) {
            result.put("user", userInfo.toJson());
        }
        return result;
    }

    public JSONObject getHealthStatus() {
        JSONObject health = createSafeJson();
        health.put("status", "UP");
        health.put("onlineCount", SESSIONS.size());
        health.put("pendingAckCount", PENDING_MESSAGES.size());
        health.put("messageQueueSize", MESSAGE_TIMESTAMPS.size());
        health.put("uptime", System.currentTimeMillis() - startTime);
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }

    private long startTime = System.currentTimeMillis();

    public void broadcastSystemMessage(String message) {
        JSONObject systemMsg = createSafeJson();
        systemMsg.put("type", "system");
        JSONObject data = createSafeJson();
        data.put("message", safeString(message));
        data.put("level", "info");
        systemMsg.put("data", data);
        systemMsg.put("timestamp", System.currentTimeMillis());
        broadcast(JSON.toJSONString(systemMsg));
        log.info("📢 系统广播：{}", message);
    }

    public void broadcastSystemMessageToTheme(String themeId, String message) {
        JSONObject systemMsg = createSafeJson();
        systemMsg.put("type", "system");
        JSONObject data = createSafeJson();
        data.put("message", safeString(message));
        data.put("level", "info");
        data.put("themeId", themeId);
        systemMsg.put("data", data);
        systemMsg.put("timestamp", System.currentTimeMillis());
        broadcastToTheme(themeId, systemMsg);
        log.info("📢 房间广播 [{}]：{}", themeId, message);
    }

    public void resetStatistics() {
        TOTAL_MESSAGES_RECEIVED.set(0);
        TOTAL_MESSAGES_REJECTED.set(0);
        TOTAL_MESSAGES_SENT.set(0);
        PENDING_MESSAGES.clear();
        MESSAGE_TIMESTAMPS.clear();
        log.info("📊 统计信息已重置");
    }

    public int cleanupIdleSessions(long idleThresholdMinutes) {
        int cleaned = 0;
        long threshold = System.currentTimeMillis() - (idleThresholdMinutes * 60 * 1000);

        for (Map.Entry<String, SessionContext> entry : SESSION_CONTEXT.entrySet()) {
            SessionContext ctx = entry.getValue();
            if (ctx.lastActivityTime.toEpochMilli() < threshold) {
                String sessionId = entry.getKey();
                WebSocketSession session = SESSIONS.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        JSONObject idleMsg = createSafeJson();
                        idleMsg.put("type", "system");
                        JSONObject data = createSafeJson();
                        data.put("message", "连接空闲超时，已自动断开");
                        data.put("code", "IDLE_TIMEOUT");
                        idleMsg.put("data", data);
                        safeSend(session, idleMsg);
                        session.close(CloseStatus.SESSION_NOT_RELIABLE);
                    } catch (IOException e) {
                        log.error("关闭空闲会话失败", e);
                    }
                }
                cleanupSession(sessionId);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("🧹 清理空闲会话: {} 个 (空闲阈值: {}分钟)", cleaned, idleThresholdMinutes);
            broadcastUserList();
            broadcastRoomList();
        }
        return cleaned;
    }


}

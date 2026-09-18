// com.ruoyi.websocket.server.WebSocketSessionManager
package org.ruoyi.ecg.websocket.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;


import org.ruoyi.ecg.websocket.model.WebSocketMessage;

import jakarta.websocket.Session;

/**
 * WebSocket 会话管理器
 *
 * @author ruoyi
 */
@Component
public class WebSocketChatManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChatManager.class);

    /**
     * 所有在线会话
     */
    private static final CopyOnWriteArraySet<Session> SESSIONS = new CopyOnWriteArraySet<>();

    /**
     * 用户ID -> Session 映射
     */
    private static final ConcurrentHashMap<String, Session> USER_SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 主题ID -> 用户ID列表 映射
     */
    private static final ConcurrentHashMap<String, List<String>> THEME_USER_MAP = new ConcurrentHashMap<>();

    /**
     * 当前在线人数
     */
    private static volatile int onlineCount = 0;

    // =============================================
    // 会话管理
    // =============================================

    public static void addSession(Session session, String userId, String themeId) {
        SESSIONS.add(session);
        USER_SESSION_MAP.put(userId, session);

        THEME_USER_MAP.computeIfAbsent(themeId, k -> new ArrayList<>())
                      .add(userId);

        onlineCount++;

        log.info("【WebSocket】用户：{}，主题：{} 上线，当前在线：{}", userId, themeId, onlineCount);
    }

    public static void removeSession(Session session) {
        String userId = getUserId(session);
        String themeId = getThemeId(session);

        SESSIONS.remove(session);
        USER_SESSION_MAP.remove(userId);

        if (themeId != null && THEME_USER_MAP.containsKey(themeId)) {
            List<String> users = THEME_USER_MAP.get(themeId);
            users.remove(userId);
            if (users.isEmpty()) {
                THEME_USER_MAP.remove(themeId);
            }
        }

        if (onlineCount > 0) {
            onlineCount--;
        }

        log.info("【WebSocket】用户：{} 下线，当前在线：{}", userId, onlineCount);
    }

    public static String getUserId(Session session) {
        return session.getUserProperties().getOrDefault("userId", "").toString();
    }

    public static String getThemeId(Session session) {
        return session.getUserProperties().getOrDefault("themeId", "").toString();
    }

    public static void setUserId(Session session, String userId) {
        session.getUserProperties().put("userId", userId);
    }

    public static void setThemeId(Session session, String themeId) {
        session.getUserProperties().put("themeId", themeId);
    }

    // =============================================
    // 发送消息
    // =============================================

    public static boolean sendToUser(String userId, String message) {
        Session session = USER_SESSION_MAP.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                log.debug("【WebSocket】发送消息到用户：{}", userId);
                return true;
            } catch (IOException e) {
                log.error("【WebSocket】发送消息到用户：{} 失败", userId, e);
                return false;
            }
        }
        return false;
    }

    public static boolean sendToUser(String userId, WebSocketMessage<?> message) {
        return sendToUser(userId, message.toJson());
    }

    public static int sendToTheme(String themeId, String message) {
        int count = 0;
        List<String> users = THEME_USER_MAP.get(themeId);
        if (users != null) {
            for (String userId : users) {
                if (sendToUser(userId, message)) {
                    count++;
                }
            }
        }
        log.debug("【WebSocket】发送消息到主题：{}，接收数：{}", themeId, count);
        return count;
    }

    public static int sendToTheme(String themeId, WebSocketMessage<?> message) {
        return sendToTheme(themeId, message.toJson());
    }

    public static int broadcast(String message) {
        int count = 0;
        for (Session session : SESSIONS) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                    count++;
                } catch (IOException e) {
                    log.error("【WebSocket】广播消息失败", e);
                }
            }
        }
        log.debug("【WebSocket】广播消息，接收数：{}", count);
        return count;
    }

    public static int broadcast(WebSocketMessage<?> message) {
        return broadcast(message.toJson());
    }

    // =============================================
    // 获取信息
    // =============================================

    public static int getOnlineCount() {
        return onlineCount;
    }

    public static List<String> getUsersByTheme(String themeId) {
        return THEME_USER_MAP.getOrDefault(themeId, new ArrayList<>());
    }

    public static List<String> getAllUsers() {
        return new ArrayList<>(USER_SESSION_MAP.keySet());
    }

    public static boolean isOnline(String userId) {
        return USER_SESSION_MAP.containsKey(userId);
    }

    public static JSONObject getStatus() {
        JSONObject status = new JSONObject();
        try {
            status.put("onlineCount", onlineCount);
            status.put("sessionCount", SESSIONS.size());
            status.put("userCount", USER_SESSION_MAP.size());
            status.put("themeCount", THEME_USER_MAP.size());
            status.put("users", new ArrayList<>(USER_SESSION_MAP.keySet()));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return status;
    }
}

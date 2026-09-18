package com.ruoyi.ems.room.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 房间消息发送。所有 WebSocket 出站消息的唯一出口。
 * 支持：单播、按角色、按多角色、广播、排除某角色。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomBroadcaster {

    private final RoomRegistry roomRegistry;
    private final WsSessionRegistry wsRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 单播 ====================

    public void sendToClient(String sessionId, String clientId, Map<String, Object> message) {
        if (sessionId == null || clientId == null || message == null) return;

        Session session = wsRegistry.get(sessionId, clientId);
        if (session == null) {
            log.debug("单播跳过：连接不存在 sessionId={}, clientId={}", sessionId, clientId);
            return;
        }
        sendRaw(session, message);
    }

    // ==================== 按角色 ====================

    public void sendToRole(String sessionId, Role role, Map<String, Object> message) {
        if (role == null) return;
        sendToRoles(sessionId, Set.of(role), message);
    }

    public void sendToRoles(String sessionId, Set<Role> roles, Map<String, Object> message) {
        if (sessionId == null || message == null || roles == null || roles.isEmpty()) return;

        Optional<SessionRoom> roomOpt = roomRegistry.get(sessionId);
        if (roomOpt.isEmpty()) {
            log.debug("发送跳过：房间不存在 sessionId={}", sessionId);
            return;
        }

        String json = serialize(message);
        if (json == null) return;

        SessionRoom room = roomOpt.get();
        for (Role role : roles) {
            for (ClientConnection conn : room.getClientsByRole(role)) {
                Session session = wsRegistry.get(sessionId, conn.getClientId());
                if (session == null || !session.isOpen()) continue;
                sendRawJson(session, json);
            }
        }
    }

    // ==================== 广播 ====================

    /**
     * 广播给房间内所有客户端（包括引擎、学员、观察者、导师）。
     */
    public void broadcast(String sessionId, Map<String, Object> message) {
        if (sessionId == null || message == null) return;

        Optional<SessionRoom> roomOpt = roomRegistry.get(sessionId);
        if (roomOpt.isEmpty()) {
            log.warn("⚠️ 广播跳过：房间不存在 sessionId={}, type={}",
                sessionId, message.get("type"));
            return;
        }

        String json = serialize(message);
        if (json == null) return;

        for (ClientConnection conn : roomOpt.get().getAllClients()) {
            Session session = wsRegistry.get(sessionId, conn.getClientId());
            if (session == null || !session.isOpen()) continue;
            sendRawJson(session, json);
        }
    }

    /**
     * 广播给房间内除指定角色外的所有客户端。
     *
     * @param excludeRole 要排除的角色。传 null 表示不排除任何角色（等同 broadcast）。
     */
    public void broadcastExcept(String sessionId, Role excludeRole, Map<String, Object> message) {
        if (sessionId == null || message == null) return;

        Optional<SessionRoom> roomOpt = roomRegistry.get(sessionId);
        if (roomOpt.isEmpty()) return;

        String json = serialize(message);
        if (json == null) return;

        for (ClientConnection conn : roomOpt.get().getAllClients()) {
            if (excludeRole != null && conn.getRole() == excludeRole) continue;
            Session session = wsRegistry.get(sessionId, conn.getClientId());
            if (session == null || !session.isOpen()) continue;
            sendRawJson(session, json);
        }
    }

    /**
     * 广播给指定 clientId 之外的所有客户端。
     * 用于"通知其他人有新成员加入"的场景，避免发给自己。
     */
    public void broadcastExceptClient(String sessionId, String excludeClientId,
                                      Map<String, Object> message) {
        if (sessionId == null || message == null) return;

        Optional<SessionRoom> roomOpt = roomRegistry.get(sessionId);
        if (roomOpt.isEmpty()) return;

        String json = serialize(message);
        if (json == null) return;

        for (ClientConnection conn : roomOpt.get().getAllClients()) {
            if (excludeClientId != null && excludeClientId.equals(conn.getClientId())) continue;
            Session session = wsRegistry.get(sessionId, conn.getClientId());
            if (session == null || !session.isOpen()) continue;
            sendRawJson(session, json);
        }
    }

    // ==================== 内部 ====================

    private void sendRaw(Session session, Map<String, Object> message) {
        if (session == null) return;
        String json = serialize(message);
        if (json != null) sendRawJson(session, json);
    }

    private void sendRawJson(Session session, String json) {
        if (session == null || json == null) return;
        try {
            synchronized (session) {
                session.getBasicRemote().sendText(json);
            }
        } catch (Exception e) {
            log.error("发送消息失败: sessionId={}", session.getId(), e);
        }
    }

    private String serialize(Map<String, Object> message) {
        if (message == null) return null;
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            return null;
        }
    }

    // ==================== 便捷封装 ====================

    public static Map<String, Object> envelope(String type, Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("data", data != null ? data : Map.of());
        m.put("timestamp", System.currentTimeMillis());
        return m;
    }

    public static Map<String, Object> envelope(String type, String requestId, Object data) {
        Map<String, Object> m = envelope(type, data);
        if (requestId != null) m.put("requestId", requestId);
        return m;
    }
}

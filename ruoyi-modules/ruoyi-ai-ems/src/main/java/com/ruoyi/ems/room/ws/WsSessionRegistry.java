package com.ruoyi.ems.room.ws;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 物理连接注册表。
 * sessionId -> (clientId -> Session)
 * 只负责连接的存取和状态查询，不做业务判断。
 */
@Slf4j
@Component
public class WsSessionRegistry {

    private final Map<String, Map<String, Session>> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String clientId, Session session) {
        if (sessionId == null || clientId == null || session == null) {
            throw new IllegalArgumentException(
                "register 参数不能为 null: sessionId=" + sessionId
                    + ", clientId=" + clientId + ", session=" + session);
        }
        sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(clientId, session);
        log.debug("WS 注册: sessionId={}, clientId={}, 当前连接数={}",
            sessionId, clientId, sessions.get(sessionId).size());
    }

    public void unregister(String sessionId, String clientId) {
        Map<String, Session> clients = sessions.get(sessionId);
        if (clients == null) return;
        clients.remove(clientId);
        if (clients.isEmpty()) {
            sessions.remove(sessionId);
        }
        log.debug("WS 注销: sessionId={}, clientId={}", sessionId, clientId);
    }

    public Session get(String sessionId, String clientId) {
        Map<String, Session> clients = sessions.get(sessionId);
        return clients != null ? clients.get(clientId) : null;
    }

    public Map<String, Session> getAll(String sessionId) {
        Map<String, Session> clients = sessions.get(sessionId);
        return clients != null ? Map.copyOf(clients) : Map.of();
    }

    public Set<String> getClientIds(String sessionId) {
        Map<String, Session> clients = sessions.get(sessionId);
        return clients != null ? Set.copyOf(clients.keySet()) : Set.of();
    }

    public int count(String sessionId) {
        Map<String, Session> clients = sessions.get(sessionId);
        return clients != null ? clients.size() : 0;
    }

    public boolean isEmpty(String sessionId) {
        return count(sessionId) == 0;
    }
}

package com.ruoyi.ems.websocket;

import jakarta.websocket.Session;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WebSocketSessionManager {
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, Session session) { sessions.put(sessionId, session); }
    public void unregister(String sessionId) { sessions.remove(sessionId); }
    public Optional<Session> get(String sessionId) { return Optional.ofNullable(sessions.get(sessionId)); }
    public boolean isOpen(String sessionId) {
        Session s = sessions.get(sessionId);
        return s != null && s.isOpen();
    }
}

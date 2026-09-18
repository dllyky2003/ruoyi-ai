package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.room.model.Role;

import java.util.Map;

public record WsMessageContext(
    String sessionId,
    String clientId,
    Role role,
    Map<String, Object> message
) {
    @SuppressWarnings("unchecked")
    public Map<String, Object> data() {
        Object d = message.get("data");
        return d instanceof Map ? (Map<String, Object>) d : Map.of();
    }

    public String requestId() {
        return (String) message.get("requestId");
    }
}

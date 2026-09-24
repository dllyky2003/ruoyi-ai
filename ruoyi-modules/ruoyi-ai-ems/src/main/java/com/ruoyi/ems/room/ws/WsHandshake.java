package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.room.model.Role;
import jakarta.websocket.Session;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手参数解析。从 URL query 中提取 role / token。
 */
public final class WsHandshake {

    private WsHandshake() {}

    public static String getQuery(Session session, String key) {
        try {
            Map<String, List<String>> params = session.getRequestParameterMap();
            if (params == null) return null;
            List<String> values = params.get(key);
            return values != null && !values.isEmpty() ? values.get(0) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Role getRole(Session session) {
        return Role.fromCode(getQuery(session, "role"));
    }

    public static String getToken(Session session) {
        return getQuery(session, "token");
    }


    public static String getAttemptId(Session session) {
        return getQuery(session, "attemptId");
    }
}

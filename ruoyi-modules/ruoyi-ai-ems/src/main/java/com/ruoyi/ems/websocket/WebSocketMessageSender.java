package com.ruoyi.ems.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import com.ruoyi.ems.room.model.Role;


@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessageSender {

    private final ObjectMapper objectMapper;
    private final RoomRegistry roomRegistry;
    private final RoomBroadcaster roomBroadcaster;

    /**
     * 通用下行（默认广播）。保留原签名，走语义化 type。
     */
    public void send(String sessionId, Object data) {
        send(sessionId, "engine_message", data);
    }

    /**
     * 指定 type 的下行。语义化 type 供 Unity 端 switch 分派。
     */
    public void send(String sessionId, String type, Object data) {
        String roomId = resolveRoomId(sessionId);
        if (roomId == null) {
            log.warn("会话 {} 不存在或已关闭，消息丢弃", sessionId);
            return;
        }
        Map<String, Object> env = RoomBroadcaster.envelope(type, data);
        log.debug("WS 发送: type={}, 入参={}, room={}", type, sessionId, roomId);
        roomBroadcaster.broadcast(roomId, env);
    }

    /**
     * 只给引擎端（Unity 学员端）。状态变更、上下文更新用这个。
     */
    public void sendToEngine(String sessionId, String type, Object data) {
        String roomId = resolveRoomId(sessionId);
        if (roomId == null) {
            log.warn("会话 {} 不存在或已关闭，消息丢弃", sessionId);
            return;
        }
        Map<String, Object> env = RoomBroadcaster.envelope(type, data);
        log.debug("WS 发送(engine): type={}, 入参={}, room={}", type, sessionId, roomId);
        roomBroadcaster.sendToRole(roomId, Role.ENGINE, env);
    }

    private String resolveRoomId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        if (roomRegistry.exists(sessionId)) return sessionId;
        return roomRegistry.getByEmsSessionId(sessionId)
            .map(SessionRoom::getSessionId)
            .orElse(null);
    }


    // ===== 下面两个方法保持不变（它们直接接收 Session，不经过这里的解析）=====

    public void sendTranscript(Session session, String text, boolean isFinal) {
        if (session == null || !session.isOpen()) {
            log.warn("会话不存在或已关闭，消息丢弃");
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                "type", "transcript",
                "text", text,
                "isFinal", isFinal,
                "timestamp", System.currentTimeMillis()
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(payload));
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败", e);
        }
    }

    public void sendMessage(Session session, String type, Object data) {
        if (session == null || !session.isOpen()) {
            log.warn("会话不存在或已关闭，消息丢弃");
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                "type", type,
                "data", data != null ? data : "",
                "timestamp", System.currentTimeMillis()
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(payload));
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败", e);
        }
    }
}

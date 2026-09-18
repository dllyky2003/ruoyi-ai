package com.ruoyi.ems.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.asr.AsrWebSocketHandler;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessageSender {

    private final ObjectMapper objectMapper;



    public void send(String sessionId, Object data) {
        Session session = AsrWebSocketHandler.getSession(sessionId);
        if (session == null) {
            log.warn("会话 {} 不存在或已关闭，消息丢弃", sessionId);
            return;
        }
        try {
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(data));
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }

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

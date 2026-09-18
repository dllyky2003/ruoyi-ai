package com.ruoyi.ems.room.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.room.model.Role;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket 上行消息路由。
 * 业务模块（生理引擎、NPC 动作、状态机）通过实现 WsMessageHandler 注册。
 */
@Slf4j
@Component
public class WsMessageRouter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<WsMessageHandler> handlers = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    public void registerHandlers(List<WsMessageHandler> handlerList) {
        if (handlerList != null) {
            handlers.addAll(handlerList);
            log.info("已注册 {} 个 WebSocket 消息处理器", handlers.size());
        }
    }

    public void route(String sessionId, String clientId, Role role, String json) {
        Map<String, Object> msg;
        try {
            msg = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("解析 WS 消息失败: sessionId={}, clientId={}", sessionId, clientId, e);
            return;
        }

        String type = (String) msg.get("type");
        if (type == null) {
            log.warn("WS 消息缺少 type: sessionId={}", sessionId);
            return;
        }

        // ping 直接响应，不进业务路由
        if ("ping".equals(type)) {
            WsMessageContext ctx = new WsMessageContext(sessionId, clientId, role, msg);
            // 响应由 handler 发，这里不处理
        }

        WsMessageContext ctx = new WsMessageContext(sessionId, clientId, role, msg);
        boolean handled = false;
        for (WsMessageHandler h : handlers) {
            if (h.supports(type)) {
                try {
                    h.handle(ctx);
                    handled = true;
                } catch (Exception e) {
                    log.error("处理器 {} 处理消息 {} 失败", h.getClass().getSimpleName(), type, e);
                }
            }
        }

        if (!handled) {
            log.debug("未处理的消息: type={}, sessionId={}", type, sessionId);
        }
    }

    /** 处理器注册接口 */
    public interface WsMessageHandler {
        boolean supports(String type);
        void handle(WsMessageContext ctx);
    }
}

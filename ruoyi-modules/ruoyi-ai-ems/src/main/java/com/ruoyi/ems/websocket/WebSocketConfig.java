package com.ruoyi.ems.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        System.out.println(">>> WebSocket ServerEndpointExporter 已注册，端点路径为 /ws/asr/{sessionId}");
        return new ServerEndpointExporter();
    }
}

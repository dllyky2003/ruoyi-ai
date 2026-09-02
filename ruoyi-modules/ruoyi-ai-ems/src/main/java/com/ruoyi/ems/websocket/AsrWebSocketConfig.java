package com.ruoyi.ems.websocket;

import com.ruoyi.ems.engine.asr.AsrClient;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * ASR WebSocket后置配置
 */
@Configuration
public class AsrWebSocketConfig {

    private final AsrClient asrClient;

    public AsrWebSocketConfig(AsrClient asrClient) {
        this.asrClient = asrClient;
    }

    @PostConstruct
    public void init() {
        // 注入AsrClient到WebSocketHandler
        AsrWebSocketHandler.setAsrClient(asrClient);
    }
}

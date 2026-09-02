package com.ruoyi.ems.npc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VR客户端通信服务（示例实现）
 * 实际使用时需替换为WebSocket或HTTP客户端
 */
@Slf4j
@Service
public class VrClientService {

    // 模拟连接管理（实际应使用WebSocket Session）
    private final Map<String, Object> sessionMap = new ConcurrentHashMap<>();

    /**
     * 向指定会话的VR客户端发送动画指令
     * @param sessionId 会话ID
     * @param animation 动画名称
     * @param durationSeconds 动画时长（秒）
     */
    public void sendAnimation(String sessionId, String animation, int durationSeconds) {
        log.info("🎬 [{}] 发送VR动画: {} ({}s)", sessionId, animation, durationSeconds);
        // TODO: 实际通过WebSocket发送指令
        // webSocketClient.send(sessionId, JSON.stringify({type:"ANIMATION", name:animation, duration:durationSeconds}));
        // 模拟发送成功
    }

    /**
     * 发送语音合成指令（可选）
     */
    public void sendSpeech(String sessionId, String text) {
        log.info("🔊 [{}] 发送TTS语音: {}", sessionId, text);
        // TODO: 实际通过WebSocket发送语音指令
    }

    /**
     * 检查客户端是否在线
     */
    public boolean isClientConnected(String sessionId) {
        return sessionMap.containsKey(sessionId);
    }
}

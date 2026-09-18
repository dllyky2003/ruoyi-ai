package com.ruoyi.ems.npc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface IVrClientService {

    /**
     * 发送动作指令（异步，返回 CompletableFuture）
     */
    CompletableFuture<Void> sendAction(String sessionId, String action, Map<String, Object> params,
                                       String speech, int durationSeconds);

    /**
     * 发送纯语音（不等待）
     */
    void sendSpeech(String sessionId, String text);

    /**
     * 检查客户端是否在线
     */
    boolean isClientConnected(String sessionId);

    /**
     * 发送动画指令（旧接口）
     */
    void sendAnimation(String sessionId, String animation, int durationSeconds);
}

package com.ruoyi.ems.npc;

import com.ruoyi.ems.tts.KokoroTtsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
@Slf4j
@Service
@RequiredArgsConstructor
public class VrClientService implements IVrClientService {

    private final KokoroTtsClient kokoroTtsClient;
    private final NpcUnityBridgeService npcBridge;

    @Override
    public CompletableFuture<Void> sendAction(String sessionId, String action,
                                              Map<String, Object> params,
                                              String speech, int durationSeconds) {
        // 1. 下发动作（如果 action 能映射到 npcId 就带上，没有就先广播）
        npcBridge.pushAction(sessionId, resolveNpcId(params), action, params, durationSeconds);

        // 2. 有 speech 就同时下发对话
        if (speech != null && !speech.isBlank()) {
            npcBridge.pushDialogue(sessionId, resolveNpcId(params), speech, "zm_098", false);
        }

        // 3. 本地 TTS 仍然保留（如果 Kokoro 是生成音频再发走，就留；如果是本地朗读就去掉）
        try {
            if (speech != null && !speech.isBlank()) {
                kokoroTtsClient.speak(sessionId, speech);
            }
        } catch (Exception e) {
            log.warn("TTS 失败: {}", e.getMessage());
        }

        // 4. 返回一个已完成的 future，不再 sleep
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void sendSpeech(String sessionId, String text) {
        npcBridge.pushDialogue(sessionId, null, text, "zm_098", false);
    }

    @Override
    public boolean isClientConnected(String sessionId) { return true; }

    @Override
    public void sendAnimation(String sessionId, String animation, int durationSeconds) {
        npcBridge.pushAction(sessionId, null, animation, Map.of(), durationSeconds);
    }

    private String resolveNpcId(Map<String, Object> params) {
        if (params == null) return null;
        Object id = params.get("npcId");
        return id == null ? null : id.toString();
    }
}

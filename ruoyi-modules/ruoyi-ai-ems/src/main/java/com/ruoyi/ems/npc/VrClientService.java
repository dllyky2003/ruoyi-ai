package com.ruoyi.ems.npc;

import com.ruoyi.ems.tts.KokoroTtsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class VrClientService implements IVrClientService {

    private final KokoroTtsClient kokoroTtsClient;
    private final NpcUnityBridgeService npcBridge;
    private final NpcSpeakerResolver speakerResolver;   // ★ 新增

    @Override
    public CompletableFuture<Void> sendAction(String sessionId, String action,
                                              Map<String, Object> params,
                                              String speech, int durationSeconds) {
        // 1. 先算 npcId：params 里有就用；没有就按 action 找执行人
        String npcId = resolveNpcId(params);
        if (npcId == null) {
            npcId = speakerResolver.resolveByAction(sessionId, action, params);
        }

        // 2. 下发动作
        npcBridge.pushAction(sessionId, npcId, action, params, durationSeconds);

        // 3. 有 speech 就下发对话
        if (speech != null && !speech.isBlank()) {
            // 对话的说话人跟动作执行人可能不同 —— 先按 action 得到的 npcId 用，用不了再按 text 反查
            String speakerId = npcId != null
                ? npcId
                : speakerResolver.resolveByText(sessionId, speech);
            npcBridge.pushDialogue(sessionId, speakerId, speech, "zm_098", false);
        }

        // 4. 本地 TTS
        try {
            if (speech != null && !speech.isBlank()) {
                kokoroTtsClient.speak(sessionId, speech);
            }
        } catch (Exception e) {
            log.warn("TTS 失败: {}", e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void sendSpeech(String sessionId, String text) {
        String npcId = speakerResolver.resolveByText(sessionId, text);   // ★ 兜底
        npcBridge.pushDialogue(sessionId, npcId, text, "zm_098", false);
        if (npcId == null) {
            log.warn("⚠️ sendSpeech 无法定位说话人: ems={}, text='{}'", sessionId, text);
        }
    }

    /** ★ 可选新签名：调用方已经知道说话人时，直接传 id。 */
    public void sendSpeech(String sessionId, String npcId, String text) {
        npcBridge.pushDialogue(sessionId, npcId, text, "zm_098", false);
    }

    @Override
    public void sendAnimation(String sessionId, String animation, int durationSeconds) {
        String npcId = speakerResolver.resolveByAction(sessionId, animation, Map.of());  // ★ 兜底
        npcBridge.pushAction(sessionId, npcId, animation, Map.of(), durationSeconds);
    }

    /** ★ 可选新签名 */
    public void sendAnimation(String sessionId, String npcId,
                              String animation, int durationSeconds) {
        npcBridge.pushAction(sessionId, npcId, animation, Map.of(), durationSeconds);
    }

    @Override
    public boolean isClientConnected(String sessionId) { return true; }

    private String resolveNpcId(Map<String, Object> params) {
        if (params == null) return null;
        Object id = params.get("npcId");
        return id == null ? null : id.toString();
    }
}

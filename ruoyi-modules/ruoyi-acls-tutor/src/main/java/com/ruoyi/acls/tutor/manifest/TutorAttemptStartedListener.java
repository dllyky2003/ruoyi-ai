package com.ruoyi.acls.tutor.manifest;

import com.ruoyi.ems.room.event.AttemptStartedEvent;
import com.ruoyi.ems.room.protocol.ProtocolBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link AttemptStartedEvent}，构建并下发 ATTEMPT_MANIFEST。
 *
 * <p>之所以在 tutor 侧监听：{@code AttemptManifest} 与 {@link ManifestService}
 * 都定义在 acls-tutor 模块，ems 侧不引用它们的类型。</p>
 *
 * <p>Spring Event 默认同步，因此 MANIFEST 的发送顺序紧随
 * EngineSessionControlHandler 的 pushSnapshot(full=true) 之后，
 * 与 SNAPSHOT 序列上相邻。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TutorAttemptStartedListener {

    private final ManifestService manifestService;
    private final ProtocolBridgeService protocolBridge;

    @EventListener
    public void onAttemptStarted(AttemptStartedEvent evt) {
        String emsSessionId = evt.getEmsSessionId();
        try {
            AttemptManifest manifest = manifestService.buildAndFreeze(
                emsSessionId, evt.getAttemptId(), evt.getCaseId());
            protocolBridge.pushManifest(emsSessionId, manifest);
        } catch (Exception e) {
            log.error("❌ MANIFEST 构建或下发失败: ems={}, err={}",
                emsSessionId, e.getMessage(), e);
        }
    }
}

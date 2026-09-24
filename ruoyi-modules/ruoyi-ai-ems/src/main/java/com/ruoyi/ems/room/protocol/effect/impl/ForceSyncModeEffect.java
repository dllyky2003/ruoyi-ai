package com.ruoyi.ems.room.protocol.effect.impl;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.room.protocol.effect.ControlledEffect;
import com.ruoyi.ems.room.protocol.effect.EffectContext;
import com.ruoyi.ems.room.protocol.effect.EffectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 契约 §7.2 / §10 —— {@code IO_FORCE_SYNC_MODE} 的受控效果实现。
 *
 * <p><b>当前状态（OVR-2b 占位）：</b></p>
 * <ul>
 *   <li>不写任何变量（{@code case.forceSyncMode} 未在 _variables.json 声明）</li>
 *   <li>不递增 revision</li>
 *   <li>直接返回 {@code accepted=true}，让 OVR-1 校验链路可端到端验证</li>
 * </ul>
 *
 * <p><b>TODO OVR-2b 真实实现：</b>等契约 §10 明确同步模式的落点后，按以下之一接入：</p>
 * <ul>
 *   <li>写已声明的受控变量 → {@link EmsContextManager#batchSetVarsWithVersion}</li>
 *   <li>写 PhaseRuntime 字段 → {@link EmsContextManager#updatePhaseRuntime}</li>
 *   <li>加语义化方法（与 {@code setClockRunning} 同类）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForceSyncModeEffect implements ControlledEffect {

    private final EmsContextManager contextManager;

    @Override
    public String getEffectId() {
        return "force_sync_mode";
    }

    @Override
    public EffectResult apply(EffectContext ctx) {
        String ems = ctx.getEmsSessionId();

        CaseContext snap = contextManager.getSnapshot(ems);
        if (snap == null) {
            return EffectResult.builder()
                .accepted(false)
                .message("会话不存在: " + ems)
                .build();
        }
        long rev = snap.getVersion();

        // ⚠️ TODO OVR-2b：契约 §10 同步模式字段确认后接入真实状态变更
        log.warn("⚠️ [OVR-2b] force_sync_mode 占位返回，状态变更待接入: ems={}, room={}",
            ems, ctx.getRoomSessionId());

        return EffectResult.builder()
            .accepted(true)
            .message("force_sync_mode 占位返回（状态变更待接入）")
            .stateRevision(rev)
            .build();
    }
}

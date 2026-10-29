package com.ruoyi.ems.room.protocol.effect.impl;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.room.protocol.effect.ControlledEffect;
import com.ruoyi.ems.room.protocol.effect.EffectContext;
import com.ruoyi.ems.room.protocol.effect.EffectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 契约 §7.2 / §10 —— {@code IO_FORCE_SYNC_MODE} 的受控效果实现。
 *
 * <p><b>OVR-2b 真实生效（2026-09-25）</b>：</p>
 *
 * <p>写 {@code system.deterministicDemo = true}，通过 {@code batchSetVarsWithVersion}
 * 走乐观锁路径，会 bump stateRevision。</p>
 *
 * <p><b>为什么选 {@code system.deterministicDemo}：</b></p>
 * <ul>
 *   <li>契约 §7.3 阶段 5 覆盖清单中，{@code IO_FORCE_SYNC_MODE} 的语义是
 *       "强制同步模式"；{@code deterministicDemoUsed} 是 {@code SystemStatus}
 *       已有字段，语义最贴近</li>
 *   <li>{@link com.ruoyi.ems.room.protocol.AttemptSnapshotService#extractSystemStatus}
 *       已有读取方：{@code getBool(vars, "system.deterministicDemo")}</li>
 *   <li>与 {@code system.assessmentInterruptionActive} 分离，避免与阶段 1.3
 *      评分中断耦合</li>
 * </ul>
 *
 * <p>{@code system.deterministicDemo} 已在 {@code AclsInternalVars.NAMES} 白名单内，
 * 不广播给 Unity 独立消息，但会随 SNAPSHOT 的 {@code systemStatus} 下发。</p>
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

        boolean ok = contextManager.batchSetVarsWithVersion(
            ems,
            Map.of("system.deterministicDemo", true),
            "OVR_SYNC_MODE",
            snap.getVersion()
        );

        if (!ok) {
            log.warn("🔒 [OVR-2b] force_sync_mode 写入失败（乐观锁冲突）: ems={}, room={}",
                ems, ctx.getRoomSessionId());
            return EffectResult.builder()
                .accepted(false)
                .message("写入 system.deterministicDemo 失败（版本冲突）")
                .build();
        }

        CaseContext latest = contextManager.getSnapshot(ems);
        long newRev = latest != null ? latest.getVersion() : -1L;

        log.info("🔒 [OVR-2b] force_sync_mode 生效: ems={}, room={}, newRev={}",
            ems, ctx.getRoomSessionId(), newRev);

        return EffectResult.builder()
            .accepted(true)
            .message("同步模式已强制开启")
            .stateRevision(newRev)
            .build();
    }
}

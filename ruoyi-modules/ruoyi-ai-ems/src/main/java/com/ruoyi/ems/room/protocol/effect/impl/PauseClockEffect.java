package com.ruoyi.ems.room.protocol.effect.impl;

import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.room.protocol.effect.ControlledEffect;
import com.ruoyi.ems.room.protocol.effect.EffectContext;
import com.ruoyi.ems.room.protocol.effect.EffectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 契约 §7.2 / §10 —— {@code IO_PAUSE_CLOCK} 的受控效果实现。
 *
 * <p><b>OVR-2b 真实生效（2026-09-25）</b>：</p>
 * <ul>
 *   <li>走 {@link ClockService#pause(String)}，而非直接 {@code setClockRunning}</li>
 *   <li>理由：{@code ClockService.pause} 会同时触发三个旁路：
 *     <ul>
 *       <li>{@code pausedAtMap} 写入 → {@code isPaused()} 一致</li>
 *       <li>{@code ClockPausedEvent} 发布 → {@code TeamManager} 等监听者</li>
 *       <li>{@code ClockEvent(SESSION/PAUSE)} 发布 → 螺旋 4 契约</li>
 *     </ul>
 *   </li>
 *   <li>若绕过 {@code ClockService} 只调 {@code setClockRunning}，时钟停是停住了，
 *       但旁路事件链断裂，下游监听者收不到</li>
 * </ul>
 *
 * <p>{@code ClockService.pause} 不 bump stateRevision（clockStatus 属系统内部状态），
 * 返回的 revision 为暂停前后的同一值。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PauseClockEffect implements ControlledEffect {

    private final EmsContextManager contextManager;
    private final ClockService clockService;

    @Override
    public String getEffectId() {
        return "pause_clock";
    }

    @Override
    public EffectResult apply(EffectContext ctx) {
        String ems = ctx.getEmsSessionId();

        try {
            clockService.pause(ems);
        } catch (Exception e) {
            log.error("⏸️ [OVR-2b] pause_clock 调用 ClockService.pause 失败: ems={}, room={}",
                ems, ctx.getRoomSessionId(), e);
            return EffectResult.builder()
                .accepted(false)
                .message("时钟暂停失败: " + e.getMessage())
                .build();
        }

        CaseContext snap = contextManager.getSnapshot(ems);
        long rev = snap != null ? snap.getVersion() : -1L;

        log.info("⏸️ [OVR-2b] pause_clock 生效: ems={}, room={}, newRev={}, isPaused={}",
            ems, ctx.getRoomSessionId(), rev, clockService.isPaused(ems));

        return EffectResult.builder()
            .accepted(true)
            .message("时钟已暂停")
            .stateRevision(rev)
            .build();
    }
}

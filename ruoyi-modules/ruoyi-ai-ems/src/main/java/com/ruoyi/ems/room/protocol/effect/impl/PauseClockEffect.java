package com.ruoyi.ems.room.protocol.effect.impl;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.room.protocol.effect.ControlledEffect;
import com.ruoyi.ems.room.protocol.effect.EffectContext;
import com.ruoyi.ems.room.protocol.effect.EffectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PauseClockEffect implements ControlledEffect {

    private final EmsContextManager contextManager;

    @Override
    public String getEffectId() {
        return "pause_clock";
    }

    @Override
    public EffectResult apply(EffectContext ctx) {
        String ems = ctx.getEmsSessionId();

        boolean ok = contextManager.setClockRunning(ems, false);
        if (!ok) {
            return EffectResult.builder()
                .accepted(false)
                .message("setClockRunning 失败，会话不存在: " + ems)
                .build();
        }

        CaseContext snap = contextManager.getSnapshot(ems);
        long rev = snap != null ? snap.getVersion() : -1L;

        log.info("⏸️ [OVR-2b] pause_clock 生效: ems={}, room={}, newRev={}",
            ems, ctx.getRoomSessionId(), rev);

        return EffectResult.builder()
            .accepted(true)
            .message("时钟已暂停")
            .stateRevision(rev)
            .build();
    }
}

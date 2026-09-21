package com.ruoyi.acls.tutor.integration;

import com.ruoyi.ems.spi.StateTransitionHook;
import com.ruoyi.ems.spi.dto.TransitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 状态转移前后钩子 SPI 的 Tutor 实现（最小版本）。
 *
 * <p>本步只记录 INFO 日志，不做任何决策或副作用。后续步骤会在这里
 * 写入审计证据、更新评估状态。</p>
 */
@Slf4j
@Component
@Primary
public class TutorStateTransitionHook implements StateTransitionHook {

    @Override
    public void beforeTransition(TransitionContext context) {
        if (context == null) return;
        log.info("[Tutor][BeforeTransition] session={}, {} -> {}, intent={}",
            context.getSessionId(),
            context.getFromState(),
            context.getToState(),
            context.getIntentCode());
    }

    @Override
    public void afterTransition(TransitionContext context) {
        if (context == null) return;
        log.info("[Tutor][AfterTransition] session={}, {} -> {}, intent={}",
            context.getSessionId(),
            context.getFromState(),
            context.getToState(),
            context.getIntentCode());
    }
}

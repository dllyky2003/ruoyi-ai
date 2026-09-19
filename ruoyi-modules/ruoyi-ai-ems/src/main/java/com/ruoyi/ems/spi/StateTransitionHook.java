package com.ruoyi.ems.spi;


import com.ruoyi.ems.spi.dto.TransitionContext;

/**
 * 状态转移前后钩子 SPI。
 *
 * <p>由 tutor 实现以记录审计证据、更新评估状态。</p>
 */
public interface StateTransitionHook {

    void beforeTransition(TransitionContext context);

    void afterTransition(TransitionContext context);
}

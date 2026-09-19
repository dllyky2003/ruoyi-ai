package com.ruoyi.ems.spi;

import com.ruoyi.ems.spi.dto.TransitionContext;

public class NoOpStateTransitionHook implements StateTransitionHook {

    @Override
    public void beforeTransition(TransitionContext context) { }

    @Override
    public void afterTransition(TransitionContext context) { }
}

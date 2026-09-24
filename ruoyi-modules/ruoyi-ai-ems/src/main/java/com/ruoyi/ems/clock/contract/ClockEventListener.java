package com.ruoyi.ems.clock.contract;

@FunctionalInterface
public interface ClockEventListener {
    void onClockEvent(ClockEvent event);
}

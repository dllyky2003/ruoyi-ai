package com.ruoyi.ems.spi;

import com.ruoyi.ems.spi.dto.ClinicalEvent;

public class NoOpTemporalEventSink implements TemporalEventSink {

    @Override
    public void onStateEntered(String sessionId, String stateId, long stateRevision) { }

    @Override
    public void onStateExited(String sessionId, String stateId, long stateRevision) { }

    @Override
    public void onEventEmitted(ClinicalEvent event) { }
}

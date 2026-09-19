package com.ruoyi.ems.spi;


import com.ruoyi.ems.spi.dto.ClinicalEvent;

/**
 * 时间线事件接收 SPI。
 *
 * <p>状态机进入/退出状态、发出事件时回调，由 tutor 实现以注册/取消时间线。</p>
 */
public interface TemporalEventSink {

    void onStateEntered(String sessionId, String stateId, long stateRevision);

    void onStateExited(String sessionId, String stateId, long stateRevision);

    void onEventEmitted(ClinicalEvent event);
}

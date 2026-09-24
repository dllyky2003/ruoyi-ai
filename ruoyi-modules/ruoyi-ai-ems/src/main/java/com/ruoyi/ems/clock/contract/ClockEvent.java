package com.ruoyi.ems.clock.contract;

import java.util.Map;

/**
 * 统一时钟事件。5 类时钟共用同一载体，通过 {@link ClockType} 区分。
 */
public record ClockEvent(
    ClockType type,
    String sessionId,
    String clockId,
    ClockEventType eventType,
    long atMillis,
    double elapsedSeconds,
    Map<String, Object> payload
) {
    public ClockEvent {
        if (payload == null) payload = Map.of();
    }
}

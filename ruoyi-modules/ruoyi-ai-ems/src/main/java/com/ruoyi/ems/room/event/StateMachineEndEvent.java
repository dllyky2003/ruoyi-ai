package com.ruoyi.ems.room.event;

import java.time.Instant;

/**
 * 状态机结束事件。
 * <p>
 * 由状态机执行层在进入 endState 时发布。
 * </p>
 */
public record StateMachineEndEvent(
    String emsSessionId,
    String finalState,
    String reason,
    Instant occurredAt
) {

    public StateMachineEndEvent(String emsSessionId, String finalState, String reason) {
        this(emsSessionId, finalState, reason, Instant.now());
    }

    public StateMachineEndEvent {
        if (emsSessionId == null || emsSessionId.isBlank()) {
            throw new IllegalArgumentException("StateMachineEndEvent.emsSessionId 不能为空");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (reason == null) {
            reason = "completed";
        }
    }
}

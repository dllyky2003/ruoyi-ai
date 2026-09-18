package com.ruoyi.ems.room.event;


/**
 * 时钟暂停事件。
 * <p>由 ClockService.pause() 发布。订阅者按需处理暂停期间的冻结逻辑。</p>
 */
public record ClockPausedEvent(
    String sessionId,
    long pausedAt
) {}

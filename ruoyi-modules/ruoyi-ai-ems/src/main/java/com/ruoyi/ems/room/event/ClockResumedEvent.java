package com.ruoyi.ems.room.event;



/**
 * 时钟恢复事件。
 * <p>
 * 由 ClockService.resume() 发布，携带本次暂停时长。
 * TeamManager 据此把正在执行任务的 NPC 的 taskStartTime 往后推，
 * 避免 tickProgress 因墙上时间流逝而突然跳过进度。
 * </p>
 */
public record ClockResumedEvent(
    String sessionId,
    long resumedAt,
    long pauseDurationMs
) {}

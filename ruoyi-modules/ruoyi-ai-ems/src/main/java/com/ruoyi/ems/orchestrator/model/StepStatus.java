package com.ruoyi.ems.orchestrator.model;

/**
 * 动作链步骤状态
 */
public enum StepStatus {
    /** 等待执行 */
    PENDING,
    /** 正在执行（如NPC正在播放动画） */
    IN_PROGRESS,
    /** 已完成 */
    DONE,
    /** 执行失败 */
    FAILED,
    /** 已被中止（取消） */
    ABORTED
}

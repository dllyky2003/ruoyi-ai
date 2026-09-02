package com.ruoyi.ems.npc;

import lombok.Getter;

public enum Status {
    /** 空闲中，可接受新任务 */
    IDLE("空闲"),
    /** 正在执行任务 */
    EXECUTING("执行中"),
    /** 任务已完成 */
    COMPLETED("已完成"),
    /** 任务执行失败 */
    FAILED("失败"),
    /** 休息/暂不可用 */
    RESTING("休息中");

    @Getter
    private final String displayName;

    Status(String displayName) {
        this.displayName = displayName;
    }
}

package com.ruoyi.ems.npc;

import lombok.Getter;
@Getter
public enum Status {

    IDLE("空闲待命"),
    EXECUTING("执行中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    CANCELLED("已取消");

    private final String displayName;

    Status(String displayName) {
        this.displayName = displayName;
    }
}

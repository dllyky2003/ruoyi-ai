package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

import java.util.List;

@Data
public class ContinuousTaskConfig {
    private Boolean enabled;
    private Integer cycleDurationSeconds;       // 每轮持续秒数，如 CPR 的 120 秒
    private Integer loopCount;                  // 循环次数，null 表示无限
    private List<String> interruptOn;           // 可中断该任务的意图列表
    private Integer autoReportIntervalSeconds;  // 自动上报间隔
    private FatigueRotationConfig fatigueRotation;
}

package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

import java.util.Map;

@Data
public class TaskConfig {
    private String refAtomic;          // 原子动作ID，如 "IV_PUSH"
    private String actor;              // 对应 Duty 枚举名，如 "IV_MED"
    private Map<String, Object> params;       // 静态参数
    private Map<String, String> spelParams;   // SpEL 动态参数，如 {"dose": "#utils.getAdenosineDose(context)"}
}

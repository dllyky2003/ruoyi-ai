package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

@Data
public class PostConditionConfig {
    private String condition;                   // "time_elapsed_seconds" 或 "effect_result"
    private Object value;                       // 固定值，如 120
    private String spelCondition;               // SpEL 条件，如 "context.systolic_bp < 90"
    private String triggerIntent;               // 触发的意图编码，如 "G4-5"
    private String priority;                    // "auto" 或 "suggestive"
}

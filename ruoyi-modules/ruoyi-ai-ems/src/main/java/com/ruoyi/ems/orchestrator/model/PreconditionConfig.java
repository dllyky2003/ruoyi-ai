package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PreconditionConfig {
    private List<String> requiredIntents;       // 必须已完成的意图列表
    private Map<String, Object> requiredState;  // 必须满足的状态键值对
    private String spelGuard;                   // SpEL 复杂条件（优先级最高）
    private Integer timeoutSeconds;
    private String onTimeoutAction;             // "abort_with_alert" 等
}

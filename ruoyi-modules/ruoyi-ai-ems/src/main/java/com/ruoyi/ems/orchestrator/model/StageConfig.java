package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

import java.util.List;

@Data
public class StageConfig {
    private String id;
    private String type; // "sequential" , "parallel" , "atomic"
    private Integer timeoutSeconds;
    private Boolean requiresAck; // 是否需要组长复述确认
    private List<TaskConfig> tasks; // type=sequential/parallel 时使用
    private TaskConfig task;       // type=atomic 时使用
}

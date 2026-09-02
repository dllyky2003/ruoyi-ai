package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

@Data
public class ErrorRecoveryConfig {
    private String fallbackIntent;
    private Integer autoRetryCount;
    private String reassignTo;
    private String abortAndTrigger;
}

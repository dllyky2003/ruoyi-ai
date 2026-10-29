package com.ruoyi.ems.service;

import com.ruoyi.ems.spi.dto.ValidationResult;

import java.util.List;
import java.util.Map;

public class ExecutionOutcome {
    private boolean success;
    private String responseText;       // 学员反馈
    private String errorMessage;
    private ValidationResult validation;
    private String failureKind;        // AttemptOutcome.FailureKind.name()
    private Map<String, Object> contextUpdates;
    private List<String> animations;
}

package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 临床规则校验结果。
 */
@Data
public class ValidationResult {

    private ExecutionStrategy decision = ExecutionStrategy.ALLOW;
    private String primaryRuleId;
    private List<String> allMatchedRuleIds = new ArrayList<>();
    private String severity = "none";
    private List<String> feedbackKeys = new ArrayList<>();
    private List<String> assessmentTags = new ArrayList<>();

    /** tutor 未启用时的默认结果：放行、不评分、无副作用。 */
    public static ValidationResult allowWithoutTutor() {
        ValidationResult r = new ValidationResult();
        r.setDecision(ExecutionStrategy.ALLOW);
        r.setSeverity("none");
        return r;
    }
}

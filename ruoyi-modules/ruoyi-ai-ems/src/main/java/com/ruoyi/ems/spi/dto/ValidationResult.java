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
    private String feedbackText;

    // ★ V3.1 新增：已解析授权效果（resolved=true 的可直接执行）
    private List<EffectSpec> allowedEffects = new ArrayList<>();

    // ★ V3.1 新增：被阻断的效果 ID（供审计与教案层候选过滤使用）
    private List<String> blockedEffects = new ArrayList<>();

    /** tutor 未启用时的默认结果：放行、不评分、无副作用。 */
    public static ValidationResult allowWithoutTutor() {
        ValidationResult r = new ValidationResult();
        r.setDecision(ExecutionStrategy.ALLOW);
        r.setSeverity("none");
        return r;
    }
}

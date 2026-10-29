package com.ruoyi.ems.engine;

import com.ruoyi.ems.spi.dto.ValidationResult;

public class TutorBlockedException extends RuntimeException {

    private final String ruleId;
    private final String severity;
    private final String feedbackMessage;
    /** ★ 批 1 新增：携带完整 ValidationResult，供 CMD_RESULT 组装 */
    private final ValidationResult validation;

    /** ★ 批 1 新增：推荐构造（带完整 vr） */
    public TutorBlockedException(ValidationResult vr, String feedbackMessage) {
        super("tutor blocked: ruleId="
            + (vr != null ? vr.getPrimaryRuleId() : null)
            + ", severity="
            + (vr != null ? vr.getSeverity() : null));
        this.validation = vr;
        this.ruleId = vr != null ? vr.getPrimaryRuleId() : null;
        this.severity = vr != null ? vr.getSeverity() : null;
        this.feedbackMessage = feedbackMessage;
    }

    /** 老构造保留（向后兼容） */
    public TutorBlockedException(String ruleId, String severity, String feedbackMessage) {
        super("tutor blocked: ruleId=" + ruleId + ", severity=" + severity);
        this.ruleId = ruleId;
        this.severity = severity;
        this.feedbackMessage = feedbackMessage;
        this.validation = null;
    }

    public String getRuleId() { return ruleId; }
    public String getSeverity() { return severity; }
    public String getFeedbackMessage() { return feedbackMessage; }
    public ValidationResult getValidation() { return validation; }
}

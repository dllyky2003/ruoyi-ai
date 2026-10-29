package com.ruoyi.ems.engine;

import com.ruoyi.ems.spi.dto.ValidationResult;

/**
 * 状态机尝试结果（结构化）。
 *
 * <p>把 {@link StateMachineFacade#selectAndTransition} 的返回值从 String
 * 升级为携带 tutor {@link ValidationResult} 的对象，让 CMD_RESULT 能填
 * decision / severity / primaryRuleId / allMatchedRuleIds / feedbackKeys /
 * assessmentTags。</p>
 */
public class AttemptOutcome {

    public enum FailureKind {
        NONE,           // 成功
        NO_MATCH,       // 状态机无匹配转移
        TUTOR_BLOCK,    // tutor 明确阻断（BLOCK）
        ARBITER_BLOCK,  // 前置仲裁拦截
        EXCEPTION       // 执行异常
    }

    private final String newState;              // null = 未转移
    private final ValidationResult validation;  // 可 null
    private final FailureKind failureKind;
    private final String failureReason;         // 人类可读

    private AttemptOutcome(String newState, ValidationResult validation,
                           FailureKind failureKind, String failureReason) {
        this.newState = newState;
        this.validation = validation;
        this.failureKind = failureKind;
        this.failureReason = failureReason;
    }

    public static AttemptOutcome success(String newState, ValidationResult vr) {
        return new AttemptOutcome(newState, vr, FailureKind.NONE, null);
    }

    public static AttemptOutcome noMatch(ValidationResult vr) {
        return new AttemptOutcome(null, vr, FailureKind.NO_MATCH, "状态机无匹配转移");
    }

    public static AttemptOutcome tutorBlock(ValidationResult vr, String reason) {
        return new AttemptOutcome(null, vr, FailureKind.TUTOR_BLOCK, reason);
    }

    public static AttemptOutcome arbiterBlock(ValidationResult vr, String reason) {
        return new AttemptOutcome(null, vr, FailureKind.ARBITER_BLOCK, reason);
    }

    public static AttemptOutcome exception(ValidationResult vr, String reason) {
        return new AttemptOutcome(null, vr, FailureKind.EXCEPTION, reason);
    }

    public boolean isSuccess() {
        return failureKind == FailureKind.NONE && newState != null;
    }

    public String getNewState() { return newState; }
    public ValidationResult getValidation() { return validation; }
    public FailureKind getFailureKind() { return failureKind; }
    public String getFailureReason() { return failureReason; }
}

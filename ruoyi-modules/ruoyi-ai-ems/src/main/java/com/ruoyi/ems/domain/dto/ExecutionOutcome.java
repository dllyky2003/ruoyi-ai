package com.ruoyi.ems.domain.dto;

import com.ruoyi.ems.spi.dto.ValidationResult;

import java.util.List;
import java.util.Map;

/**
 * 单条或多条指令执行结果（结构化）。
 *
 * <p>用于替代 {@code SessionService.executeCommandSync} 的 String 返回，
 * 让 CMD_RESULT 能拿到 tutor 决策的完整字段。</p>
 */
public class ExecutionOutcome {

    private boolean success;
    private String responseText;          // 学员反馈文本
    private String errorMessage;
    private ValidationResult validation;  // tutor 决策
    private String failureKind;           // AttemptOutcome.FailureKind.name()
    private Map<String, Object> contextUpdates;
    private List<String> animations;

    public ExecutionOutcome() {}

    // ---------- 工厂 ----------

    public static ExecutionOutcome success(String text, ValidationResult vr) {
        ExecutionOutcome o = new ExecutionOutcome();
        o.success = true;
        o.responseText = text;
        o.validation = vr;
        o.failureKind = "NONE";
        return o;
    }

    public static ExecutionOutcome failure(String kind, String errMsg, ValidationResult vr) {
        ExecutionOutcome o = new ExecutionOutcome();
        o.success = false;
        o.failureKind = kind;
        o.errorMessage = errMsg;
        o.validation = vr;
        return o;
    }

    // ---------- getters / setters ----------

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public ValidationResult getValidation() { return validation; }
    public void setValidation(ValidationResult validation) { this.validation = validation; }

    public String getFailureKind() { return failureKind; }
    public void setFailureKind(String failureKind) { this.failureKind = failureKind; }

    public Map<String, Object> getContextUpdates() { return contextUpdates; }
    public void setContextUpdates(Map<String, Object> contextUpdates) { this.contextUpdates = contextUpdates; }

    public List<String> getAnimations() { return animations; }
    public void setAnimations(List<String> animations) { this.animations = animations; }
}

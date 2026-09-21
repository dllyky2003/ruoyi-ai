package com.ruoyi.ems.engine;

/**
 * tutor 层 BLOCK 时抛出的异常。
 *
 * <p>用于区分 {@link StateMachineFacade#selectAndTransition} 返回 null 的两种语义：
 * <ul>
 *   <li>正常的"无匹配规则" → 返回 null，调用方可以 fallback</li>
 *   <li>被 tutor 明确阻断 → 抛本异常，调用方不应 fallback</li>
 * </ul>
 *
 * <p>调用方（AclsCommandEngine）必须单独捕获本异常，并直接返回失败结果，
 * 不得走"变量驱动重评估"兜底路径。
 */
public class TutorBlockedException extends RuntimeException {

    private final String ruleId;
    private final String severity;
    private final String feedbackMessage;

    public TutorBlockedException(String ruleId, String severity, String feedbackMessage) {
        super("tutor blocked: ruleId=" + ruleId + ", severity=" + severity);
        this.ruleId = ruleId;
        this.severity = severity;
        this.feedbackMessage = feedbackMessage;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getFeedbackMessage() {
        return feedbackMessage;
    }
}

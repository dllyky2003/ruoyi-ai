package com.ruoyi.ems.engine;

import lombok.Getter;

/**
 * 状态转移结果。用于把"是否成功转移 / 未转移原因"从状态机传给调用方。
 *
 * <p>背景：{@code SkillCompletionStateDriver} 在状态机未成功转移时
 * 不应触发技能效果（Effect）。之前的 {@code String} 返回值无法区分
 * "成功转移" / "条件不满足" / "Tutor 拦截" 三种情况，导致 Effect 泄漏。</p>
 */
@Getter
public class StateTransitionResult {

    /** 是否成功转移到新状态 */
    private final boolean success;

    /** 源状态 */
    private final String fromState;

    /** 目标状态（未转移时 = fromState） */
    private final String toState;

    /** 未转移原因（成功时为 null） */
    private final String reason;

    private StateTransitionResult(boolean success, String fromState, String toState, String reason) {
        this.success = success;
        this.fromState = fromState;
        this.toState = toState;
        this.reason = reason;
    }

    /** 成功转移 */
    public static StateTransitionResult success(String fromState, String toState) {
        return new StateTransitionResult(true, fromState, toState, null);
    }

    /** 未转移（条件不满足 / 无匹配规则） */
    public static StateTransitionResult noChange(String currentState, String reason) {
        return new StateTransitionResult(false, currentState, currentState, reason);
    }

    /** Tutor 拦截 */
    public static StateTransitionResult blocked(String currentState, String reason) {
        return new StateTransitionResult(false, currentState, currentState, reason);
    }

    public boolean hasChanged() {
        return success && fromState != null && !fromState.equals(toState);
    }
}

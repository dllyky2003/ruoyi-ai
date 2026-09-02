package com.ruoyi.ems.rule;

import com.ruoyi.ems.domain.CaseContext;

/**
 * 规则评估器接口
 * 当前由 SpEL 实现，将来可替换为 Drools
 */
public interface RuleEvaluator {

    /**
     * 评估 Guard 条件表达式
     * @param expression SpEL 表达式
     * @param context 患者上下文
     * @return 条件是否满足
     */
    boolean evaluateGuard(String expression, CaseContext context);

    /**
     * 评估 Action 表达式（执行副作用）
     * @param expression SpEL 表达式
     * @param context 患者上下文
     */
    void evaluateAction(String expression, CaseContext context);

    /**
     * 评估值表达式（返回泛型结果）
     * @param expression SpEL 表达式
     * @param context 患者上下文
     * @param targetType 返回类型
     * @param <T> 泛型类型
     * @return 计算结果
     */
    <T> T evaluateValue(String expression, CaseContext context, Class<T> targetType);

    /**
     * 评估条件表达式（语义同 evaluateGuard，命名更通用）
     */
    default boolean evaluateCondition(String expression, CaseContext context) {
        return evaluateGuard(expression, context);
    }

    /**
     * 执行动作表达式（语义同 evaluateAction）
     */
    default void executeAction(String expression, CaseContext context) {
        evaluateAction(expression, context);
    }
}

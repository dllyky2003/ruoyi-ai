package com.ruoyi.ems.rule;
import com.ruoyi.ems.domain.CaseContext;

/**
 * 规则评估器门面接口
 *
 * 统一 Guard、Action 和值表达式的评估入口。
 * 具体实现可基于 SpEL、Drools 或其他规则引擎，
 * 通过配置文件切换（acls.rule-engine=spel|drools）。
 *
 * 设计目的：
 * 1. 隔离规则引擎与核心决策层
 * 2. 支持热切换规则引擎实现
 * 3. 便于单元测试和 Mock
 *
 * @author RuoYi EMS
 * @version 1.0
 */
public interface IRuleEvaluator {

    /**
     * 评估 Guard 条件表达式
     *
     * @param expression 条件表达式（SpEL 字符串或 Drools 规则标识）
     * @param context 当前运行时上下文
     * @return 条件是否满足
     */
    boolean evaluateCondition(String expression, CaseContext context);

    /**
     * 执行 Action 表达式
     *
     * @param expression 动作表达式（SpEL 字符串或 Drools 动作）
     * @param context 当前运行时上下文
     */
    void executeAction(String expression, CaseContext context);

    /**
     * 评估值表达式（用于动态取值、计算）
     *
     * @param expression 表达式
     * @param context 当前运行时上下文
     * @param targetType 期望返回的类型
     * @param <T> 泛型
     * @return 表达式的求值结果
     */
    <T> T evaluateValue(String expression, CaseContext context, Class<T> targetType);
}

package com.ruoyi.ems.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 转移配置（构建状态机转移的完整定义）
 *
 * 与 TransitionRule 的区别：
 * - TransitionRule: YAML 原始配置映射（字符串表达式、未解析）
 * - TransitionConfig: 构建时使用的完整定义（包含 Guard/Action 的 Bean 引用）
 *
 * 由 CaseStateMachineDefinitionLoader 从 TransitionRule 构建，
 * 用于 Spring StateMachine Builder 的配置。
 *
 * 本类作为 Transfer Object，不包含业务逻辑。
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitionConfig {

    // ==================== 核心转移信息 ====================

    /**
     * 源状态
     */
    private String source;

    /**
     * 目标状态
     */
    private String target;

    /**
     * 触发事件编码
     */
    private String event;

    /**
     * 关联意图编码（可为 null）
     */
    private String intentCode;

    // ==================== Guard 配置 ====================

    /**
     * Guard 表达式（SpEL 字符串）
     * 当 guardBeanName 为空时使用
     */
    private String guardExpression;

    /**
     * Guard Bean 名称（Spring Bean，实现 Guard 接口）
     * 优先于 guardExpression
     */
    private String guardBeanName;

    /**
     * Guard 实例（直接注入，优先于以上两者）
     * 用于测试或特殊场景
     */
    private transient GuardInstance guardInstance;

    // ==================== Action 配置 ====================

    /**
     * Action 表达式（SpEL 字符串）
     * 当 actionBeanName 为空时使用
     */
    private String actionExpression;

    /**
     * Action Bean 名称（Spring Bean，实现 Action 接口）
     * 优先于 actionExpression
     */
    private String actionBeanName;

    /**
     * Action 实例（直接注入，优先于以上两者）
     * 用于测试或特殊场景
     */
    private transient ActionInstance actionInstance;

    // ==================== 系统动作配置 ====================

    /**
     * 系统动作类型（如 simulate_drug）
     */
    private String systemActionType;

    /**
     * 系统动作参数
     */
    private Map<String, Object> systemActionParams;

    // ==================== 元数据 ====================

    /**
     * 优先级（数值越大优先级越高）
     * 用于解决冲突
     */
    @Builder.Default
    private int priority = 0;

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 描述（用于日志和调试）
     */
    private String description;

    // ==================== 内部接口（便于测试） ====================

    /**
     * Guard 实例接口（用于直接注入）
     */
    @FunctionalInterface
    public interface GuardInstance {
        boolean evaluate(StateContext context);
    }

    /**
     * Action 实例接口（用于直接注入）
     */
    @FunctionalInterface
    public interface ActionInstance {
        void execute(StateContext context);
    }

    /**
     * StateContext 简化接口（避免直接依赖 Spring StateMachine）
     */
    public interface StateContext {
        String getStateMachineId();
        String getSource();
        String getTarget();
        String getEvent();
        Map<String, Object> getVariables();
    }

    // ==================== 便捷判断 ====================

    public boolean hasGuard() {
        return guardExpression != null || guardBeanName != null || guardInstance != null;
    }

    public boolean hasAction() {
        return actionExpression != null || actionBeanName != null || actionInstance != null;
    }

    public boolean hasSystemAction() {
        return systemActionType != null && !systemActionType.isEmpty();
    }

    public boolean isSystemEvent() {
        return intentCode == null || intentCode.isEmpty();
    }

    // ==================== 构建方法 ====================

    public static TransitionConfig fromRule(TransitionRule rule) {
        return TransitionConfig.builder()
            .source(rule.getSource())
            .target(rule.getTarget())
            .event(rule.getEvent())
            .intentCode(rule.getIntentCode())
            .guardExpression(rule.getCondition())
            .actionExpression(rule.getAction())
            .systemActionType(rule.getSystemActionType())
            .systemActionParams(rule.getSystemActionParams())
            .priority(rule.getPriority() != null ? rule.getPriority() : 0)
            .enabled(rule.isEnabled())
            .description(rule.getDescription())
            .build();
    }
}

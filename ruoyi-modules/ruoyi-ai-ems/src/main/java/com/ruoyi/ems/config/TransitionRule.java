package com.ruoyi.ems.config;

import lombok.Data;

import java.util.Map;

/**
 * 状态机转移规则（对应 YAML 中的单个 transition）
 */
@Data
public class TransitionRule {

    /**
     * 源状态编码
     */
    private String source;

    /**
     * 目标状态编码
     */
    private String target;


    /**
     * 关联的意图编码（S1-1, S2-2, G4-1 等）
     * 可为 null（表示系统事件，如超时、错误处理）
     */
    private String intentCode;

    /**
     * 触发转移的系统事件编码
     * 如: "S1_1_NARROW", "S2_2_DONE", "SYS_TIMEOUT"
     */
    private String event;

    /**
     * Guard 条件表达式 (SpEL)
     * 如: "#isUnstable == false", "#adenosine_dose_count == 1"
     */
    private String condition;

    /**
     * Action 表达式 (SpEL) 或 Spring Bean 名称
     * 如: "#adenosine_dose_count = #adenosine_dose_count + 1"
     * 如: "actionLogTransition"
     */
    private String action;

    /**
     * 系统动作类型（用于向模拟器发送指令）
     * 如: "simulate_drug", "simulate_cardioversion"
     */
    private String systemActionType;

    /**
     * 系统动作参数（JSON 格式）
     * 如: {"drug": "adenosine", "dose": 6}
     */
    private Map<String, Object> systemActionParams;

    /**
     * 优先级（数值越大优先级越高）
     * 用于解决同一状态下多个匹配规则的冲突
     */
    private Integer priority = 0;

    /**
     * 规则描述（用于日志和复盘）
     */
    private String description;

    /**
     * 是否启用（软删除）
     */
    private boolean enabled = true;

    // ==================== 新增字段 ====================

    /**
     * 是否为系统定时触发规则（由 ClockService 自动触发）
     * 默认 false，仅当 YAML 中明确设置 systemTrigger: true 时启用
     */
    private boolean systemTrigger = false;

    // ==================== 便捷方法 ====================

    /**
     * 检查是否有 Guard 条件
     */
    public boolean hasCondition() {
        return condition != null && !condition.trim().isEmpty();
    }

    /**
     * 检查是否有 Action
     */
    public boolean hasAction() {
        return action != null && !action.trim().isEmpty();
    }

    /**
     * 检查是否为系统事件（无 intentCode）
     */
    public boolean isSystemEvent() {
        return intentCode == null || intentCode.isEmpty();
    }

    /**
     * 获取显示名称（用于日志）
     */
    public String getDisplayName() {
        return String.format("%s --[%s]--> %s", source, event, target);
    }

    @Override
    public String toString() {
        return getDisplayName() + (description != null ? " (" + description + ")" : "");
    }
}

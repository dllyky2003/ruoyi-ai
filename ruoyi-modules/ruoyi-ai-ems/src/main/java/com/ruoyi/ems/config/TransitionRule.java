package com.ruoyi.ems.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 状态机转移规则（对应 YAML 中的单个 transition）。
 *
 * <p>数据来源：{@code config/acls/**／_flow.yaml} 的 {@code stateMachine.transitions} 节点。
 * 由 {@link com.ruoyi.ems.config.loader.YamlStateMachineLoader} 通过 SnakeYAML 反序列化。
 *
 * <p><b>注意</b>：本类的字段名、类型与 YAML 结构一一对应，不可随意修改。
 * 契约构建所需的额外便捷方法全部标注 {@link JsonIgnore}，不参与序列化。
 */
@Data
public class TransitionRule {

    // ==================== 核心字段（与 YAML 一一对应） ====================

    /**
     * 源状态编码。
     */
    private String source;

    /**
     * 目标状态编码。
     */
    private String target;

    /**
     * 关联的意图编码（如 S-101-1-1）。
     * 可为 null，表示系统事件触发（如超时、错误处理）。
     */
    private String intentCode;

    /**
     * 触发转移的系统事件编码。
     * 如: "S1_1_NARROW"、"S2_2_DONE"、"SYS_TIMEOUT"
     */
    private String event;

    /**
     * Guard 条件表达式（SpEL）。
     * 如: "#isUnstable == false"、"#derived.isNarrowRegularStable == true"
     */
    private String condition;

    /**
     * Action 表达式（SpEL）或 Spring Bean 名称。
     * 如: "#intervention.adenosineDoseCount = #intervention.adenosineDoseCount + 1"
     * 如: "actionLogTransition"
     */
    private String action;

    /**
     * 系统动作类型（用于向模拟器发送指令）。
     * 如: "simulate_drug"、"simulate_cardioversion"
     */
    private String systemActionType;

    /**
     * 系统动作参数（JSON 格式）。
     * 如: {"drug": "adenosine", "dose": 6}
     */
    private Map<String, Object> systemActionParams;

    /**
     * 优先级（数值越大优先级越高）。
     * 用于解决同一状态下多个匹配规则的冲突。
     */
    private Integer priority = 0;

    /**
     * 规则描述（用于日志和复盘）。
     */
    private String description;

    /**
     * 是否启用（软删除）。
     */
    private boolean enabled = true;

    // ==================== 扩展字段 ====================

    /**
     * 是否为系统定时触发规则（由 ClockService 自动触发）。
     * 默认 false，仅当 YAML 中明确设置 systemTrigger: true 时启用。
     */
    private boolean systemTrigger = false;

    /**
     * 是否为偏离指南的"试错路径"。默认 false。
     */
    private boolean deviation = false;
    /**
     * 退出原因。子级退出时声明，引擎自动写入 process.returnToParent。
     */
    private String exitReason;

    /**
     * 迁移副作用。声明式写变量，由引擎在迁移时执行。
     */
    private List<EffectRule> effects;

    @Data
    public static class EffectRule {
        private String var;               // 变量名
        private Object value;             // 字面量值（可选）
        private String valueExpression;   // SpEL 表达式（可选，优先于 value）
    }
    // ====================================================================
    // 便捷方法（原有）
    // ====================================================================

    /**
     * 检查是否有 Guard 条件。
     */
    public boolean hasCondition() {
        return condition != null && !condition.trim().isEmpty();
    }

    /**
     * 检查是否有 Action。
     */
    public boolean hasAction() {
        return action != null && !action.trim().isEmpty();
    }

    /**
     * 检查是否为系统事件（无 intentCode）。
     */
    public boolean isSystemEvent() {
        return intentCode == null || intentCode.isEmpty();
    }

    /**
     * 获取显示名称（用于日志）。
     */
    public String getDisplayName() {
        return String.format("%s --[%s]--> %s", source, event, target);
    }

    @Override
    public String toString() {
        return getDisplayName() + (description != null ? " (" + description + ")" : "");
    }

    // ====================================================================
    // 便捷方法（新增，全部 @JsonIgnore）
    // ====================================================================

    /**
     * 是否有意图编码。
     * 与 {@link #isSystemEvent()} 互补。
     */
    @JsonIgnore
    public boolean hasIntent() {
        return intentCode != null && !intentCode.trim().isEmpty();
    }

    /**
     * 是否有系统事件编码。
     */
    @JsonIgnore
    public boolean hasEvent() {
        return event != null && !event.trim().isEmpty();
    }

    /**
     * 是否有系统动作定义。
     */
    @JsonIgnore
    public boolean hasSystemAction() {
        return systemActionType != null && !systemActionType.trim().isEmpty();
    }

    /**
     * 获取触发类型。
     *
     * <p>返回值：
     * <ul>
     *   <li>{@code "INTENT"}：意图触发（有 intentCode）</li>
     *   <li>{@code "EVENT"}：系统事件触发（有 event）</li>
     *   <li>{@code "TIMER"}：定时触发（systemTrigger=true）</li>
     *   <li>{@code "UNKNOWN"}：无触发路径（配置错误）</li>
     * </ul>
     *
     * <p>优先级：INTENT > EVENT > TIMER。同一转移可能同时具备多种触发路径，
     * 此时返回优先级最高的那个。
     */
    @JsonIgnore
    public String getTriggerType() {
        if (hasIntent()) return "INTENT";
        if (hasEvent()) return "EVENT";
        if (systemTrigger) return "TIMER";
        return "UNKNOWN";
    }

    /**
     * 是否无触发路径（配置错误）。
     */
    @JsonIgnore
    public boolean isUntriggerable() {
        return !hasIntent() && !hasEvent() && !systemTrigger;
    }

    /**
     * 是否为自循环转移（source == target）。
     */
    @JsonIgnore
    public boolean isSelfLoop() {
        return source != null && source.equals(target);
    }

    /**
     * 生成稳定的转移 ID，用于契约索引。
     *
     * <p>格式：{@code T-{source}-{target}-{hash}}，其中 hash 由
     * priority / intentCode / event / condition 组合计算，保证同一 YAML
     * 每次生成的 ID 一致。
     */
    @JsonIgnore
    public String getStableTransitionId() {
        int hash = java.util.Objects.hash(priority, intentCode, event, condition);
        return String.format("T-%s-%s-%03d",
            source != null ? source : "NULL",
            target != null ? target : "NULL",
            Math.abs(hash) % 1000);
    }
}

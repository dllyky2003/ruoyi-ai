package com.ruoyi.acls.contract.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Data
public class TransitionContract {

    // ====================================================================
    // 状态机信息（来自 TransitionRule）
    // ====================================================================

    /**
     * 转移的唯一标识。
     *
     * <p>格式：{@code T-{source}-{target}-{hash}}。
     * 同一个 YAML 每次生成的 ID 稳定一致；不同转移若 source/target 相同，
     * 通过 hash 区分（hash 由 priority / intentCode / event / condition 组合计算）。
     */
    private String transitionId;

    /**
     * 源状态编码。
     */
    private String sourceState;

    /**
     * 目标状态编码。
     */
    private String targetState;

    /**
     * 优先级（数值越大越优先）。
     */
    private int priority;

    /**
     * 转移描述（用于日志和复盘）。
     */
    private String description;


    /**
     * 转移的退出原因（来自 TransitionRule.exitReason）。
     * 运行期会自动写入 process.returnToParent。
     */
    private String exitReason;

    /**
     * 转移的副作用列表（来自 TransitionRule.effects）。
     * 运行期逐个写入 effect.var。
     */
    private List<Effect> effects = new ArrayList<>();

    /**
     * 副作用模型（与 TransitionRule.EffectRule 对齐）。
     */
    @Data
    public static class Effect {
        private String var;
        private Object value;
        private String valueExpression;
    }

    // ====================================================================
    // 触发方式（三种互斥路径，但可以并存）
    // ====================================================================

    /**
     * 关联的意图编码。
     * 为 null 表示该转移不由意图触发（而是由事件或定时器触发）。
     */
    private String intentCode;

    /**
     * 系统事件编码（如 SYS_TIMEOUT、S1_1_NARROW）。
     * 为 null 表示无系统事件触发。
     */
    private String event;

    /**
     * 是否由 ClockService 定时触发。
     */
    private boolean systemTrigger;

    /**
     * 是否启用（对应 TransitionRule.enabled）。
     * 禁用的转移仍会进入契约，但会在诊断中标记。
     */
    private boolean enabled = true;

    /**
     * 是否为偏离指南的"试错路径"。
     */
    private boolean deviation;

    // ====================================================================
    // 表达式（来自 TransitionRule）
    // ====================================================================

    /**
     * 原始 Guard 表达式（来自 {@code TransitionRule.condition}）。
     * 注意：这是 condition 字段，不是 guard 字段。
     */
    private String guardExpression;

    /**
     * 原始 Action 表达式（来自 {@code TransitionRule.action}）。
     * 可能是 SpEL 赋值语句（如 {@code #a = #b + 1}），
     * 也可能是 Spring Bean 名称（如 {@code actionLogTransition}）。
     */
    private String actionExpression;

    // ====================================================================
    // 变量读写
    // ====================================================================

    /**
     * Guard 表达式中引用的变量（可能含 derived.*）。
     */
    private Set<String> readVariables = new LinkedHashSet<>();

    /**
     * readVariables 展开派生依赖后的叶子变量（全部是 phys./process./device. 等）。
     */
    private Set<String> readLeafVariables = new LinkedHashSet<>();

    /**
     * 该转移写入的变量。
     *
     * <p>总是包含 {@code process.currentState}；
     * 若 actionExpression 是赋值语句，还会包含其写入目标。
     */
    private Set<String> writtenVariables = new LinkedHashSet<>();

    // ====================================================================
    // 反查
    // ====================================================================

    /**
     * 能触发这条转移的所有意图编码。
     *
     * <p>来源：
     * <ol>
     *   <li>转移声明的 intentCode（若有）</li>
     *   <li>从 readVariables 反查：任何能写入这些变量的意图都可能触发它</li>
     * </ol>
     */
    private Set<String> triggeringIntents = new LinkedHashSet<>();

    /**
     * 转移关联的技能 ID 集合。
     * 从 intentCode + 源状态，通过 SemanticRoutingLoader 反查得到。
     */
    private Set<String> skills = new LinkedHashSet<>();

    // ====================================================================
    // 系统动作（来自 TransitionRule）
    // ====================================================================

    /**
     * 系统动作类型（如 simulate_drug、simulate_cardioversion）。
     */
    private String systemActionType;

    /**
     * 系统动作参数（如 {"drug": "adenosine", "dose": 6}）。
     */
    private Map<String, Object> systemActionParams;

    // ====================================================================
    // 诊断标记
    // ====================================================================

    /**
     * 是否为死转移（无触发路径）。
     * 判定：无 intentCode、无 event、无 systemTrigger、无 guardExpression。
     */
    private boolean dead;

    /**
     * 是否为自循环（source == target）。
     */
    private boolean selfLoop;

    /**
     * 构建过程中的警告列表。
     */
    private List<String> warnings = new ArrayList<>();

    // ====================================================================
    // 便捷方法
    // ====================================================================

    /**
     * 是否有意图触发。
     */
    @JsonIgnore
    public boolean hasIntent() {
        return intentCode != null && !intentCode.isBlank();
    }

    /**
     * 是否有系统事件触发。
     */
    @JsonIgnore
    public boolean hasEvent() {
        return event != null && !event.isBlank();
    }

    /**
     * 是否有 Guard 条件。
     *
     * <p>有 Guard 条件的纯条件转移（intentCode / event / systemTrigger 均为空）
     * 由运行时"变量驱动重评估"机制触发，不是死转移。
     */
    @JsonIgnore
    public boolean hasGuardCondition() {
        return guardExpression != null && !guardExpression.isBlank();
    }

    /**
     * 是否有系统动作定义。
     */
    @JsonIgnore
    public boolean hasSystemAction() {
        return systemActionType != null && !systemActionType.isBlank();
    }

    /**
     * 是否为系统事件转移（无 intentCode）。
     * 与 {@link #hasIntent()} 互补。
     */
    @JsonIgnore
    public boolean isSystemEvent() {
        return !hasIntent();
    }

    /**
     * 获取触发类型。
     *
     * <p>返回值：
     * <ul>
     *   <li>{@code "INTENT"}：意图触发</li>
     *   <li>{@code "EVENT"}：系统事件触发</li>
     *   <li>{@code "TIMER"}：定时触发</li>
     *   <li>{@code "CONDITION"}：纯条件转移（变量驱动重评估）</li>
     *   <li>{@code "UNKNOWN"}：无触发路径</li>
     * </ul>
     *
     * <p>优先级：INTENT > EVENT > TIMER > CONDITION。
     * 同一转移可能同时具备多种触发路径，此时返回优先级最高的那个。
     */
    @JsonIgnore
    public String getTriggerType() {
        if (hasIntent()) return "INTENT";
        if (hasEvent()) return "EVENT";
        if (systemTrigger) return "TIMER";
        if (hasGuardCondition()) return "CONDITION";
        return "UNKNOWN";
    }

    /**
     * 是否为可触发转移（非死转移）。
     */
    @JsonIgnore
    public boolean isTriggerable() {
        return !dead;
    }

    /**
     * 是否有关联的技能。
     */
    @JsonIgnore
    public boolean hasSkill() {
        return !skills.isEmpty();
    }

    /**
     * 是否写入了除 process.currentState 之外的业务变量。
     */
    @JsonIgnore
    public boolean hasBusinessSideEffects() {
        return writtenVariables.stream()
            .anyMatch(v -> !"process.currentState".equals(v));
    }

    /**
     * 获取写入的业务变量（不含 process.currentState）。
     */
    @JsonIgnore
    public Set<String> getBusinessWrittenVariables() {
        Set<String> result = new LinkedHashSet<>(writtenVariables);
        result.remove("process.currentState");
        return result;
    }

    /**
     * 获取显示名称（用于日志）。
     */
    @JsonIgnore
    public String getDisplayName() {
        String trigger;
        if (hasIntent()) {
            trigger = intentCode;
        } else if (hasEvent()) {
            trigger = event;
        } else if (systemTrigger) {
            trigger = "TIMER";
        } else if (hasGuardCondition()) {
            trigger = "COND";
        } else {
            trigger = "?";
        }
        return String.format("%s --[%s]--> %s",
            sourceState, trigger, targetState);
    }

    /**
     * 生成简短摘要（用于诊断日志）。
     */
    @JsonIgnore
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getDisplayName());
        if (dead) sb.append(" [DEAD]");
        if (selfLoop) sb.append(" [SELF-LOOP]");
        if (deviation) sb.append(" [DEVIATION]");
        if (!enabled) sb.append(" [DISABLED]");
        return sb.toString();
    }

    /**
     * 添加警告（去重）。
     */
    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()
            && !warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    /**
     * 获取只读的系统动作参数。
     */
    @JsonIgnore
    public Map<String, Object> getSystemActionParamsReadonly() {
        return systemActionParams != null
            ? java.util.Collections.unmodifiableMap(systemActionParams)
            : java.util.Collections.emptyMap();
    }

    /**
     * 浅拷贝（用于调试或隔离）。
     */
    public TransitionContract shallowCopy() {
        TransitionContract copy = new TransitionContract();
        copy.setTransitionId(this.transitionId);
        copy.setSourceState(this.sourceState);
        copy.setTargetState(this.targetState);
        copy.setPriority(this.priority);
        copy.setDescription(this.description);
        copy.setIntentCode(this.intentCode);
        copy.setEvent(this.event);
        copy.setSystemTrigger(this.systemTrigger);
        copy.setEnabled(this.enabled);
        copy.setDeviation(this.deviation);
        copy.setGuardExpression(this.guardExpression);
        copy.setActionExpression(this.actionExpression);
        copy.setReadVariables(new LinkedHashSet<>(this.readVariables));
        copy.setReadLeafVariables(new LinkedHashSet<>(this.readLeafVariables));
        copy.setWrittenVariables(new LinkedHashSet<>(this.writtenVariables));
        copy.setTriggeringIntents(new LinkedHashSet<>(this.triggeringIntents));
        copy.setSkills(new LinkedHashSet<>(this.skills));
        copy.setSystemActionType(this.systemActionType);
        copy.setSystemActionParams(this.systemActionParams != null
            ? new LinkedHashMap<>(this.systemActionParams) : null);
        copy.setDead(this.dead);
        copy.setSelfLoop(this.selfLoop);
        copy.setWarnings(new ArrayList<>(this.warnings));
        copy.setExitReason(this.exitReason);
        if (this.effects != null) {
            copy.setEffects(new ArrayList<>(this.effects));
        }
        return copy;
    }

    @Override
    public String toString() {
        return getSummary();
    }
}

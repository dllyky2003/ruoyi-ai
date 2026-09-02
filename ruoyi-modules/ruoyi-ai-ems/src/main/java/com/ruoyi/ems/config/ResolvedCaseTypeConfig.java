package com.ruoyi.ems.config;



import lombok.Data;

import java.util.*;

/**
 * 解析后的完整案例配置（已合并父级）
 *
 * 由 YamlStateMachineLoader 在启动时通过递归合并生成，
 * 包含该案例类型及其所有父级（GENERAL_ACLS）的完整状态和转移规则。
 *
 * 该对象是只读的运行时配置，所有字段在构建完成后不应再修改。
 */
@Data
public class ResolvedCaseTypeConfig {

    // ==================== 基本信息 ====================

    /**
     * 案例类型编码（唯一标识）
     * 如: "tachycardia_stable_narrow"
     */
    private String code;

    /**
     * 案例显示名称
     * 如: "窄QRS稳定心动过速"
     */
    private String caseName;

    /**
     * 配置版本
     */
    private String version;

    /**
     * 父级案例编码（仅用于引用，实际合并后的父级规则已包含在本对象中）
     * 如: "GENERAL_ACLS"
     */
    private String parent;

    // ==================== 状态定义 ====================

    /**
     * 初始状态编码
     * 如: "INITIAL_SETUP"
     */
    private String initialState;

    /**
     * 所有状态编码列表（合并父级 + 子级，去重）
     * 如: ["INITIAL_SETUP", "ASSESS_STABILITY", "CHECK_QRS_WIDTH", ...]
     */
    private List<String> states = new ArrayList<>();

    /**
     * 终止状态编码列表（合并父级 + 子级，去重）
     * 如: ["MONITOR_SINUS_RHYTHM", "WIDE_QRS_ALGORITHM", ...]
     */
    private List<String> endStates = new ArrayList<>();


    private Map<String, String> stateDescriptions;

    public String getStateDescription(String stateCode) {
        if (stateDescriptions != null && stateDescriptions.containsKey(stateCode)) {
            return stateDescriptions.get(stateCode);
        }
        return null;
    }
    // ==================== 转移规则 ====================

    /**
     * 所有转移规则列表（父级规则 + 子级规则，子级追加）
     * 注意：子类可通过在继承链中匹配优先级来覆盖父类规则，
     * 见 RuleMatcher.findMatchingRule() 的实现
     */
    private List<TransitionRule> transitions = new ArrayList<>();

    // ==================== 意图映射（默认跳转） ====================

    /**
     * 意图编码 → 目标状态映射
     * 当 allowUnlistedTransitions = true 时，用于处理未显式定义转移的意图
     * 如: {"S1-1": "CHECK_QRS_WIDTH", "S1-2": "NARROW_QRS_CHECK_RHYTHM"}
     */
    private Map<String, String> intentToStateMapping = new LinkedHashMap<>();

    /**
     * 全局事件意图列表
     * 这些意图不改变主状态，仅记录日志
     * 如: ["G1-1", "G1-2", "G4-1", "G4-2"]
     */
    private Set<String> globalEventIntents = new LinkedHashSet<>();

    // ==================== 默认转移配置 ====================

    /**
     * 是否允许未显式列出的转移
     * 若为 true，意图匹配失败时会尝试使用 intentToStateMapping 进行默认跳转
     */
    private boolean allowUnlistedTransitions = false;

    /**
     * 默认转移时执行的动作（Spring Bean 名称或 SpEL 表达式）
     */
    private String defaultTransitionAction;

    // ==================== 便捷查询方法 ====================

    /**
     * 检查是否包含某个状态
     */
    public boolean hasState(String stateCode) {
        return states != null && states.contains(stateCode);
    }

    /**
     * 检查是否为终止状态
     */
    public boolean isEndState(String stateCode) {
        return endStates != null && endStates.contains(stateCode);
    }

    /**
     * 检查是否为初始状态
     */
    public boolean isInitialState(String stateCode) {
        return initialState != null && initialState.equals(stateCode);
    }

    /**
     * 获取某个状态下所有可用的转移规则
     */
    public List<TransitionRule> getTransitionsBySource(String sourceState) {
        if (transitions == null || sourceState == null) {
            return Collections.emptyList();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 获取某个状态下所有可用的意图编码列表（用于 NLU 约束）
     */
    public Set<String> getAvailableIntents(String sourceState) {
        if (transitions == null || sourceState == null) {
            return Collections.emptySet();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .map(TransitionRule::getIntentCode)
            .filter(Objects::nonNull)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * 根据意图编码查找转移规则（用于 RuleMatcher）
     */
    public Optional<TransitionRule> findTransition(String sourceState, String intentCode) {
        if (transitions == null || sourceState == null) {
            return Optional.empty();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .filter(r -> intentCode.equals(r.getIntentCode()))
            .findFirst();
    }

    /**
     * 根据事件编码查找转移规则
     */
    public Optional<TransitionRule> findTransitionByEvent(String sourceState, String eventCode) {
        if (transitions == null || sourceState == null || eventCode == null) {
            return Optional.empty();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .filter(r -> eventCode.equals(r.getEvent()))
            .findFirst();
    }

    /**
     * 获取所有状态的数量（用于监控）
     */
    public int getStateCount() {
        return states != null ? states.size() : 0;
    }

    /**
     * 获取转移规则数量（用于监控）
     */
    public int getTransitionCount() {
        return transitions != null ? transitions.size() : 0;
    }

    /**
     * 检查是否为全局事件
     */
    public boolean isGlobalEvent(String intentCode) {
        return globalEventIntents != null && globalEventIntents.contains(intentCode);
    }

    /**
     * 获取默认目标状态
     */
    public String getDefaultTargetState(String intentCode) {
        if (intentToStateMapping == null || intentCode == null) {
            return null;
        }
        return intentToStateMapping.get(intentCode);
    }

    /**
     * 创建深拷贝（用于调试或隔离）
     */
    public ResolvedCaseTypeConfig deepCopy() {
        ResolvedCaseTypeConfig copy = new ResolvedCaseTypeConfig();
        copy.setCode(this.code);
        copy.setCaseName(this.caseName);
        copy.setVersion(this.version);
        copy.setParent(this.parent);
        copy.setInitialState(this.initialState);
        copy.setStates(new ArrayList<>(this.states));
        copy.setEndStates(new ArrayList<>(this.endStates));
        copy.setTransitions(new ArrayList<>(this.transitions));
        copy.setIntentToStateMapping(new LinkedHashMap<>(this.intentToStateMapping));
        copy.setGlobalEventIntents(new LinkedHashSet<>(this.globalEventIntents));
        copy.setAllowUnlistedTransitions(this.allowUnlistedTransitions);
        copy.setDefaultTransitionAction(this.defaultTransitionAction);
        return copy;
    }

    // ==================== Builder 模式（简化构建） ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ResolvedCaseTypeConfig config = new ResolvedCaseTypeConfig();

        public Builder code(String code) { config.code = code; return this; }
        public Builder caseName(String caseName) { config.caseName = caseName; return this; }
        public Builder version(String version) { config.version = version; return this; }
        public Builder parent(String parent) { config.parent = parent; return this; }
        public Builder initialState(String initialState) { config.initialState = initialState; return this; }

        public Builder states(List<String> states) {
            config.states = states != null ? new ArrayList<>(states) : new ArrayList<>();
            return this;
        }

        public Builder endStates(List<String> endStates) {
            config.endStates = endStates != null ? new ArrayList<>(endStates) : new ArrayList<>();
            return this;
        }

        public Builder transitions(List<TransitionRule> transitions) {
            config.transitions = transitions != null ? new ArrayList<>(transitions) : new ArrayList<>();
            return this;
        }

        public Builder intentToStateMapping(Map<String, String> mapping) {
            config.intentToStateMapping = mapping != null ? new LinkedHashMap<>(mapping) : new LinkedHashMap<>();
            return this;
        }

        public Builder globalEventIntents(Set<String> intents) {
            config.globalEventIntents = intents != null ? new LinkedHashSet<>(intents) : new LinkedHashSet<>();
            return this;
        }

        public Builder allowUnlistedTransitions(boolean allow) {
            config.allowUnlistedTransitions = allow;
            return this;
        }

        public Builder defaultTransitionAction(String action) {
            config.defaultTransitionAction = action;
            return this;
        }

        public ResolvedCaseTypeConfig build() {
            // 基本校验
            if (config.code == null || config.code.isEmpty()) {
                throw new IllegalArgumentException("案例编码不能为空");
            }
            if (config.initialState == null || config.initialState.isEmpty()) {
                throw new IllegalArgumentException("初始状态不能为空");
            }
            return config;
        }
    }
}

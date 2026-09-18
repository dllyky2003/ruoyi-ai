package com.ruoyi.ems.config;



import com.ruoyi.ems.domain.PhaseDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 状态机定义（对应 YAML 中的 stateMachine 节点）
 *
 * 注意：本类手动编写 getter/setter 以确保 SnakeYAML 反序列化正常工作
 */
public class StateMachineDefinition {

    private String initial;
    private boolean allowUnlistedTransitions;
    private String defaultTransitionAction;
    private Map<String, String> intentToStateMapping;
    private Set<String> globalEventIntents;

    // ★ 新增：本案例独有的“始终允许”意图（用于构建 LLM 提示词白名单）
    private Set<String> alwaysAllowedIntents;

    private List<String> states;
    private List<String> endStates;
    private List<TransitionRule> transitions;
    private List<String> overviewExcludeStates = new ArrayList<>();


    // ==================== ★ Phase SM 新增字段 ====================
    public List<String> getOverviewExcludeStates() { return overviewExcludeStates; }
    public void setOverviewExcludeStates(List<String> v) { this.overviewExcludeStates = v; }
    /**
     * 初始主阶段编码（可选）。
     * 为空时，由 ResolvedCaseTypeConfig.Builder 按 priority 最高者自动推导。
     */
    private String initialPhase;

    /**
     * 阶段定义列表（YAML 中的 stateMachine.phases）。
     * 子级通过相同的 id 覆盖父级的同 id 定义。
     */
    private List<PhaseDefinition> phases;

    // ==================== Getter ====================

    public String getInitial() {
        return initial;
    }

    public boolean isAllowUnlistedTransitions() {
        return allowUnlistedTransitions;
    }

    public String getDefaultTransitionAction() {
        return defaultTransitionAction;
    }

    public Map<String, String> getIntentToStateMapping() {
        return intentToStateMapping;
    }

    public Set<String> getGlobalEventIntents() {
        return globalEventIntents;
    }

    public Set<String> getAlwaysAllowedIntents() {
        return alwaysAllowedIntents;
    }

    public List<String> getStates() {
        return states;
    }

    public List<String> getEndStates() {
        return endStates;
    }

    public List<TransitionRule> getTransitions() {
        return transitions;
    }

    // ★ Phase SM 新增 getter
    public String getInitialPhase() {
        return initialPhase;
    }

    public List<PhaseDefinition> getPhases() {
        return phases;
    }

    // ==================== Setter（SnakeYAML 需要） ====================

    public void setInitial(String initial) {
        this.initial = initial;
    }

    public void setAllowUnlistedTransitions(boolean allowUnlistedTransitions) {
        this.allowUnlistedTransitions = allowUnlistedTransitions;
    }

    public void setDefaultTransitionAction(String defaultTransitionAction) {
        this.defaultTransitionAction = defaultTransitionAction;
    }

    public void setIntentToStateMapping(Map<String, String> intentToStateMapping) {
        this.intentToStateMapping = intentToStateMapping;
    }

    public void setGlobalEventIntents(Set<String> globalEventIntents) {
        this.globalEventIntents = globalEventIntents;
    }

    public void setAlwaysAllowedIntents(Set<String> alwaysAllowedIntents) {
        this.alwaysAllowedIntents = alwaysAllowedIntents;
    }

    public void setStates(List<String> states) {
        this.states = states;
    }

    public void setEndStates(List<String> endStates) {
        this.endStates = endStates;
    }

    public void setTransitions(List<TransitionRule> transitions) {
        this.transitions = transitions;
    }

    // ★ Phase SM 新增 setter
    public void setInitialPhase(String initialPhase) {
        this.initialPhase = initialPhase;
    }

    public void setPhases(List<PhaseDefinition> phases) {
        this.phases = phases;
    }
}

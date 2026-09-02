package com.ruoyi.ems.config;

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
    private boolean allowUnlistedTransitions;          // 改为 boolean
    private String defaultTransitionAction;
    private Map<String, String> intentToStateMapping;
    private Set<String> globalEventIntents;
    private List<String> states;
    private List<String> endStates;
    private List<TransitionRule> transitions;

    // ==================== Getter ====================

    public String getInitial() {
        return initial;
    }

    public boolean isAllowUnlistedTransitions() {       // 对于 boolean 类型 getter 为 isXxx
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

    public List<String> getStates() {
        return states;
    }

    public List<String> getEndStates() {
        return endStates;
    }

    public List<TransitionRule> getTransitions() {
        return transitions;
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

    public void setStates(List<String> states) {
        this.states = states;
    }

    public void setEndStates(List<String> endStates) {
        this.endStates = endStates;
    }

    public void setTransitions(List<TransitionRule> transitions) {
        this.transitions = transitions;
    }
}

package com.ruoyi.acls.contract;



import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import lombok.Data;

import java.util.*;

@Data
public class StateMachineSnapshot {

    private String caseId;
    private String caseName;
    private String version;
    private String parent;
    private String initialState;
    private List<String> states = new ArrayList<>();
    private List<String> endStates = new ArrayList<>();
    private Map<String, String> stateDescriptions = new LinkedHashMap<>();
    private List<TransitionSnapshot> transitions = new ArrayList<>();
    private Map<String, String> intentToStateMapping = new LinkedHashMap<>();
    private Set<String> globalEventIntents = new LinkedHashSet<>();
    private Set<String> alwaysAllowedIntents = new LinkedHashSet<>();
    private List<String> resetVarsOnArrival = new ArrayList<>();
    private Map<String, List<String>> onEnterRules = new LinkedHashMap<>();
    private boolean allowUnlistedTransitions;
    private String defaultTransitionAction;

    // ★ 关键：合并后的状态集合，父级 + 子级
    private String inheritanceChain;

    @Data
    public static class TransitionSnapshot {
        private String source;
        private String target;
        private String intentCode;
        private String event;
        private String condition;
        private Integer priority;
        private String exitReason;
        private List<String> effects = new ArrayList<>();
        private String description;
    }

    public static StateMachineSnapshot from(ResolvedCaseTypeConfig config,
                                            String inheritanceChain) {
        StateMachineSnapshot s = new StateMachineSnapshot();
        s.setCaseId(config.getCode());
        s.setCaseName(config.getCaseName());
        s.setVersion(config.getVersion());
        s.setParent(config.getParent());
        s.setInitialState(config.getInitialState());
        s.setStates(config.getStates() != null
            ? new ArrayList<>(config.getStates()) : new ArrayList<>());
        s.setEndStates(config.getEndStates() != null
            ? new ArrayList<>(config.getEndStates()) : new ArrayList<>());
        s.setStateDescriptions(config.getStateDescriptions() != null
            ? new LinkedHashMap<>(config.getStateDescriptions()) : new LinkedHashMap<>());
        s.setIntentToStateMapping(config.getIntentToStateMapping() != null
            ? new LinkedHashMap<>(config.getIntentToStateMapping()) : new LinkedHashMap<>());
        s.setGlobalEventIntents(config.getGlobalEventIntents() != null
            ? new LinkedHashSet<>(config.getGlobalEventIntents()) : new LinkedHashSet<>());
        s.setAlwaysAllowedIntents(config.getAlwaysAllowedIntents() != null
            ? new LinkedHashSet<>(config.getAlwaysAllowedIntents()) : new LinkedHashSet<>());
        s.setResetVarsOnArrival(config.getResetVarsOnArrival() != null
            ? new ArrayList<>(config.getResetVarsOnArrival()) : new ArrayList<>());
        s.setOnEnterRules(config.getOnEnterRules() != null
            ? new LinkedHashMap<>(config.getOnEnterRules()) : new LinkedHashMap<>());
        s.setAllowUnlistedTransitions(config.isAllowUnlistedTransitions());
        s.setDefaultTransitionAction(config.getDefaultTransitionAction());
        s.setInheritanceChain(inheritanceChain);

        if (config.getTransitions() != null) {
            for (TransitionRule t : config.getTransitions()) {
                TransitionSnapshot ts = new TransitionSnapshot();
                ts.setSource(t.getSource());
                ts.setTarget(t.getTarget());
                ts.setIntentCode(t.getIntentCode());
                ts.setEvent(t.getEvent());
                ts.setCondition(t.getCondition());
                ts.setPriority(t.getPriority());
                ts.setExitReason(t.getExitReason());
                ts.setDescription(t.getDescription());
                if (t.getEffects() != null) {
                    List<String> eff = new ArrayList<>();
                    for (TransitionRule.EffectRule e : t.getEffects()) {
                        eff.add(e.getVar() + " = " + e.getValue());
                    }
                    ts.setEffects(eff);
                }
                s.getTransitions().add(ts);
            }
        }
        return s;
    }
}

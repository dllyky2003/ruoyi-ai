package com.ruoyi.ems.config;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class CaseDefinition {
    private String caseId;
    private String caseName;
    private String version;
    private String parent;
    private String description;
    private Map<String, String> stateDescriptions;
    private Initialization initialization;
    private StateMachine stateMachine;
    private List<VariableDefinition> variables;
    private Map<String, String> slotMappings;
    private List<RuleDefinition> rules;
    private List<ReflexRuleDefinition> reflexRules;
    private Map<String, PhysiologyDefinition> physiology;
    private Map<String, List<String>> onEnterRules;
    private External external;

    @Data
    public static class Initialization {
        private List<String> resetVarsOnArrival;
    }

    @Data
    public static class StateMachine {
        private String initial;
        private List<String> states;
        private List<String> endStates;
        private List<Transition> transitions;
    }

    @Data
    public static class Transition {
        private String source;
        private String target;
        private String condition;
        private Integer priority;
        private String description;
        private String action;
        private Boolean deviation;
    }

    @Data
    public static class VariableDefinition {
        private String name;
        private String nameCn;
        private String type;          // "integer", "boolean", "string", "long"
        private Integer min;
        private Integer max;
        private String unit;
        private List<EnumValue> enumValues;
        private String description;
        private String category;
        private String source;
        private String writableBy;
        private Object defaultValue;   // 可能是 String/Integer/Boolean，用 Object 接收
        private Boolean persistence;
        private Boolean scoring;
    }

    @Data
    public static class EnumValue {
        private String value;
        private String label;
    }

    @Data
    public static class RuleDefinition {
        private String id;
        private String intent;
        private List<String> allowedStates;
        private List<String> conditions;
        private String denyMessage;
        private Integer priority;
    }

    @Data
    public static class ReflexRuleDefinition {
        private String id;
        private String duty;
        private String condition;
        private String speech;
        private Integer priority;
        private String type;          // "speech" 或 "action"
        private String clinicalBasis;
        private String animation;
    }

    @Data
    public static class PhysiologyDefinition {
        private Integer heartRate;
        private Integer systolicBp;
        private Integer diastolicBp;
        private Integer spo2;
        private Integer respiratoryRate;
        private String consciousness;
        private Integer transitionDuration;  // 对应 transition_duration
    }

    @Data
    public static class External {
        private String chains;
        private String intents;
    }
}

package com.ruoyi.acls.contract;


import lombok.Data;
import java.util.*;

@Data
public class ContractSnapshot {

    // ===== 案例元数据 =====
    private String caseId;
    private String caseName;
    private String parent;
    private String initialState;
    private List<String> terminalStates = new ArrayList<>();
    private List<String> globalEventIntents = new ArrayList<>();
    private Map<String, Object> resetDefaults = new LinkedHashMap<>();
    private String snapshotVersion;
    private String generatedAt;

    // ===== 状态模板 =====
    private List<StateTemplate> stateTemplates = new ArrayList<>();

    // ===== 意图 =====
    private List<IntentSnapshot> intents = new ArrayList<>();

    // ===== 变量 =====
    private VariableSnapshot variables = new VariableSnapshot();

    // ===== 派生规则 =====
    private List<DerivationSnapshot> derivations = new ArrayList<>();

    // ===== 技能与写回 =====
    private List<SkillSnapshot> skills = new ArrayList<>();

    // ===== 护栏 =====
    private List<GuardRailSnapshot> guardRails = new ArrayList<>();

    // ===== 反射规则 =====
    private List<ReflexRuleSnapshot> reflexRules = new ArrayList<>();

    // ===== 生理基线 =====
    private Map<String, Object> physiologyBaseline = new LinkedHashMap<>();

    // ===== 规则配置 =====
    private List<RuleSnapshot> rules = new ArrayList<>();

    // ===== 2025 AHA 指南规则（人工维护，可外部注入）=====
    private List<GuidelineRule> guidelineRules = new ArrayList<>();

    // ===== 终止性规则（人工维护，可外部注入）=====
    private List<TerminationRule> terminationRules = new ArrayList<>();
    // ===== 案例元数据补充 =====
    private String version;
    private boolean allowUnlistedTransitions;
    private String defaultTransitionAction;

    // ===== 意图白名单补充 =====
    private List<String> alwaysAllowedIntents = new ArrayList<>();
    private Map<String, String> intentToStateMapping = new LinkedHashMap<>();

    // ===== 状态进入规则 =====
    private Map<String, List<String>> onEnterRules = new LinkedHashMap<>();

    // ===== 当前已有转移（作为 LLM 参考，防止重写时漏分支）=====
    private List<TransitionSnapshot> currentTransitions = new ArrayList<>();

    // ===== 新增子结构 =====
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
    // ---------------- 子结构 ----------------

    @Data
    public static class StateTemplate {
        private String id;
        private String name;
        private boolean terminal;
        private List<String> tags = new ArrayList<>();
        private String description;
    }

    @Data
    public static class IntentSnapshot {
        private String intentCode;
        private String nameCn;
        private String actType;
        private String domainCode;
        private String subDomain;
        private boolean general;
        private boolean specific;
        private String caseCode;
        private List<String> examples = new ArrayList<>();
        private List<String> regex = new ArrayList<>();
        private List<String> negativeRegex = new ArrayList<>();
        private List<String> stateTagsAny = new ArrayList<>();
        private List<String> stateTagsAll = new ArrayList<>();
        private Map<String, Object> contextVars = new LinkedHashMap<>();
        private List<String> slots = new ArrayList<>();
        private String outArtifact;
        private String downstream;
    }

    @Data
    public static class VariableSnapshot {
        private List<String> readable = new ArrayList<>();
        private List<String> writable = new ArrayList<>();
        private List<String> derived = new ArrayList<>();
        private Map<String, Object> defaults = new LinkedHashMap<>();
        private Map<String, String> prefixes = new LinkedHashMap<>();
    }

    @Data
    public static class DerivationSnapshot {
        private String targetVar;
        private String expression;
        private List<String> dependsOn = new ArrayList<>();
    }

    @Data
    public static class SkillSnapshot {
        private String skillId;
        private String intentCode;
        private String description;
        private List<String> writes = new ArrayList<>();
        private List<String> reads = new ArrayList<>();
        private Map<String, String> writeConditions = new LinkedHashMap<>();
    }

    @Data
    public static class GuardRailSnapshot {
        private String ruleId;
        private String target;
        private String condition;
        private String action;
        private String severity;
    }

    @Data
    public static class ReflexRuleSnapshot {
        private String ruleId;
        private String trigger;
        private String action;
        private String targetState;
    }

    @Data
    public static class RuleSnapshot {
        private String ruleId;
        private String type;
        private String expression;
    }

    @Data
    public static class GuidelineRule {
        private String ruleId;
        private String target;
        private String appliesWhen;
        private String require;
        private String severity;
        private String guidelineRef;
    }

    @Data
    public static class TerminationRule {
        private String ruleId;
        private String description;
        private String severity;
    }
}

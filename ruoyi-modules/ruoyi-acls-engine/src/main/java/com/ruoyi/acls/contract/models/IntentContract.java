package com.ruoyi.acls.contract.models;

import lombok.Data;

import java.util.*;

@Data
public class IntentContract {
    private String intentCode;
    private String nameCn;
    private String actType;
    private String writerType;       // EXECUTOR / CLASSIFIER / STATE_MACHINE
    private boolean general;
    private String caseCode;

    // 触发模式（三个来源合并）
    private Set<String> surfaces = new LinkedHashSet<>();      // IntentDictLoader
    private Set<String> triggers = new LinkedHashSet<>();      // IntentDefinition.trg.regex
    private Set<String> aliases = new LinkedHashSet<>();       // IntentDefinition.aliases
    private Set<String> examples = new LinkedHashSet<>();      // IntentDefinition.examples
    private Set<String> negativeRegex = new LinkedHashSet<>(); // IntentDefinition.trg.negative_regex

    // 路由（按 state 维度）
    private Map<String, String> skillsByState = new LinkedHashMap<>();
    private Set<String> allSkills = new LinkedHashSet<>();
    private Set<String> writtenVariables = new LinkedHashSet<>();

    // 关联
    private Set<String> mayTriggerTransitions = new LinkedHashSet<>();
    private Set<String> guardRules = new LinkedHashSet<>();

    // 诊断
    private boolean noSkill;
    private boolean noWriter;
    private boolean alwaysAllowed;
    private List<String> warnings = new ArrayList<>();
}

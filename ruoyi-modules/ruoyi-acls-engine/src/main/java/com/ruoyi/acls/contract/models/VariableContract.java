package com.ruoyi.acls.contract.models;

import lombok.Data;

import java.util.*;

@Data
public class VariableContract {
    private String name;
    private String nameCn;
    private String type;
    private String category;
    private String source;
    private String writableBy;

    // 写入方
    private Set<String> writtenByIntents = new LinkedHashSet<>();
    private Set<String> writtenByDerivations = new LinkedHashSet<>();
    private Set<String> writtenByTransitions = new LinkedHashSet<>();
    private Set<String> writtenByOnEnter = new LinkedHashSet<>();
    private boolean writtenBySystem;

    // 读取方
    private Set<String> readByGuards = new LinkedHashSet<>();          // 状态机转移
    private Set<String> readByOnEnter = new LinkedHashSet<>();         // 状态 onEnter
    private Set<String> readByDerivations = new LinkedHashSet<>();
    private Set<String> readBySkills = new LinkedHashSet<>();          // 技能前置条件
    private Set<String> readByGuardRails = new LinkedHashSet<>();      // 安全护栏
    private Set<String> readByReflexRules = new LinkedHashSet<>();     // 反射规则

    // 诊断
    private boolean orphan;         // 无人读
    private boolean unwritable;     // 被读但无人写
    private List<String> warnings = new ArrayList<>();
}

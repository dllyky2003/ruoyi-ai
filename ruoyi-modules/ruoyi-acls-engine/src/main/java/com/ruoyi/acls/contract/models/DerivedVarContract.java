package com.ruoyi.acls.contract.models;

import lombok.Data;

import java.util.*;

@Data
public class DerivedVarContract {
    private String name;
    private String expression;
    private String type;
    private Set<String> dependencies = new LinkedHashSet<>();
    private Set<String> leafDependencies = new LinkedHashSet<>();
    private Set<String> readByGuards = new LinkedHashSet<>();
    private Set<String> readByOnEnter = new LinkedHashSet<>();
    private Set<String> readByDerivations = new LinkedHashSet<>();

    // 诊断
    private boolean undefined;
    private boolean circular;
    private List<String> warnings = new ArrayList<>();
}

package com.ruoyi.acls.contract.models;

import lombok.Data;

import java.util.*;

@Data
public class ContractDiagnostics {
    private String caseId;
    private List<String> undefinedDerivedVars = new ArrayList<>();
    private List<String> deadTransitions = new ArrayList<>();
    private List<String> selfLoops = new ArrayList<>();
    private List<String> unwritableVariables = new ArrayList<>();
    private List<String> orphanVariables = new ArrayList<>();
    private List<String> emptyIntents = new ArrayList<>();
    private List<String> circularDependencies = new ArrayList<>();
    private List<String> skillCoverageIssues = new ArrayList<>();
    private List<String> routingConflicts = new ArrayList<>();

    public static ContractDiagnostics from(CaseContract c) {
        ContractDiagnostics d = new ContractDiagnostics();
        d.setCaseId(c.getCaseId());
        c.getDerivedVarContracts().values().forEach(dv -> {
            if (dv.isUndefined()) d.getUndefinedDerivedVars().add(dv.getName());
            if (dv.isCircular()) d.getCircularDependencies().add(dv.getName());
        });
        c.getTransitionContracts().values().forEach(tc -> {
            if (tc.isDead()) d.getDeadTransitions().add(tc.getTransitionId());
            if (tc.isSelfLoop()) d.getSelfLoops().add(tc.getTransitionId());
        });
        c.getVariableContracts().values().forEach(vc -> {
            if (vc.isUnwritable()) d.getUnwritableVariables().add(vc.getName());
            if (vc.isOrphan()) d.getOrphanVariables().add(vc.getName());
        });
        c.getIntentContracts().values().forEach(ic -> {
            if (ic.isNoSkill()) d.getEmptyIntents().add(ic.getIntentCode());
        });
        return d;
    }

    public boolean hasErrors() {
        return !undefinedDerivedVars.isEmpty()
            || !deadTransitions.isEmpty()
            || !circularDependencies.isEmpty();
    }
}

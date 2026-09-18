package com.ruoyi.acls.contract.models;

import lombok.Data;

import java.util.*;

@Data
public class CaseContract {
    private String caseId;
    private Map<String, StateContract> stateContracts = new LinkedHashMap<>();
    private Map<String, TransitionContract> transitionContracts = new LinkedHashMap<>();
    private Map<String, IntentContract> intentContracts = new LinkedHashMap<>();
    private Map<String, VariableContract> variableContracts = new LinkedHashMap<>();
    private Map<String, DerivedVarContract> derivedVarContracts = new LinkedHashMap<>();
    private List<String> buildWarnings = new ArrayList<>();

    public CaseContract() {}
    public CaseContract(String caseId) { this.caseId = caseId; }
}

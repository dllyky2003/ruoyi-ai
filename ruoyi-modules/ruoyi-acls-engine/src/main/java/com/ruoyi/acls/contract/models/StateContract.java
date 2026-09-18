package com.ruoyi.acls.contract.models;

import lombok.Data;

import java.util.*;

@Data
public class StateContract {
    private String stateId;
    private String stateDescription;
    private boolean initialState;
    private boolean endState;
    private List<String> onEnterRules = new ArrayList<>();
    private List<TransitionContract> transitions = new ArrayList<>();
    private Set<String> requiredVariables = new LinkedHashSet<>();
    private Set<String> requiredIntents = new LinkedHashSet<>();
}

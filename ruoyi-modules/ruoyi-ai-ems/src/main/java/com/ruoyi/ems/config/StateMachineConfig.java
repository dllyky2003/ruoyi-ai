package com.ruoyi.ems.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.*;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateMachineConfig {
    private String caseId;
    private String caseName;
    private String version;
    private String parent;
    private String description;
    private StateMachineDefinition stateMachine;
    private Map<String, String> stateDescriptions;
    private List<String> resetVarsOnArrival = new ArrayList<>();
    private Map<String, List<String>> onEnterRules;
}

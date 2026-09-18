package com.ruoyi.ems.domain;

import lombok.Data;
import java.util.List;
import lombok.Data;
import java.util.List;

@Data
public class RuleDefinition {
    private String id;
    private String intent;
    private List<String> allowedStates;
    private List<String> conditions;
    private String denyMessage;
    private int priority = 100;
}

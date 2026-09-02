package com.ruoyi.ems.domain;

import lombok.Data;

import java.util.Map;

@Data
public class ScoringRules {
    private String compareWith;
    private Integer tolerance;
    private Map<String, Object> mapping;
}

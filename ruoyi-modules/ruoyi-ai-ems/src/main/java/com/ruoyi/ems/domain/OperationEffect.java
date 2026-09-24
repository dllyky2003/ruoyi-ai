package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OperationEffect {

    private String variable;
    private String type;
    private Float value;
    private Integer steps;
    private String jumpTo;
    private String unityStateName;
    private Integer transitionDuration;
    private String target;
    private float delta;
    private Integer duration;
    private Integer recovery;
    private String curve;

    @JsonAlias("var")
    public void setVariableAlias(String v) { this.variable = v; }

    @JsonAlias("targetVar")
    public void setTargetAlias(String t) { this.target = t; }
}

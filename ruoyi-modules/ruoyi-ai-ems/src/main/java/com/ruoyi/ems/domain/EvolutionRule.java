package com.ruoyi.ems.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvolutionRule {
    private String rhythm;
    private String variable;
    private long afterSeconds;
    private Float delta;
}

package com.ruoyi.ems.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 生理配置：心律 → 体征基准 + 操作效果 + 演变规则。
 *
 * <p>YAML 采用 snake_case 命名（{@code state_physiology}），
 * 通过 {@code @JsonNaming} 自动映射到驼峰字段。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PhysiologyConfig {

    private String configVersion;
    private String configHash;
    private int defaultTransitionDuration = 2;
    private Map<String, String> rhythmMapping;
    private Map<String, StatePhysiology> statePhysiology;
    private Map<String, List<OperationEffect>> operationEffects;
    private List<EvolutionRule> evolutionRules;
}

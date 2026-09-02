package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * 意图参数抽取配置（映射 JSON 中的 extraction 字段）
 */
@Data
@NoArgsConstructor
public class ExtractionConfig {

    @JsonProperty("target_params")
    private List<String> targetParams;      // 目标参数列表，如 ["drug", "dose"]

    @JsonProperty("rules")
    private Map<String, ParamRule> rules;   // 参数名 -> 具体抽取规则

    private String overrideInstruction;
}


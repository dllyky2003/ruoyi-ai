package com.ruoyi.ems.intent.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionConfig {
    private String method;
    @JsonProperty("target_params")
    private List<String> targetParams;
    @JsonProperty("outputs")
    private Map<String, String> outputs;    // ★ 新增：processor 路径的"短名 → 全名"映射
    private Map<String, Object> rules;      // 或使用自定义类，但 Map 更通用
    @JsonProperty("llm_prompt")
    private String llmPrompt;
    @JsonProperty("override_instruction")
    private String overrideInstruction;
}

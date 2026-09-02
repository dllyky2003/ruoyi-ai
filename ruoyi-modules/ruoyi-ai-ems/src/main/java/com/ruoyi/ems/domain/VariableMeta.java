package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariableMeta {

    private String name;
    private String type;

    /**
     * 枚举选项列表（新格式，包含 value 和 label）
     * 对应 JSON 中的 "enum": [{"value": "...", "label": "..."}, ...]
     */
    @JsonProperty("enum")
    private List<EnumOption> enumOptions;

    private Integer min;
    private Integer max;
    private String unit;
    private String description;
    private String category;
    private String source;

    @JsonProperty("writable_by")
    private String writableBy;

    @JsonProperty("default_value")
    private Object defaultValue;

    private Boolean persistence;
    private Boolean scoring;

    @JsonProperty("scoring_rules")
    private ScoringRules scoringRules;

    private List<String> applicableTo;
    private Map<String, Object> items;

    /**
     * 根据枚举值获取对应的显示标签
     */
    public String getEnumLabel(String value) {
        if (enumOptions != null) {
            for (EnumOption opt : enumOptions) {
                if (opt.getValue().equals(value)) {
                    return opt.getLabel();
                }
            }
        }
        return null; // 未找到标签
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnumOption {
        private String value;
        private String label;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoringRules {
        @JsonProperty("compare_with")
        private String compareWith;

        private Integer tolerance;
        private Map<String, Object> mapping;
    }
}

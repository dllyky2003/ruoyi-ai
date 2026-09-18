package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlotSpec {

    private String type;                // enum|string|number|time|boolean
    private List<String> values;
    private boolean required = false;
    private double weight = 0.0;

    @JsonProperty("extract_by")
    private String extractBy = "regex"; // regex|model

    /** patterns: 枚举值 -> 口语样本列表 */
    private Map<String, List<String>> patterns;
}

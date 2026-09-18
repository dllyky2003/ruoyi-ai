package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ActTypeRule {
    @JsonProperty("pattern") private String pattern;
    @JsonProperty("act_type") private String actType;
    @JsonProperty("priority") private Integer priority = 0;
}

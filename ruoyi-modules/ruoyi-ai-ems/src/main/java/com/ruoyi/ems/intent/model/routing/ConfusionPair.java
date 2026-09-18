package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfusionPair {
    private String with;               // 对方 intent_code
    private String mode = "score_penalty";
    private Double delta = 0.08;
}

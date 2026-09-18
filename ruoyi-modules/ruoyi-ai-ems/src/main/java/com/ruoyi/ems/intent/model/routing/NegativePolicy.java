package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NegativePolicy {

    private String mode = "hard";   // hard | soft

    @JsonProperty("block_on_any_hit")
    private boolean blockOnAnyHit = true;

    @JsonProperty("downweight_factor")
    private double downweightFactor = 0.1;

    @JsonProperty("negative_scope")
    private String negativeScope = "same_turn";  // same_turn|same_sentence|same_window
}

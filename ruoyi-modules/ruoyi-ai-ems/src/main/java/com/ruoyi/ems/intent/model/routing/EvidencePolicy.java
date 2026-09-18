package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvidencePolicy {

    @JsonProperty("min_regex_hit")
    private int minRegexHit = 1;

    @JsonProperty("slot_required")
    private List<String> slotRequired;

    @JsonProperty("on_slot_missing")
    private OnSlotMissingPolicy onSlotMissing;

    @JsonProperty("required_slot_policy")
    private RequiredSlotPolicy requiredSlotPolicy;

    @JsonProperty("slot_confidence_threshold")
    private double slotConfidenceThreshold = 0.5;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OnSlotMissingPolicy {
        private String mode = "downweight";
        private double factor = 0.5;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequiredSlotPolicy {
        private String mode = "reject";
        @JsonProperty("downweight_factor")
        private double downweightFactor = 0.3;
    }
}

package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouterDefaults {

    @JsonProperty("negative_policy")
    private NegativePolicy negativePolicy;

    @JsonProperty("evidence_policy")
    private EvidencePolicy evidencePolicy;

    @JsonProperty("confusion_policy")
    private ConfusionPolicy confusionPolicy;

    @JsonProperty("accept_policy")
    private AcceptPolicy acceptPolicy;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfusionPolicy {
        private String mode = "score_penalty";
        private double delta = 0.08;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AcceptPolicy {
        @JsonProperty("top1_min_score")
        private double top1MinScore = 0.62;

        @JsonProperty("top1_top2_margin")
        private double top1Top2Margin = 0.12;

        @JsonProperty("on_margin_fail")
        private String onMarginFail = "clarify_or_fallback";
    }
}

package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoringDefinition {

    @JsonProperty("w_trg")
    private double wTrg = 0.65;

    @JsonProperty("w_prior")
    private double wPrior = 0.25;

    @JsonProperty("w_pref")
    private double wPref = 0.10;

    @JsonProperty("score_range")
    private List<Double> scoreRange;

    @JsonProperty("S_trg_definition")
    private STrgDefinition sTrgDefinition;

    @JsonProperty("S_prior_definition")
    private SPriorDefinition sPriorDefinition;

    @JsonProperty("S_pref_definition")
    private SPrefDefinition sPrefDefinition;

    @JsonProperty("final_score_formula")
    private String finalScoreFormula;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class STrgDefinition {
        @JsonProperty("min_regex_hit_fail_action")
        private String minRegexHitFailAction = "hard_reject";

        @JsonProperty("required_slot_missing_action_by_policy")
        private Map<String, String> requiredSlotMissingActionByPolicy;

        @JsonProperty("on_slot_missing_downweight_factor_default")
        private double onSlotMissingDownweightFactorDefault = 0.5;

        @JsonProperty("S_regex_formula")
        private String sRegexFormula;

        @JsonProperty("S_required_formula")
        private String sRequiredFormula;

        @JsonProperty("S_optional_formula")
        private String sOptionalFormula;

        @JsonProperty("S_trg_formula")
        private String sTrgFormula;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SPriorDefinition {
        @JsonProperty("h_weight")
        private double hWeight = 0.5;

        @JsonProperty("act_weight")
        private double actWeight = 0.3;

        @JsonProperty("obj_weight")
        private double objWeight = 0.2;

        @JsonProperty("match_rule")
        private String matchRule = "exact_or_unknown";

        @JsonProperty("exact_score")
        private double exactScore = 1.0;

        @JsonProperty("fallback_score")
        private double fallbackScore = 0.3;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SPrefDefinition {
        private String mode = "confusion_pair_only";

        @JsonProperty("penalty_delta")
        private double penaltyDelta = 0.08;
    }
}

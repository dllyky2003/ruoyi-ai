package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerConfig {

    private List<String> regex;

    @JsonProperty("negative_regex")
    private List<String> negativeRegex;

    private Map<String, SlotSpec> slots;

    @JsonProperty("evidence_policy")
    private EvidencePolicy evidencePolicy;
}

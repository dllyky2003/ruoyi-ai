package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.ems.intent.model.routing.EvidencePolicy;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public  class Trg {
    private List<String> regex;
    @JsonProperty("negative_regex")
    private List<String> negativeRegex;
    private Map<String, Object> slots;
    private EvidencePolicy evidencePolicy;
}

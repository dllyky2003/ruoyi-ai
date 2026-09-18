package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteGuard {

    @JsonProperty("guard_mode")
    private String guardMode = "soft";   // hard | soft

    @JsonProperty("requires_context")
    private RouteGuardContext requiresContext;

    @JsonProperty("excludes_context")
    private RouteGuardContext excludesContext;

    @JsonProperty("confusion_pairs")
    private List<ConfusionPair> confusionPairs;
}

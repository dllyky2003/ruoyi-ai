package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteGuardContext {

    @JsonProperty("state_tags_all")
    private List<String> stateTagsAll;

    @JsonProperty("state_tags_any")
    private List<String> stateTagsAny;

    @JsonProperty("context_vars")
    private Map<String, Object> contextVars;

    @JsonProperty("history_window")
    private HistoryWindow historyWindow;
}

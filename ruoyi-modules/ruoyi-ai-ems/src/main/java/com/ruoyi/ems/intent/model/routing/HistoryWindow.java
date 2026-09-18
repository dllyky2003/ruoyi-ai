package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryWindow {
    private int seconds = 0;

    @JsonProperty("state_tags_any")
    private List<String> stateTagsAny;
}

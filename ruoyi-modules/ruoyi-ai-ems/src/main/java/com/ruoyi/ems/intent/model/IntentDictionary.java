package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentDictionary {
    private String dictVersion;
    private String lastUpdated;
    private String description;
    private List<Layer> layers;
}

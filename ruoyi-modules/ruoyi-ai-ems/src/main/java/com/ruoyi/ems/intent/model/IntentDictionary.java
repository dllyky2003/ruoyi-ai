package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.ems.intent.model.routing.RoutePipeline;
import com.ruoyi.ems.intent.model.routing.RouterDefaults;
import com.ruoyi.ems.intent.model.routing.ScoringDefinition;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)

public class IntentDictionary {

    // ==================== 原有字段 ====================
    @JsonProperty("dict_version")
    private String dictVersion;

    @JsonProperty("last_updated")
    private String lastUpdated;

    private String description;

    private List<Layer> layers;

    // ==================== v1.3 新增字段 ====================
    @JsonProperty("route_feature_schema")
    private Map<String, Object> routeFeatureSchema;

    @JsonProperty("route_pipeline")
    private RoutePipeline routePipeline;

    @JsonProperty("scoring_definition")
    private ScoringDefinition scoringDefinition;

    private RouterDefaults defaults;

    @JsonProperty("state_tags_vocabulary")
    private List<String> stateTagsVocabulary;

    @JsonProperty("context_vars_vocabulary")
    private List<String> contextVarsVocabulary;

    @JsonProperty("router_trace_schema")
    private Map<String, Object> routerTraceSchema;

    @JsonProperty("case_code")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String caseCode;

    @JsonProperty("case_name_cn")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String caseNameCn;
}

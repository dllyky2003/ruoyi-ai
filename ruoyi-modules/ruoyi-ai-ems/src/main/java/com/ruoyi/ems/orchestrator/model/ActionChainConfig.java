package com.ruoyi.ems.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionChainConfig(String type, List<StepConfig> steps, Map<String, List<String>> preconditions,
                                OnCompleteConfig onComplete, OnErrorConfig onError, Boolean enabled) {

    @JsonCreator
    public ActionChainConfig(
        @JsonProperty("type") String type,
        @JsonProperty("steps") List<StepConfig> steps,
        @JsonProperty("preconditions") Map<String, List<String>> preconditions,
        @JsonProperty("on_complete") OnCompleteConfig onComplete,
        @JsonProperty("on_error") OnErrorConfig onError,
        @JsonProperty("enabled") Boolean enabled) {
        this.type = type;
        this.steps = steps;
        this.preconditions = preconditions;
        this.onComplete = onComplete;
        this.onError = onError;
        this.enabled = enabled == null || enabled;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepConfig(String stepId, String refAtomic, String duty, Integer durationSec, String vrAnimation,
                             String description, Boolean optional) {
        @JsonCreator
        public StepConfig(
            @JsonProperty("step_id") String stepId,
            @JsonProperty("ref_atomic") String refAtomic,
            @JsonProperty("duty") String duty,
            @JsonProperty("duration_sec") Integer durationSec,
            @JsonProperty("vr_animation") String vrAnimation,
            @JsonProperty("description") String description,
            @JsonProperty("optional") Boolean optional) {
            this.stepId = stepId;
            this.refAtomic = refAtomic;
            this.duty = duty;
            this.durationSec = durationSec;
            this.vrAnimation = vrAnimation;
            this.description = description;
            this.optional = optional != null && optional;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OnCompleteConfig(String transitionEvent, String responseTemplate, Boolean autoTransition) {
        @JsonCreator
        public OnCompleteConfig(
            @JsonProperty("transition_event") String transitionEvent,
            @JsonProperty("response_template") String responseTemplate,
            @JsonProperty("auto_transition") Boolean autoTransition) {
            this.transitionEvent = transitionEvent;
            this.responseTemplate = responseTemplate;
            this.autoTransition = autoTransition == null || autoTransition;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OnErrorConfig(String fallbackIntent, String responseTemplate, Integer maxRetries) {
        @JsonCreator
        public OnErrorConfig(
            @JsonProperty("fallback_intent") String fallbackIntent,
            @JsonProperty("response_template") String responseTemplate,
            @JsonProperty("max_retries") Integer maxRetries) {
            this.fallbackIntent = fallbackIntent;
            this.responseTemplate = responseTemplate;
            this.maxRetries = maxRetries != null ? maxRetries : 0;
        }
    }
}

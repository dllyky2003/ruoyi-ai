package com.ruoyi.ems.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 编排配置（从 orchestration/chain.json 加载）
 */
@Data
public class OrchestrationConfig {

    private String version;
    private String caseType;
    private String description;

    // === Stage 编排（原有） ===
    private PreconditionConfig preconditions;
    private List<StageConfig> stages;
    private ContinuousTaskConfig continuousTask;
    private ErrorRecoveryConfig errorRecovery;
    private List<PostConditionConfig> postConditions;

    @JsonProperty("action_chains")
    private Map<String, ChainConfig> actionChains;

    /**
     * 单个动作链配置
     */
    @Data
    public static class ChainConfig {
        private String description;
        private List<String> steps;                     // 步骤ID列表（有序）
        private Map<String, List<String>> preconditions; // stepId → 依赖的stepId列表
        private List<StepDefinition> resolvedSteps;     // 解析后的完整步骤（由加载器填充）
        private OnCompleteConfig onComplete;
        private OnErrorConfig onError;
        private Boolean enabled = true;
    }

    /**
     * 步骤定义（完整信息）
     * ★ 新增字段：procedure, speech, params, outputs
     */
    @Data
    public static class StepDefinition {
        private String stepId;
        private String procedure;          // ★ 新增：操作类型（ATOMIC / DEFIB / DRUG / AIRWAY）
        private String refAtomic;
        private String duty;
        private String vrAnimation;
        private Integer durationSec;
        private String description;
        private Boolean optional;
        private String speech;             // ★ 新增：语音提示

        // ★ 新增：输入参数
        private Map<String, Object> params;

        // ★ 新增：输出映射（核心）
        private Map<String, Object> outputs;
    }

    @Data
    public static class OnCompleteConfig {
        private String transitionEvent;
        private String responseTemplate;
        private Boolean autoTransition = true;
    }

    @Data
    public static class OnErrorConfig {
        private String fallbackIntent;
        private String responseTemplate;
        private Integer maxRetries = 0;
    }

    @Data
    public static class PostConditionConfig {
        private String triggerIntent;
        private String condition;
        private Object value;
    }

    @Data
    public static class ErrorRecoveryConfig {
        private Integer autoRetryCount;
        private String fallbackIntent;
    }

}

package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.ems.orchestrator.model.ActionChainConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentDefinition {

    @JsonProperty("intent_code")
    private String intentCode;          // 如 G1-1, S2-5-ccb

    @JsonProperty("name_cn")
    private String nameCn;

    @JsonProperty("description_cn")
    private String descriptionCn;

    @JsonProperty("keywords")
    private List<String> keywords;

    @JsonProperty("pattern_hints")
    private List<String> patternHints;

    @JsonProperty("examples")
    private List<String> examples;

    @JsonProperty("standard_script")
    private String standardScript;

    @JsonProperty("act_type")
    private String actType;             // 取值："询问" | "指令" | "宣告"

    // ===================================================================
    @JsonProperty("extraction")
    private ExtractionConfig extraction;  // 参数抽取配置，若无则为 null
    // ===================================================================

    // ★ 新增：执行配置（支持传统模式 + 动作链模式）
    @JsonProperty("execution")
    private ActionChainConfig execution;  // 动作链配置（含 steps, preconditions 等）

    // ===================================================================
    // 以下为运行时元数据（不从JSON加载）
    // ===================================================================

    private String layer;               // 层级名称
    private String subDomain;           // 子域名称
    private String domainCode;          // 归属域
    private boolean general;            // 是否 G 类
    private boolean specific;           // 是否 S 类
    private String caseCode;            // 仅 S 类有值

    // ===================================================================
    // 便捷方法
    // ===================================================================

    /**
     * 判断是否为动作链类型
     */
    public boolean isActionChain() {
        return execution != null && execution.steps() != null && !execution.steps().isEmpty();
    }

    /**
     * 判断是否为传统执行类型
     */
    public boolean isLegacyExecution() {
        return execution == null || execution.steps() == null || execution.steps().isEmpty();
    }

    /**
     * 获取动作链配置（如果存在）
     */
    public ActionChainConfig getActionChain() {
        return isActionChain() ? execution : null;
    }
}

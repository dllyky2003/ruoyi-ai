package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单条临床规则。最小版本只支持 3 个匹配字段 + 3 个结果字段。
 */
@Data
public class ClinicalRule {
    @JsonProperty("rule_id")
    @JsonAlias({"ruleId"})
    private String ruleId;

    @JsonProperty("match_intent")
    @JsonAlias({"matchIntent"})
    private String matchIntent;

    @JsonProperty("applies_when_current_state")
    @JsonAlias({"appliesWhenCurrentState"})
    private String appliesWhenCurrentState;

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("feedback_key")
    @JsonAlias({"feedbackKey"})
    private String feedbackKey;


    // ==================== V3.1 新增字段 ====================

    /** 评分标签，写入 ValidationResult.assessmentTags。 */
    @JsonProperty("assessment_tags")
    @JsonAlias({"assessmentTags"})
    private List<String> assessmentTags;

    /** 同状态多规则时的优先级，数值大者优先。 */
    @JsonProperty("priority")
    private Integer priority;

    /** 条件 DSL 表达式（Phase 2.2+ 求值；当前仅存储）。 */
    @JsonProperty("applies_when")
    @JsonAlias({"appliesWhen"})
    private Map<String, Object> appliesWhen;

    /** 该规则允许的受控效果（静态模板）。Phase 2.3 转成运行时 EffectSpec。 */
    @JsonProperty("effects")
    private List<EffectSpecStatic> effects;

    /** 指向 evidence_manifest.yaml 的 evidence_id。 */
    @JsonProperty("evidence_ref")
    @JsonAlias({"evidenceRef"})
    private String evidenceRef;

    /** 审核记录。 */
    @JsonProperty("review")
    private Review review;
}

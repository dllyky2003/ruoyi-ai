package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 教案剧情节点（V3.1 §6.4）。
 *
 * <p>beat_mode 决定字段可见性：
 * <ul>
 *   <li>observation：只允许 observation 段</li>
 *   <li>branch_selection：必须有 selection_group / selection_priority
 *       / proposed_effect / case_outcome_ref</li>
 * </ul>
 * </p>
 *
 * <p>本类仅承载数据结构；字段互斥的强校验由 JSON Schema 与契约诊断完成。</p>
 */
@Data
public class ScenarioBeat {

    // ---- 基本标识 ----
    @JsonProperty("beat_id")
    @JsonAlias({"beatId"})
    private String beatId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("beat_mode")
    @JsonAlias({"beatMode"})
    private String beatMode;   // "observation" | "branch_selection"

    @JsonProperty("sequence_hint")
    @JsonAlias({"sequenceHint"})
    private Integer sequenceHint;   // 仅用于展示排序，不参与运行时裁决

    // ---- 触发 ----
    @JsonProperty("trigger")
    private Trigger trigger;

    // ---- 观测型 ----
    @JsonProperty("observation")
    private Observation observation;

    // ---- 分支型 ----
    @JsonProperty("selection_group")
    @JsonAlias({"selectionGroup"})
    private String selectionGroup;

    @JsonProperty("selection_priority")
    @JsonAlias({"selectionPriority"})
    private Integer selectionPriority;

    @JsonProperty("proposed_effect")
    @JsonAlias({"proposedEffect"})
    private ProposedEffect proposedEffect;

    @JsonProperty("case_outcome_ref")
    @JsonAlias({"caseOutcomeRef"})
    private String caseOutcomeRef;

    // ---- 确定性教学演示专用 ----
    @JsonProperty("eligible_for_summative_use")
    @JsonAlias({"eligibleForSummativeUse"})
    private Boolean eligibleForSummativeUse;

    @JsonProperty("requires_explicit_learner_notice")
    @JsonAlias({"requiresExplicitLearnerNotice"})
    private Boolean requiresExplicitLearnerNotice;

    @JsonProperty("requires_educational_rationale")
    @JsonAlias({"requiresEducationalRationale"})
    private Boolean requiresEducationalRationale;

    @JsonProperty("requires_medical_review")
    @JsonAlias({"requiresMedicalReview"})
    private Boolean requiresMedicalReview;

    @JsonProperty("applicable_scenarios")
    @JsonAlias({"applicableScenarios"})
    private List<String> applicableScenarios;

    @JsonProperty("non_applicable_scenarios")
    @JsonAlias({"nonApplicableScenarios"})
    private List<String> nonApplicableScenarios;

    @JsonProperty("educational_rationale")
    @JsonAlias({"educationalRationale"})
    private String educationalRationale;

    // ---- 审核 ----
    @JsonProperty("review")
    private Review review;

    // ====================================================================
    // 内部类：Trigger
    // ====================================================================

    /**
     * 触发条件。
     *
     * <p>type 决定哪些字段有效：
     * <ul>
     *   <li>initial: state</li>
     *   <li>event: intervention_id (+ shock_index)</li>
     *   <li>compound: all_of（递归）</li>
     *   <li>rule_hit: rule_id</li>
     *   <li>deterministic_teaching_demo: deterministic + requires_instructor_mode</li>
     * </ul>
     * </p>
     */
    @Data
    public static class Trigger {

        @JsonProperty("type")
        private String type;

        @JsonProperty("state")
        private String state;

        @JsonProperty("intent_code")
        @JsonAlias({"intentCode"})
        private String intentCode;

        @JsonProperty("intervention_id")
        @JsonAlias({"interventionId"})
        private String interventionId;

        @JsonProperty("shock_index")
        @JsonAlias({"shockIndex"})
        private Integer shockIndex;

        @JsonProperty("rule_id")
        @JsonAlias({"ruleId"})
        private String ruleId;

        @JsonProperty("all_of")
        @JsonAlias({"allOf"})
        private List<Trigger> allOf;

        @JsonProperty("deterministic")
        private Boolean deterministic;

        @JsonProperty("requires_instructor_mode")
        @JsonAlias({"requiresInstructorMode"})
        private Boolean requiresInstructorMode;
    }

    // ====================================================================
    // 内部类：ProposedEffect
    // ====================================================================

    /**
     * 建议效果（V3.1 S1 / P0-7）。
     *
     * <p>只允许三字段。禁止声明 constraints / dose / energy / route /
     * shock_index 等效果参数；参数约束属于临床规则层。</p>
     */
    @Data
    public static class ProposedEffect {

        @JsonProperty("effect_id")
        @JsonAlias({"effectId"})
        private String effectId;

        @JsonProperty("branch_id")
        @JsonAlias({"branchId"})
        private String branchId;

        @JsonProperty("allowed_by_rule_ids")
        @JsonAlias({"allowedByRuleIds"})
        private List<String> allowedByRuleIds;
    }

    // ====================================================================
    // 内部类：Observation
    // ====================================================================

    /**
     * 观测型展示字段。不改变临床状态。
     */
    @Data
    public static class Observation {

        @JsonProperty("ui_banner")
        @JsonAlias({"uiBanner"})
        private String uiBanner;

        @JsonProperty("chapter_title")
        @JsonAlias({"chapterTitle"})
        private String chapterTitle;

        @JsonProperty("assessment_tag")
        @JsonAlias({"assessmentTag"})
        private String assessmentTag;
    }
}

package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 导师覆盖通道（V3.1 §6.4 / §6.5 / D4）。
 *
 * <p>覆盖动作必须映射为已批准的受控效果（{@code action.proposedEffect}），
 * 不能直接写 {@code ClinicalState}。与临床 {@code BLOCK} 冲突的覆盖被拒绝。</p>
 *
 * <p>评分中断：{@code triggersAssessmentInterruption=true} 时必须声明
 * {@code interruptedCriteria} 与 {@code interruptionResultPolicy}。</p>
 */
@Data
public class InstructorOverride {

    @JsonProperty("override_id")
    @JsonAlias({"overrideId"})
    private String overrideId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("allowed_when")
    @JsonAlias({"allowedWhen"})
    private AllowedWhen allowedWhen;

    @JsonProperty("action")
    private Action action;

    @JsonProperty("requires_reason")
    @JsonAlias({"requiresReason"})
    private Boolean requiresReason;

    @JsonProperty("audit_required")
    @JsonAlias({"auditRequired"})
    private Boolean auditRequired;

    @JsonProperty("triggers_assessment_interruption")
    @JsonAlias({"triggersAssessmentInterruption"})
    private Boolean triggersAssessmentInterruption;

    @JsonProperty("interrupted_criteria")
    @JsonAlias({"interruptedCriteria"})
    private List<String> interruptedCriteria;

    @JsonProperty("interruption_result_policy")
    @JsonAlias({"interruptionResultPolicy"})
    private String interruptionResultPolicy;

    @JsonProperty("affects_formative_score")
    @JsonAlias({"affectsFormativeScore"})
    private Boolean affectsFormativeScore;

    @JsonProperty("review")
    private Review review;

    // ====================================================================
    // 内部类
    // ====================================================================

    /**
     * 允许条件。
     */
    @Data
    public static class AllowedWhen {

        @JsonProperty("attempt_status")
        @JsonAlias({"attemptStatus"})
        private String attemptStatus;   // "RUNNING" | "PAUSED"

        @JsonProperty("current_state_in")
        @JsonAlias({"currentStateIn"})
        private List<String> currentStateIn;
    }

    /**
     * 覆盖动作。
     *
     * <p>type 决定哪些字段有效：
     * <ul>
     *   <li>force_case_outcome_branch: 必须有 proposedEffect</li>
     *   <li>clock_control: 必须有 operation，禁止 proposedEffect</li>
     * </ul>
     * </p>
     */
    @Data
    public static class Action {

        @JsonProperty("type")
        private String type;

        @JsonProperty("proposed_effect")
        @JsonAlias({"proposedEffect"})
        private ScenarioBeat.ProposedEffect proposedEffect;

        @JsonProperty("operation")
        private String operation;   // "pause" | "resume"
    }
}

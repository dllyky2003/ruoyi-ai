package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 兜底路径（V3.1 §4.3 / §6.4 / P0-8）。
 *
 * <p>MVP 硬规则：{@code fallbackAction.type} 只能是 {@code instructor_alert}。
 * 禁止自动演进病情、自动失败、自动执行临床效果。</p>
 *
 * <p>升级通道 {@code escalation} 是独立事件（{@code instructor_confirmation_required}），
 * 不是原 fallback 的自动续作。</p>
 */
@Data
public class FallbackPath {

    @JsonProperty("fallback_id")
    @JsonAlias({"fallbackId"})
    private String fallbackId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("applies_when_state")
    @JsonAlias({"appliesWhenState"})
    private String appliesWhenState;

    /** 相对时长，格式：{@code "180s"}。 */
    @JsonProperty("after")
    private String after;

    @JsonProperty("fallback_action")
    @JsonAlias({"fallbackAction"})
    private FallbackAction fallbackAction;

    @JsonProperty("escalation")
    private Escalation escalation;

    @JsonProperty("review")
    private Review review;

    // ====================================================================
    // 内部类
    // ====================================================================

    /**
     * 兜底动作。MVP 只允许 {@code instructor_alert}。
     */
    @Data
    public static class FallbackAction {

        @JsonProperty("type")
        private String type;   // "instructor_alert"

        @JsonProperty("message")
        private String message;
    }

    /**
     * 升级通道。必须是独立事件，不是自动续作。
     */
    @Data
    public static class Escalation {

        @JsonProperty("after")
        private String after;

        @JsonProperty("action")
        private String action;   // "instructor_confirmation_required"

        @JsonProperty("proposed_branch_id")
        @JsonAlias({"proposedBranchId"})
        private String proposedBranchId;

        @JsonProperty("requires_reason")
        @JsonAlias({"requiresReason"})
        private Boolean requiresReason;

        @JsonProperty("requires_medical_review")
        @JsonAlias({"requiresMedicalReview"})
        private Boolean requiresMedicalReview;

        @JsonProperty("affects_formative_score")
        @JsonAlias({"affectsFormativeScore"})
        private Boolean affectsFormativeScore;
    }
}

package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 教案根文件（V3.1 §6.3）。对应 config/acls/cases/{caseId}/lesson_plan.yaml。
 *
 * <p>只承载元数据与子文件引用（含哈希）；具体内容在
 * ScenarioBeat / FallbackPath / InstructorOverride 中。</p>
 */
@Data
public class LessonPlan {

    @JsonProperty("lesson_plan_id")
    @JsonAlias({"lessonPlanId"})
    private String lessonPlanId;

    @JsonProperty("lesson_plan_version")
    @JsonAlias({"lessonPlanVersion"})
    private String lessonPlanVersion;

    @JsonProperty("case_id")
    @JsonAlias({"caseId"})
    private String caseId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("target_audience")
    @JsonAlias({"targetAudience"})
    private String targetAudience;

    @JsonProperty("estimated_duration_min")
    @JsonAlias({"estimatedDurationMin"})
    private Integer estimatedDurationMin;

    @JsonProperty("learning_objectives")
    @JsonAlias({"learningObjectives"})
    private List<String> learningObjectives;

    @JsonProperty("instructor_authority_profile")
    @JsonAlias({"instructorAuthorityProfile"})
    private String instructorAuthorityProfile;

    @JsonProperty("eligible_for_summative_use")
    @JsonAlias({"eligibleForSummativeUse"})
    private Boolean eligibleForSummativeUse;

    @JsonProperty("scenario_beats_ref")
    @JsonAlias({"scenarioBeatsRef"})
    private FileRef scenarioBeatsRef;

    @JsonProperty("fallback_paths_ref")
    @JsonAlias({"fallbackPathsRef"})
    private FileRef fallbackPathsRef;

    @JsonProperty("instructor_overrides_ref")
    @JsonAlias({"instructorOverridesRef"})
    private FileRef instructorOverridesRef;

    @JsonProperty("review_status")
    @JsonAlias({"reviewStatus"})
    private String reviewStatus;

    @JsonProperty("reviewed_by")
    @JsonAlias({"reviewedBy"})
    private String reviewedBy;

    @JsonProperty("reviewed_at")
    @JsonAlias({"reviewedAt"})
    private String reviewedAt;

    /**
     * 子文件引用（文件名 + sha256）。
     */
    @Data
    public static class FileRef {
        @JsonProperty("file")
        private String file;

        @JsonProperty("sha256")
        private String sha256;
    }
}

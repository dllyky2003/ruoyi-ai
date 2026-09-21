package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 病例结局分支（V3.1 §5.6.1）。定义在 clinical_rules.yaml 的 case_outcome_branches 段。
 *
 * <p>是 {@code trend_labels} 与临床状态的唯一来源；教案 beat 侧不得复制。</p>
 */
@Data
public class CaseOutcomeBranch {

    @JsonProperty("branch_id")
    @JsonAlias({"branchId"})
    private String branchId;

    @JsonProperty("description")
    private String description;

    @JsonProperty("clinical_state")
    @JsonAlias({"clinicalState"})
    private BranchClinicalState clinicalState;

    @JsonProperty("presentation_mapping_ref")
    @JsonAlias({"presentationMappingRef"})
    private String presentationMappingRef;

    @JsonProperty("trend_labels")
    @JsonAlias({"trendLabels"})
    private List<String> trendLabels;

    @JsonProperty("evidence")
    private Evidence evidence;

    @JsonProperty("review")
    private Review review;
}

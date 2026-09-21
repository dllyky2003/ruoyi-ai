package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * case_outcome_branches[].evidence —— 证据来源（V3.1 §6.7）。
 *
 * <p>与 {@link Review} 分离（P0-11）：
 * {@code source_class} 表示"这条证据属于哪类来源"，
 * {@code review} 表示"这条分支被谁、何时、以何状态审核过"。</p>
 */
@Data
public class Evidence {

    @JsonProperty("source_class")
    @JsonAlias({"sourceClass"})
    private String sourceClass;

    @JsonProperty("supporting_guideline_refs")
    @JsonAlias({"supportingGuidelineRefs"})
    private List<String> supportingGuidelineRefs;

    @JsonProperty("evidence_manifest_refs")
    @JsonAlias({"evidenceManifestRefs"})
    private List<String> evidenceManifestRefs;
}

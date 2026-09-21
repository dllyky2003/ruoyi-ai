package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * case_outcome_branches[].clinical_state —— 病例结局分支的临床快照。
 *
 * <p>与 {@link ClinicalState}（V3.1 §3.1 四类状态之一）区分：
 * 本类是"某个结局分支下的离散临床值"，不是运行时状态容器。</p>
 */
@Data
public class BranchClinicalState {

    @JsonProperty("rhythm")
    private String rhythm;

    @JsonProperty("pulse")
    private String pulse;

    @JsonProperty("perfusion")
    private String perfusion;

    @JsonProperty("consciousness")
    private String consciousness;

    @JsonProperty("ventilation")
    private String ventilation;

    @JsonProperty("oxygenation")
    private String oxygenation;
}

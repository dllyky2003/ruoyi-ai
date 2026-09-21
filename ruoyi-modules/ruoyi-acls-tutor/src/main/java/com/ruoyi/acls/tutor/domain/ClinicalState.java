package com.ruoyi.acls.tutor.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 病例临床状态。表示病例预设的临床事实。
 * 本类只承载字段，无业务逻辑；后续由 ClinicalRuleEngine 读写。
 */
@Data
public class ClinicalState {
    private String rhythm;              // vf / pulseless_vt / pea / asystole / sinus / svt_regular_narrow ...
    private String pulse;               // present / absent / uncertain
    private String perfusion;           // adequate / poor / absent
    private String consciousness;       // alert / altered / unresponsive
    private String ventilation;         // adequate / inadequate / absent
    private String oxygenation;         // adequate / low / unknown
    private List<String> reversibleCauses = new ArrayList<>();
    private String caseOutcomeBranch;   // persistent_vf / rosc / deterioration ...
}

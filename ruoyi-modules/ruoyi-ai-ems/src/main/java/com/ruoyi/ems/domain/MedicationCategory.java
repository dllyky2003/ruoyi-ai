package com.ruoyi.ems.domain;

import lombok.Getter;

/**
 * 药物分类
 */
@Getter
public enum MedicationCategory {

    CARDIAC_ARREST("心脏骤停", "心脏骤停核心用药"),
    ANTIARRHYTHMIC("抗心律失常", "抗心律失常药物"),
    VASOACTIVE("血管活性", "升压/血管活性药物"),
    ACS("抗缺血", "ACS/抗缺血药物"),
    ANTICOAGULANT("抗凝", "抗凝药物"),
    ELECTROLYTE("电解质/代谢", "电解质和代谢药物"),
    ANTIDOTE("解毒/拮抗", "解毒和拮抗药物"),
    SEDATION("镇静/镇痛", "镇静/镇痛/诱导药物"),
    VOLUME("容量治疗", "晶体液/容量治疗"),
    BRONCHODILATOR("支气管扩张", "支气管扩张剂");

    private final String nameCn;
    private final String description;

    MedicationCategory(String nameCn, String description) {
        this.nameCn = nameCn;
        this.description = description;
    }
}

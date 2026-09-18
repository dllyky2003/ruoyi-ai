package com.ruoyi.ems.npc;

import lombok.Getter;

/**
 * 虚拟团队成员职责枚举
 * 对应团队模板 JSON 中的 duties 字段，遵循国内 120 院前急救分工标准
 */
@Getter
public enum Duty {
    TEAM_LEADER("组长", "全局指挥决策，不参与物理操作"),
    ASSESS("评估", "患者评估与诊断"),
    AIRWAY("气道管理", "开放气道、BVM通气、插管"),
    CPR("心肺复苏", "胸外按压"),
    IV_MED("静脉给药", "建立静脉通路、推注药物"),
    MONITOR_DEFIB("监护/除颤", "心电监护、AED/手动除颤操作"),
    RECORD("记录", "时间记录、用药记录、交接记录"),
    ASSIST("协助/搬运", "协助操作、患者转运、担架搬运");

    private final String displayName;
    private final String description;

    Duty(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}

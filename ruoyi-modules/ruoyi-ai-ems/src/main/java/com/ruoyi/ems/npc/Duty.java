package com.ruoyi.ems.npc;

/**
 * ACLS 流程中的具体职责
 * 一个团队成员可以承担一个或多个职责
 */
public enum Duty {
    /** 胸外按压 */
    CPR("胸外按压", "执行高质量胸外按压"),
    /** 气道管理（包括球囊通气、插管） */
    AIRWAY("气道管理", "维持气道通畅和通气"),
    /** 药物/静脉通路（推药、建立通路） */
    IV_MED("药物管理", "建立静脉通路、给药"),
    /** 监护/除颤（心律识别、电击） */
    MONITOR_DEFIB("监护除颤", "连接监护、识别心律、操作除颤仪"),
    /** 记录/时间（记录事件和用药时间） */
    RECORD("记录计时", "记录关键事件和时间节点"),
    /** 评估/指挥（通常由组长承担，但在NPC中可配置） */
    ASSESS("评估指挥", "负责评估和现场指挥"),
    /** 辅助/备勤（搬运、协助） */
    ASSIST("辅助搬运", "协助搬运和后勤支援");

    private final String displayName;
    private final String description;

    Duty(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
}

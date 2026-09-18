package com.ruoyi.ems.intent.classifier;


/**
 * ACLS 院前急救语用类型。
 * 顺序即规则优先级（高 → 低），用于冲突裁决。
 */
public enum ActType {
    VETO("否决"),
    STRATEGY_CORRECTION("策略纠偏"),
    CLOSED_LOOP("闭环确认"),
    COMMAND("发令性指令"),
    PROBE("探查性询问"),
    ANNOUNCEMENT("宣告"),
    UNCLASSIFIED("未分类");   // ← 新增

    private final String label;

    ActType(String label) { this.label = label; }

    public String label() { return label; }
}

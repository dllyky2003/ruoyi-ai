package com.ruoyi.ems.event;

import lombok.Getter;

import java.util.*;

/**
 * 案例事件枚举
 *
 * 统一定义系统中所有事件，分为五大类：
 * 1. 用户意图事件（S类）- 由学员指令触发
 * 2. 全局意图事件（G类）- 由学员通用指令触发
 * 3. 系统事件（SYS类）- 由系统内部触发
 * 4. 模拟器事件（SIM类）- 由模拟器回调触发
 * 5. 状态生命周期事件（STATE类）- 状态进入/退出时触发
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Getter
public enum CaseEvent {

    // ============================================================
    // 1. 用户意图事件（S类 - 案例特定）
    // ============================================================

    /** QRS 宽度判读 - 窄 */
    S1_1_NARROW("S1_1_NARROW", "QRS判读-窄", EventCategory.USER),
    /** QRS 宽度判读 - 宽 */
    S1_1_WIDE("S1_1_WIDE", "QRS判读-宽", EventCategory.USER),

    /** 节律规则性判读 - 规则 */
    S1_2_REGULAR("S1_2_REGULAR", "节律判读-规则", EventCategory.USER),
    /** 节律规则性判读 - 不规则 */
    S1_2_IRREGULAR("S1_2_IRREGULAR", "节律判读-不规则", EventCategory.USER),

    /** 稳定性评估 - 稳定 */
    S1_3_STABLE("S1_3_STABLE", "稳定性评估-稳定", EventCategory.USER),
    /** 稳定性评估 - 不稳定 */
    S1_3_UNSTABLE("S1_3_UNSTABLE", "稳定性评估-不稳定", EventCategory.USER),

    /** 心律综合判读请求 */
    S1_4_REQUEST("S1_4_REQUEST", "心律综合判读", EventCategory.USER),

    /** 迷走神经刺激完成 */
    S2_1_DONE("S2_1_DONE", "迷走刺激完成", EventCategory.USER),

    /** 腺苷推注完成 */
    S2_2_DONE("S2_2_DONE", "腺苷推注完成", EventCategory.USER),

    /** 盐水冲管完成 */
    S2_4_DONE("S2_4_DONE", "盐水冲管完成", EventCategory.USER),

    /** 二线药物执行完成 */
    S2_5A_DONE("S2_5A_DONE", "二线药物完成", EventCategory.USER),

    /** 同步电复律 - 开始准备 */
    S2_6_START("S2_6_START", "电复律准备", EventCategory.USER),
    /** 同步电复律 - 充电完成 */
    S2_6_CHARGED("S2_6_CHARGED", "电复律充电完成", EventCategory.USER),
    /** 同步电复律 - 放电完成 */
    S2_6_DONE("S2_6_DONE", "电复律放电完成", EventCategory.USER),

    /** WPW 预激警示 */
    S2_7_WPW_ALERT("S2_7_WPW_ALERT", "WPW预激警示", EventCategory.USER),

    /** 治疗结果 - 成功转复 */
    S2_8_SUCCESS("S2_8_SUCCESS", "治疗成功转复", EventCategory.USER),
    /** 治疗结果 - 失败 */
    S2_8_FAILURE("S2_8_FAILURE", "治疗失败", EventCategory.USER),

    /** 抗凝评估 */
    S2_9_ANTICOAG("S2_9_ANTICOAG", "抗凝评估", EventCategory.USER),

    /** 血流动力学恶化宣告 */
    S3_1_WORSENED("S3_1_WORSENED", "血流动力学恶化", EventCategory.USER),

    /** 紧急电复律决策 */
    S3_2_URGENT_CARDIOVERSION("S3_2_URGENT_CARDIOVERSION", "紧急电复律", EventCategory.USER),

    /** 药物诱发室颤宣告 */
    S4_1_VF("S4_1_VF", "药物诱发室颤", EventCategory.USER),
    /** 误电击致室颤宣告 */
    S4_2_MISSHOCK("S4_2_MISSHOCK", "误电击致室颤", EventCategory.USER),
    /** PEA/停搏宣告 */
    S4_3_PEA("S4_3_PEA", "PEA/停搏", EventCategory.USER),
    /** 禁忌操作拦截 */
    S4_4_BLOCKED("S4_4_BLOCKED", "禁忌操作拦截", EventCategory.USER),

    // ============================================================
    // 2. 全局意图事件（G类 - 通用）
    // ============================================================

    /** 安全清场 */
    G1_1_CLEAR("G1_1_CLEAR", "安全清场", EventCategory.GLOBAL),
    /** 安全禁忌询问 */
    G1_2_CONTRADICTION("G1_2_CONTRADICTION", "安全禁忌询问", EventCategory.GLOBAL),
    /** 环境安全评估 */
    G1_3_ENVIRONMENT("G1_3_ENVIRONMENT", "环境安全评估", EventCategory.GLOBAL),

    /** 角色分配 */
    G2_1_ROLE_ASSIGN("G2_1_ROLE_ASSIGN", "角色分配", EventCategory.GLOBAL),
    /** 闭环确认 */
    G2_2_CONFIRM("G2_2_CONFIRM", "闭环确认", EventCategory.GLOBAL),
    /** 时间记录 */
    G2_3_TIME_RECORD("G2_3_TIME_RECORD", "时间记录", EventCategory.GLOBAL),
    /** 会诊请求 */
    G2_4_CONSULT("G2_4_CONSULT", "会诊请求", EventCategory.GLOBAL),
    /** 策略纠偏 */
    G2_5_OVERRIDE("G2_5_OVERRIDE", "策略纠偏", EventCategory.GLOBAL),
    /** 治疗操作启动 */
    G2_6_OPERATION_START("G2_6_OPERATION_START", "操作启动", EventCategory.GLOBAL),
    /** 家属沟通 */
    G2_7_FAMILY("G2_7_FAMILY", "家属沟通", EventCategory.GLOBAL),
    /** 转运准备 */
    G2_8_TRANSPORT("G2_8_TRANSPORT", "转运准备", EventCategory.GLOBAL),

    /** 设备开机 */
    G3_1_SETUP_ON("G3_1_SETUP_ON", "设备开机", EventCategory.GLOBAL),
    /** 电极片连接 */
    G3_2_PADS_ON("G3_2_PADS_ON", "电极片连接", EventCategory.GLOBAL),
    /** 静脉通路建立 */
    G3_3_IV_ACCESS("G3_3_IV_ACCESS", "静脉通路建立", EventCategory.GLOBAL),

    /** 血压报告 */
    G4_1_BP_REPORT("G4_1_BP_REPORT", "血压报告", EventCategory.GLOBAL),
    /** 心率报告 */
    G4_2_HR_REPORT("G4_2_HR_REPORT", "心率报告", EventCategory.GLOBAL),
    /** 血氧/呼吸报告 */
    G4_3_SPO2_REPORT("G4_3_SPO2_REPORT", "血氧/呼吸报告", EventCategory.GLOBAL),
    /** 意识状态报告 */
    G4_4_CONSCIOUSNESS("G4_4_CONSCIOUSNESS", "意识状态报告", EventCategory.GLOBAL),
    /** 治疗后复评 */
    G4_5_REASSESS("G4_5_REASSESS", "治疗后复评", EventCategory.GLOBAL),
    /** 病史采集 */
    G4_6_HISTORY("G4_6_HISTORY", "病史采集", EventCategory.GLOBAL),

    // ============================================================
    // 3. 系统事件（SYS类）
    // ============================================================

    /** 超时 */
    SYS_TIMEOUT("SYS_TIMEOUT", "超时", EventCategory.SYSTEM),
    /** 超时-迷走刺激 */
    SYS_TIMEOUT_VAGAL("SYS_TIMEOUT_VAGAL", "迷走刺激超时", EventCategory.SYSTEM),
    /** 默认转移 */
    SYS_DEFAULT_TRANSITION("SYS_DEFAULT_TRANSITION", "默认转移", EventCategory.SYSTEM),

    /** 错误已纠正 */
    SYS_ERROR_FIXED("SYS_ERROR_FIXED", "错误已纠正", EventCategory.SYSTEM),
    /** 超时继续 */
    SYS_TIMEOUT_CONTINUE("SYS_TIMEOUT_CONTINUE", "超时后继续", EventCategory.SYSTEM),
    /** 超时自动推进 */
    SYS_TIMEOUT_NEXT("SYS_TIMEOUT_NEXT", "超时自动推进", EventCategory.SYSTEM),

    /** 终止案例 */
    SYS_TERMINATE("SYS_TERMINATE", "终止案例", EventCategory.SYSTEM),
    /** 案例重置 */
    SYS_RESET("SYS_RESET", "重置案例", EventCategory.SYSTEM),

    // ============================================================
    // 4. 模拟器事件（SIM类）
    // ============================================================

    /** 心律变更 */
    SIM_RHYTHM_CHANGED("SIM_RHYTHM_CHANGED", "心律变更", EventCategory.SIMULATOR),
    /** 生命体征更新 */
    SIM_VITAL_UPDATED("SIM_VITAL_UPDATED", "生命体征更新", EventCategory.SIMULATOR),
    /** 药物效果反馈 */
    SIM_DRUG_EFFECT("SIM_DRUG_EFFECT", "药物效果反馈", EventCategory.SIMULATOR),
    /** 电复律结果反馈 */
    SIM_CARDIOVERSION_RESULT("SIM_CARDIOVERSION_RESULT", "电复律结果", EventCategory.SIMULATOR),
    /** 患者状态变更 */
    SIM_PATIENT_STATE_CHANGED("SIM_PATIENT_STATE_CHANGED", "患者状态变更", EventCategory.SIMULATOR),

    // ============================================================
    // 5. 状态生命周期事件（STATE类 - 内部使用）
    // ============================================================

    /** 状态进入 */
    STATE_ENTRY("STATE_ENTRY", "状态进入", EventCategory.STATE_LIFECYCLE),
    /** 状态退出 */
    STATE_EXIT("STATE_EXIT", "状态退出", EventCategory.STATE_LIFECYCLE),
    /** 状态错误 */
    STATE_ERROR("STATE_ERROR", "状态错误", EventCategory.STATE_LIFECYCLE);

    // ==================== 字段 ====================

    /** 事件编码 */
    private final String code;

    /** 事件描述 */
    private final String description;

    /** 事件分类 */
    private final EventCategory category;

    // ==================== 构造器 ====================

    CaseEvent(String code, String description, EventCategory category) {
        this.code = code;
        this.description = description;
        this.category = category;
    }

    // ==================== 静态查找 ====================

    private static final Map<String, CaseEvent> CODE_MAP = new HashMap<>();

    static {
        for (CaseEvent event : values()) {
            CODE_MAP.put(event.code, event);
        }
    }

    public static CaseEvent fromCode(String code) {
        return CODE_MAP.get(code);
    }

    public static boolean exists(String code) {
        return CODE_MAP.containsKey(code);
    }

    // ==================== 分类快捷方法 ====================

    public boolean isUserEvent() {
        return category == EventCategory.USER;
    }

    public boolean isGlobalEvent() {
        return category == EventCategory.GLOBAL;
    }

    public boolean isSystemEvent() {
        return category == EventCategory.SYSTEM;
    }

    public boolean isSimulatorEvent() {
        return category == EventCategory.SIMULATOR;
    }

    public boolean isStateLifecycleEvent() {
        return category == EventCategory.STATE_LIFECYCLE;
    }

    @Override
    public String toString() {
        return code;
    }
}

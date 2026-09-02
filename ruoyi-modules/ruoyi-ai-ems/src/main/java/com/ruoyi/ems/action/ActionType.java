package com.ruoyi.ems.action;

/**
 * 系统动作类型枚举
 *
 * 定义所有系统可执行的动作类型，分为两大类：
 * 1. 模拟器动作（simulate_*）：由模拟器执行，改变患者生理状态
 * 2. 系统动作（非simulate_*）：由系统自身执行，如日志、通知、计时器
 *
 * @author RuoYi EMS
 * @version 1.0
 */
public enum ActionType {

    // ==================== 模拟器动作 ====================

    /**
     * 模拟药物推注
     * params: drug(药名), dose(剂量), route(途径: IV/IO/PO), rate(推注速度)
     */
    SIMULATE_DRUG("simulate_drug", "药物推注", ActionCategory.SIMULATOR),

    /**
     * 模拟同步电复律
     * params: energy(焦耳), mode(synchronized/unsynchronized)
     */
    SIMULATE_CARDIOVERSION("simulate_cardioversion", "同步电复律", ActionCategory.SIMULATOR),

    /**
     * 模拟非同步除颤
     * params: energy(焦耳)
     */
    SIMULATE_DEFIBRILLATION("simulate_defibrillation", "非同步除颤", ActionCategory.SIMULATOR),

    /**
     * 模拟迷走神经刺激
     * params: procedure(valsalva/carotid_sinus)
     */
    SIMULATE_VAGAL("simulate_vagal", "迷走神经刺激", ActionCategory.SIMULATOR),

    /**
     * 模拟心律变更
     * params: rhythm(新心律), heart_rate(新心率)
     */
    SIMULATE_RHYTHM_CHANGE("simulate_rhythm_change", "心律变更", ActionCategory.SIMULATOR),

    /**
     * 模拟生命体征变更
     * params: systolic_bp, diastolic_bp, heart_rate, spo2, respiratory_rate, consciousness
     */
    SIMULATE_VITAL_CHANGE("simulate_vital_change", "生命体征变更", ActionCategory.SIMULATOR),

    /**
     * 模拟呼吸/通气
     * params: mode(oxygen/bvm/intubation), rate(频率), volume(潮气量)
     */
    SIMULATE_VENTILATION("simulate_ventilation", "呼吸/通气", ActionCategory.SIMULATOR),

    /**
     * 模拟 CPR 开始/停止
     * params: action(start/stop/pause), rate(按压频率)
     */
    SIMULATE_CPR("simulate_cpr", "CPR操作", ActionCategory.SIMULATOR),

    /**
     * 模拟患者意识变更
     * params: consciousness(alert/verbal/pain/unresponsive), gcs(GCS评分)
     */
    SIMULATE_CONSCIOUSNESS("simulate_consciousness", "意识变更", ActionCategory.SIMULATOR),

    /**
     * 模拟胸痛
     * params: severity(0-10), location
     */
    SIMULATE_CHEST_PAIN("simulate_chest_pain", "胸痛模拟", ActionCategory.SIMULATOR),

    // ==================== 系统动作 ====================

    /**
     * 日志记录
     * params: message, level, data
     */
    LOG_EVENT("log_event", "日志记录", ActionCategory.SYSTEM),

    /**
     * 状态更新（由状态机自身处理）
     * params: state, additional
     */
    UPDATE_STATE("update_state", "状态更新", ActionCategory.SYSTEM),

    /**
     * 计时器操作
     * params: operation(start/stop/reset), duration(毫秒), callback
     */
    TIMER_OPERATION("timer_operation", "计时器操作", ActionCategory.SYSTEM),

    /**
     * 错误记录
     * params: code, message, severity, stack_trace
     */
    ERROR_RECORD("error_record", "错误记录", ActionCategory.SYSTEM),

    /**
     * 评分更新
     * params: category, score, reason, weight
     */
    SCORE_UPDATE("score_update", "评分更新", ActionCategory.SYSTEM),

    /**
     * 通知/告警（推送到前端）
     * params: level(info/warn/error), title, message, duration
     */
    NOTIFICATION("notification", "通知告警", ActionCategory.SYSTEM),

    /**
     * WebSocket 推送
     * params: event, data, target(room/private)
     */
    WEBSOCKET_PUSH("websocket_push", "WebSocket推送", ActionCategory.SYSTEM),

    /**
     * 案例终止
     * params: reason, duration(总耗时), outcome(success/failure)
     */
    TERMINATE_CASE("terminate_case", "案例终止", ActionCategory.SYSTEM),

    /**
     * 变量变更事件
     * params: var_name, old_value, new_value
     */
    VARIABLE_CHANGE("variable_change", "变量变更", ActionCategory.SYSTEM),

    /**
     * 状态迁移事件
     * params: from_state, to_state, event, timestamp
     */
    STATE_TRANSITION("state_transition", "状态迁移", ActionCategory.SYSTEM),

    /**
     * 数据持久化
     * params: entity_type, entity_id, operation(save/update/delete)
     */
    PERSISTENCE("persistence", "数据持久化", ActionCategory.SYSTEM);

    // ==================== 字段 ====================

    /** 动作类型编码（保持兼容性，与之前的字符串一致） */
    private final String code;

    /** 中文描述 */
    private final String description;

    /** 所属类别 */
    private final ActionCategory category;

    // ==================== 构造器 ====================

    ActionType(String code, String description, ActionCategory category) {
        this.code = code;
        this.description = description;
        this.category = category;
    }

    // ==================== Getter ====================

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public ActionCategory getCategory() {
        return category;
    }

    // ==================== 便捷判断方法 ====================

    /**
     * 是否为模拟器动作
     */
    public boolean isSimulatorAction() {
        return category == ActionCategory.SIMULATOR;
    }

    /**
     * 是否为系统动作
     */
    public boolean isSystemAction() {
        return category == ActionCategory.SYSTEM;
    }

    // ==================== 静态查找方法 ====================

    /**
     * 根据代码查找枚举
     */
    public static ActionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ActionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断是否为模拟器动作（静态版本）
     */
    public static boolean isSimulatorAction(String code) {
        ActionType type = fromCode(code);
        return type != null && type.isSimulatorAction();
    }

    /**
     * 判断是否为系统动作（静态版本）
     */
    public static boolean isSystemAction(String code) {
        ActionType type = fromCode(code);
        return type != null && type.isSystemAction();
    }

    @Override
    public String toString() {
        return code;
    }
}

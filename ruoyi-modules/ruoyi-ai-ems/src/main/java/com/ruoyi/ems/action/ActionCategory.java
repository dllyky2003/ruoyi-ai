package com.ruoyi.ems.action;

/**
 * 动作类别枚举
 *
 * 将系统动作分为两大类，便于分类处理、监控和日志分析：
 * 1. SIMULATOR：模拟器动作 → 需调用模拟器执行，改变患者生理状态，有副作用
 * 2. SYSTEM：系统动作 → 由系统自身执行，不改变患者状态，无生理副作用
 *
 * @author RuoYi EMS
 * @version 1.0
 */
public enum ActionCategory {

    /**
     * 模拟器动作
     *
     * 需要模拟器执行的生理模拟指令，会改变患者状态：
     * - 药物推注、电复律、除颤
     * - 心律/生命体征变更
     * - CPR、通气、迷走刺激
     *
     * 特征：
     * - 执行后有生理反馈（模拟器回调）
     * - 可能产生延迟（需等待模拟器处理）
     * - 通常需要记录执行结果
     * - 建议异步执行
     */
    SIMULATOR("模拟器动作", true),

    /**
     * 系统动作
     *
     * 由系统自身执行的非生理指令，不改变患者状态：
     * - 日志记录、通知推送
     * - 计时器操作、评分更新
     * - 状态持久化、WebSocket推送
     * - 案例终止
     *
     * 特征：
     * - 执行后无生理反馈
     * - 执行时间短
     * - 通常同步执行即可
     * - 失败不影响患者状态
     */
    SYSTEM("系统动作", false);

    // ==================== 字段 ====================

    /** 中文描述 */
    private final String description;

    /** 是否影响患者生理状态 */
    private final boolean physiological;

    // ==================== 构造器 ====================

    ActionCategory(String description, boolean physiological) {
        this.description = description;
        this.physiological = physiological;
    }

    // ==================== Getter ====================

    public String getDescription() {
        return description;
    }

    public boolean isPhysiological() {
        return physiological;
    }

    // ==================== 便捷方法 ====================

    /**
     * 从 ActionType 获取类别（替代静态方法，更符合枚举设计）
     */
    public static ActionCategory fromActionType(ActionType actionType) {
        if (actionType == null) {
            return null;
        }
        return actionType.getCategory();
    }

    /**
     * 从动作类型编码获取类别
     */
    public static ActionCategory fromCode(String code) {
        ActionType type = ActionType.fromCode(code);
        return type != null ? type.getCategory() : null;
    }

    /**
     * 判断是否为模拟器动作（编码版）
     */
    public static boolean isSimulator(String code) {
        ActionCategory category = fromCode(code);
        return category == SIMULATOR;
    }

    /**
     * 判断是否为系统动作（编码版）
     */
    public static boolean isSystem(String code) {
        ActionCategory category = fromCode(code);
        return category == SYSTEM;
    }

    @Override
    public String toString() {
        return name() + "(" + description + ")";
    }
}

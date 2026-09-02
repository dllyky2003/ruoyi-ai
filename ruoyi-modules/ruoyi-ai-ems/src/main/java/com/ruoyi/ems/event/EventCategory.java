package com.ruoyi.ems.event;


import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 事件分类枚举
 *
 * 按事件来源对 CaseEvent 进行分组，便于：
 * 1. 事件路由：不同分类的事件由不同的监听器处理
 * 2. 监控统计：按分类统计事件发生频率
 * 3. 权限控制：不同分类的事件可配置不同的处理策略
 * 4. UI 展示：不同分类在日志/前端使用不同颜色标识
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Getter
public enum EventCategory {

    /**
     * 用户意图事件（S类）
     *
     * 由学员指令驱动，触发状态机转移。
     * 这些事件是案例流程的核心驱动力。
     *
     * 示例：S1_1_NARROW, S2_2_DONE, S3_1_WORSENED
     * 特征：触发状态转移，需要实时处理，影响流程走向
     */
    USER("USER", "用户意图",
        "#1A73E8",          // 蓝色
        true,               // 触发状态转移
        false,              // 不改变患者生理
        false),             // 不是内部事件

    /**
     * 全局意图事件（G类）
     *
     * 由学员通用指令驱动，不改变状态机主流程状态。
     * 这些事件用于记录、评分和辅助决策。
     *
     * 示例：G4-1 血压报告, G1-1 安全清场, G2-3 时间记录
     * 特征：不触发状态转移，异步处理，用于评分/日志
     */
    GLOBAL("GLOBAL", "全局事件",
        "#E37400",          // 橙色
        false,              // 不触发状态转移
        false,              // 不改变患者生理
        false),             // 不是内部事件

    /**
     * 系统事件（SYS类）
     *
     * 由超时、错误、自动流程等系统内部触发。
     * 这些事件通常用于流程控制和安全兜底。
     *
     * 示例：SYS_TIMEOUT, SYS_DEFAULT_TRANSITION, SYS_TERMINATE
     * 特征：系统内部触发，可能触发状态转移，通常有默认处理逻辑
     */
    SYSTEM("SYSTEM", "系统驱动",
        "#9334E6",          // 紫色
        true,               // 可触发状态转移
        false,              // 不改变患者生理
        true),              // 是内部事件

    /**
     * 模拟器事件（SIM类）
     *
     * 由模拟器回调触发，反映患者生理状态变化。
     * 这些事件是客观事实的反馈。
     *
     * 示例：SIM_RHYTHM_CHANGED, SIM_VITAL_UPDATED, SIM_DRUG_EFFECT
     * 特征：模拟器驱动，可触发状态转移（自动流转），反映客观事实
     */
    SIMULATOR("SIMULATOR", "模拟器驱动",
        "#0D904F",          // 绿色
        true,               // 可触发状态转移
        true,               // 改变患者生理
        true),              // 是内部事件

    /**
     * 状态生命周期事件（STATE类）
     *
     * 状态进入/退出时由状态机自身触发，用于内部监听。
     * 这些事件通常不对外暴露，用于系统内部日志和监控。
     *
     * 示例：STATE_ENTRY, STATE_EXIT, STATE_ERROR
     * 特征：内部使用，不对外暴露，用于日志/监控
     */
    STATE_LIFECYCLE("STATE_LIFECYCLE", "状态生命周期",
        "#757575",          // 灰色
        false,              // 不触发状态转移（是状态转移的结果）
        false,              // 不改变患者生理
        true);              // 是内部事件

    // ==================== 字段 ====================

    /** 分类编码 */
    private final String code;

    /** 中文描述 */
    private final String description;

    /** CSS 颜色（用于前端展示） */
    private final String color;

    /** 是否触发状态机转移 */
    private final boolean triggersTransition;

    /** 是否影响患者生理状态 */
    private final boolean isPhysiological;

    /** 是否为系统内部事件（不对外暴露） */
    private final boolean isInternal;

    // ==================== 构造器 ====================

    EventCategory(String code, String description, String color,
                  boolean triggersTransition, boolean isPhysiological, boolean isInternal) {
        this.code = code;
        this.description = description;
        this.color = color;
        this.triggersTransition = triggersTransition;
        this.isPhysiological = isPhysiological;
        this.isInternal = isInternal;
    }

    // ==================== 静态查找 ====================

    private static final Map<String, EventCategory> CODE_MAP = new HashMap<>();

    static {
        for (EventCategory category : values()) {
            CODE_MAP.put(category.code, category);
        }
    }

    public static EventCategory fromCode(String code) {
        return CODE_MAP.get(code);
    }

    public static EventCategory fromCodeOrDefault(String code, EventCategory defaultCategory) {
        EventCategory category = fromCode(code);
        return category != null ? category : defaultCategory;
    }

    // ==================== 便捷判断 ====================

    public boolean isUserDriven() {
        return this == USER || this == GLOBAL;
    }

    public boolean isSystemDriven() {
        return this == SYSTEM || this == STATE_LIFECYCLE;
    }

    public boolean isSimulatorDriven() {
        return this == SIMULATOR;
    }

    public boolean isExternal() {
        return !isInternal;
    }

    // ==================== 颜色方法（供前端使用） ====================

    /**
     * 获取不带 # 的颜色值（用于 CSS 变量）
     */
    public String getColorWithoutHash() {
        return color != null ? color.replace("#", "") : null;
    }

    @Override
    public String toString() {
        return code;
    }
}

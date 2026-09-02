package com.ruoyi.ems.config;


import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态类型枚举
 *
 * 定义 CaseState 的语义分类，帮助系统理解每个状态的角色。
 *
 * 使用场景：
 * - UI 展示：不同状态类型使用不同颜色/图标
 * - 超时管理：TIMEOUT 类型状态触发超时逻辑
 * - 错误处理：ERROR 类型状态触发错误流程
 * - 流程控制：INITIAL/END 类型决定状态机边界
 * - 子状态管理：SUB_STATE 支持嵌套状态
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Getter
public enum StateType {

    /**
     * 普通状态
     *
     * 一般的流程状态，可正常进入和退出。
     * 大部分业务状态属于此类型。
     *
     * 示例：DO_ADENOSINE_6MG, CHECK_QRS_WIDTH
     */
    NORMAL("NORMAL", "普通", false, false, false),

    /**
     * 初始状态
     *
     * 状态机启动时的第一个状态。
     * 每个状态机有且仅有一个初始状态。
     *
     * 示例：INITIAL_SETUP
     */
    INITIAL("INITIAL", "初始", false, true, false),

    /**
     * 终止状态
     *
     * 状态机结束状态，进入后不可再转移。
     * 可以有多个终止状态，代表不同的结束结果。
     *
     * 示例：MONITOR_SINUS_RHYTHM, TERMINAL_END
     */
    END("END", "终止", true, false, false),

    /**
     * 错误状态
     *
     * 用于处理异常情况，如指令拦截、超时、系统错误等。
     * 进入错误状态通常意味着需要学员纠偏或系统介入。
     *
     * 示例：ERROR_HANDLING
     */
    ERROR("ERROR", "错误", false, false, false),

    /**
     * 超时状态
     *
     * 当在某个状态停留超过 timeoutSeconds 时，自动进入的中间状态。
     * 超时状态通常用于提醒学员或自动推进流程。
     *
     * 示例：TIMEOUT_HANDLING
     */
    TIMEOUT("TIMEOUT", "超时", false, false, true),

    /**
     * 选择状态
     *
     * 需要学员做出选择（如是否继续治疗、选择哪种药物）。
     * 选择状态通常有多个出口转移。
     *
     * 示例：EXPERT_CONSULTATION（会诊后选择方向）
     */
    CHOICE("CHOICE", "选择", false, false, false),

    /**
     * 子状态
     *
     * 嵌套状态，用于表示复杂流程中的内部状态。
     * 子状态在父状态内部执行，不对外暴露。
     *
     * 示例：电复律流程内部的 "CHARGING", "DISCHARGING"
     */
    SUB_STATE("SUB_STATE", "子状态", false, false, false);

    // ==================== 字段 ====================

    /** 类型编码 */
    private final String code;

    /** 中文描述 */
    private final String description;

    /** 是否为终止类型（END） */
    private final boolean terminal;

    /** 是否为初始类型（INITIAL） */
    private final boolean initial;

    /** 是否为临时类型（TIMEOUT 或 CHOICE，状态机不持久化） */
    private final boolean transientState;

    // ==================== 构造器 ====================

    StateType(String code, String description, boolean terminal, boolean initial, boolean transientState) {
        this.code = code;
        this.description = description;
        this.terminal = terminal;
        this.initial = initial;
        this.transientState = transientState;
    }

    // ==================== 静态查找 ====================

    private static final Map<String, StateType> CODE_MAP = new HashMap<>();

    static {
        for (StateType type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    /**
     * 根据编码查找
     */
    public static StateType fromCode(String code) {
        return CODE_MAP.get(code);
    }

    /**
     * 根据编码查找，若不存在则返回默认值
     */
    public static StateType fromCodeOrDefault(String code, StateType defaultType) {
        StateType type = fromCode(code);
        return type != null ? type : defaultType;
    }

    /**
     * 根据描述查找
     */
    public static StateType fromDescription(String description) {
        for (StateType type : values()) {
            if (type.description.equals(description)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断是否为终止类型
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 判断是否为初始类型
     */
    public boolean isInitial() {
        return initial;
    }

    /**
     * 判断是否为临时类型（状态机不持久化）
     */
    public boolean isTransient() {
        return transientState;
    }

    /**
     * 判断是否为错误状态
     */
    public boolean isError() {
        return this == ERROR;
    }

    /**
     * 判断是否为选择状态
     */
    public boolean isChoice() {
        return this == CHOICE;
    }

    /**
     * 判断是否为子状态
     */
    public boolean isSubState() {
        return this == SUB_STATE;
    }

    /**
     * 判断是否为有效流程状态（非临时、非错误、非子状态）
     */
    public boolean isEffectiveState() {
        return !transientState && this != ERROR && this != SUB_STATE;
    }

    // ==================== 转换方法 ====================

    /**
     * 从 YAML 字符串解析
     * 支持大小写不敏感
     */
    public static StateType parse(String value) {
        if (value == null) {
            return NORMAL;
        }
        String upper = value.toUpperCase().trim();
        try {
            return StateType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            // 尝试从 code 匹配
            StateType fromCode = fromCode(upper);
            if (fromCode != null) {
                return fromCode;
            }
            return NORMAL;
        }
    }

    @Override
    public String toString() {
        return code;
    }
}

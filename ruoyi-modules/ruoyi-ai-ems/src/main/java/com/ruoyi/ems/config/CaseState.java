package com.ruoyi.ems.config;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 案例状态定义
 *
 * 封装状态机中单个状态的完整元数据，包括：
 * - 状态标识（ID）
 * - 显示名称
 * - 状态类型（普通/初始/终止/错误/超时）
 * - 进入/退出动作
 * - 超时配置
 * - 显示顺序
 *
 * 与 Spring StateMachine 的 State 的关系：
 * - Spring StateMachine 使用 String 作为状态 ID
 * - CaseState 是业务层的状态元数据，用于构建 StateMachine 时的配置
 * - 运行时状态机只使用 String ID，不持有 CaseState 对象
 *
 * 设计原则：
 * 1. 状态定义与状态机实例分离（元数据 vs 运行时）
 * 2. 支持 YAML 配置驱动
 * 3. 支持进入/退出动作的 SpEL 表达式
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseState implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 核心标识 ====================

    /**
     * 状态编码（唯一标识）
     * 如: "INITIAL_SETUP", "DO_ADENOSINE_6MG"
     * 对应 Spring StateMachine 中的 State ID
     */
    private String code;

    /**
     * 状态显示名称（用于 UI 和日志）
     * 如: "初始准备", "腺苷6mg推注"
     */
    private String name;

    /**
     * 状态描述（详细说明）
     * 如: "团队到达，设备与通路准备"
     */
    private String description;

    // ==================== 状态类型 ====================

    /**
     * 状态类型
     * 详见 {@link StateType}
     */
    @Builder.Default
    private StateType type = StateType.NORMAL;

    /**
     * 是否为初始状态
     * 等同于 type == StateType.INITIAL
     */
    public boolean isInitial() {
        return type == StateType.INITIAL;
    }

    /**
     * 是否为终止状态
     * 等同于 type == StateType.END
     */
    public boolean isEnd() {
        return type == StateType.END;
    }

    /**
     * 是否为错误状态
     * 等同于 type == StateType.ERROR
     */
    public boolean isError() {
        return type == StateType.ERROR;
    }

    // ==================== 进入/退出动作 ====================

    /**
     * 进入状态时执行的动作（SpEL 表达式或 Bean 名称）
     * 如: "#utils.logEntry('INITIAL_SETUP')"
     */
    private String onEntryAction;

    /**
     * 退出状态时执行的动作（SpEL 表达式或 Bean 名称）
     * 如: "#utils.logExit('INITIAL_SETUP')"
     */
    private String onExitAction;

    /**
     * 状态内部活动（持续执行，如计时器）
     * 如: "#timer.start('DO_ADENOSINE_6MG', 60)"
     */
    private String onDoAction;

    // ==================== 超时配置 ====================

    /**
     * 超时时间（秒）
     * 在此状态停留超过此时间，触发超时事件
     */
    private Integer timeoutSeconds;

    /**
     * 超时后触发的系统事件
     * 如: "SYS_TIMEOUT_VAGAL"
     */
    private String timeoutEvent;

    /**
     * 超时后执行的动作（SpEL 表达式或 Bean 名称）
     */
    private String timeoutAction;

    // ==================== 显示与顺序 ====================

    /**
     * 显示顺序（用于 UI 展示）
     */
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * CSS 样式类（用于前端高亮）
     */
    private String styleClass;

    /**
     * 图标（用于前端展示）
     */
    private String icon;

    // ==================== 分组与标签 ====================

    /**
     * 状态所属分组（用于 UI 分组展示）
     * 如: "评估", "治疗", "处置"
     */
    private String group;

    /**
     * 状态标签（用于搜索和过滤）
     */
    private String tags;

    // ==================== 权限与可见性 ====================

    /**
     * 是否对学员可见（某些状态用于内部流转，不对学员展示）
     */
    @Builder.Default
    private boolean visible = true;

    /**
     * 是否可回退（学员可从此状态回退到前一状态）
     */
    @Builder.Default
    private boolean allowRollback = false;

    // ==================== 便捷判断 ====================

    /**
     * 是否有超时配置
     */
    public boolean hasTimeout() {
        return timeoutSeconds != null && timeoutSeconds > 0;
    }

    /**
     * 是否有进入动作
     */
    public boolean hasOnEntryAction() {
        return onEntryAction != null && !onEntryAction.isEmpty();
    }

    /**
     * 是否有退出动作
     */
    public boolean hasOnExitAction() {
        return onExitAction != null && !onExitAction.isEmpty();
    }

    /**
     * 获取显示名称（若 name 为空则返回 code）
     */
    public String getDisplayName() {
        return name != null ? name : code;
    }

    @Override
    public String toString() {
        return String.format("CaseState{code='%s', type=%s, name='%s'}", code, type, name);
    }
}

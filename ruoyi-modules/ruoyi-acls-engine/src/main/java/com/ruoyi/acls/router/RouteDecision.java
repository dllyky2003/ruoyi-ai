package com.ruoyi.acls.router;

import com.ruoyi.acls.engine.CommandResult;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 路由决策结果
 * 由 ActTypeRouter 或 SemanticRouter 返回，指导引擎执行何种操作
 */
@Data
@Builder
public class RouteDecision {

    /**
     * 动作类型枚举
     */
    public enum ActionType {
        /** 执行技能（完整链路） */
        EXECUTE_SKILL,
        /** 查询回答（只读，NPC 口头回答） */
        QUERY_RESPOND,
        /** 状态更新（宣告，不执行动作） */
        STATE_UPDATE,
        /** 任务取消（中断当前动作） */
        TASK_CANCEL,
        /** 确认匹配（闭环确认） */
        CONFIRM_ACK,
        /** 状态跳转（纠偏/否决） */
        OVERRIDE_JUMP,
        /** 被安全护栏拦截 */
        BLOCKED,
        /** 需要向用户澄清 */
        CLARIFY,
        /** 无动作（忽略） */
        NONE
    }

    private final ActionType actionType;

    /**
     * 仅当 actionType = EXECUTE_SKILL 时有效
     */
    private final String skillId;

    /**
     * 执行参数（标准化后的 slots）
     */
    private final Map<String, Object> params;

    /**
     * 返回给组长的 TTS 语音文本
     */
    private final String responseText;

    /**
     * 需要更新的上下文变量
     */
    private final Map<String, Object> contextUpdates;

    /**
     * 是否需要等待确认
     */
    private final boolean requiresAck;

    /**
     * 澄清追问内容（仅 CLARIFY 时有效）
     */
    private final String clarifyQuestion;

    // ========== 静态工厂方法 ==========

    public static RouteDecision executeSkill(String skillId, Map<String, Object> params) {
        return RouteDecision.builder()
            .actionType(ActionType.EXECUTE_SKILL)
            .skillId(skillId)
            .params(params)
            .build();
    }

    public static RouteDecision executeSkill(String skillId, Map<String, Object> params, String responseText) {
        return RouteDecision.builder()
            .actionType(ActionType.EXECUTE_SKILL)
            .skillId(skillId)
            .params(params)
            .responseText(responseText)
            .build();
    }

    public static RouteDecision queryRespond(String responseText) {
        return RouteDecision.builder()
            .actionType(ActionType.QUERY_RESPOND)
            .responseText(responseText)
            .build();
    }

    public static RouteDecision stateUpdate(Map<String, Object> contextUpdates, String responseText) {
        return RouteDecision.builder()
            .actionType(ActionType.STATE_UPDATE)
            .contextUpdates(contextUpdates)
            .responseText(responseText)
            .build();
    }

    public static RouteDecision taskCancel(String responseText) {
        return RouteDecision.builder()
            .actionType(ActionType.TASK_CANCEL)
            .responseText(responseText)
            .build();
    }

    public static RouteDecision confirmAck(String responseText) {
        return RouteDecision.builder()
            .actionType(ActionType.CONFIRM_ACK)
            .responseText(responseText)
            .build();
    }

    public static RouteDecision overrideJump(Map<String, Object> contextUpdates, String responseText) {
        return RouteDecision.builder()
            .actionType(ActionType.OVERRIDE_JUMP)
            .contextUpdates(contextUpdates)
            .responseText(responseText)
            .build();
    }

    public static RouteDecision blocked(String responseText) {
        return RouteDecision.builder()
            .actionType(ActionType.BLOCKED)
            .responseText(responseText)
            .build();
    }

    public static RouteDecision clarify(String question) {
        return RouteDecision.builder()
            .actionType(ActionType.CLARIFY)
            .clarifyQuestion(question)
            .responseText(question)
            .build();
    }

    public static RouteDecision none() {
        return RouteDecision.builder()
            .actionType(ActionType.NONE)
            .build();
    }

    // ========== 辅助方法 ==========

    public boolean isExecutable() {
        return actionType == ActionType.EXECUTE_SKILL;
    }

    public boolean isBlocked() {
        return actionType == ActionType.BLOCKED;
    }

    public boolean isClarify() {
        return actionType == ActionType.CLARIFY;
    }
}

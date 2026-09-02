package com.ruoyi.ems.domain;

import com.ruoyi.ems.action.SystemAction;
import com.ruoyi.ems.executor.IntentExecutor;
import com.ruoyi.ems.npc.TeamMember;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 意图执行结果
 */
@Data
@Builder
public class ExecutionResult {

    /** 是否成功 */
    private boolean success;

    /** 是否被拦截（如S4-4） */
    private boolean blocked;

    /** 拦截原因 */
    private String blockReason;

    /** 是否需要驱动状态机 */
    private boolean requiresStateTransition;

    /** 状态机事件码（如果需要驱动状态机） */
    private String eventCode;

    /** 系统动作列表 */
    private List<SystemAction> systemActions;

    /** 给学员的自然语言回复 */
    private String responseText;

    /** 给特定角色的指令（角色名 → 指令） */
    private Map<String, String> roleInstructions;

    /** 错误信息 */
    private String errorMessage;

    /** 变量更新列表 */
    private Map<String, Object> variableUpdates;

    /** 分配了任务的成员（如果有） */
    private TeamMember assignedMember;

    /** 执行器类型 */
    private IntentExecutor.ExecutorType executorType;

    // ==================== 静态工厂方法 ====================

    public static ExecutionResult success(String response) {
        return ExecutionResult.builder()
            .success(true)
            .responseText(response)
            .build();
    }

    public static ExecutionResult successWithTransition(String response, String eventCode) {
        return ExecutionResult.builder()
            .success(true)
            .requiresStateTransition(true)
            .eventCode(eventCode)
            .responseText(response)
            .build();
    }

    public static ExecutionResult successWithAction(String response, List<SystemAction> actions) {
        return ExecutionResult.builder()
            .success(true)
            .responseText(response)
            .systemActions(actions)
            .build();
    }

    public static ExecutionResult successWithTeam(String response, TeamMember member) {
        return ExecutionResult.builder()
            .success(true)
            .responseText(response)
            .assignedMember(member)
            .build();
    }

    public static ExecutionResult rejected(String reason) {
        return ExecutionResult.builder()
            .success(false)
            .errorMessage(reason)
            .build();
    }

    public static ExecutionResult blocked(String reason) {
        return ExecutionResult.builder()
            .success(false)
            .blocked(true)
            .blockReason(reason)
            .build();
    }

    /**
     * 将执行结果格式化为可读字符串，用于日志输出
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ExecutionResult {");
        sb.append("success=").append(success);
        sb.append(", blocked=").append(blocked);
        if (blocked) {
            sb.append(", blockReason='").append(blockReason).append('\'');
        }
        sb.append(", responseText='").append(responseText).append('\'');
        if (errorMessage != null) {
            sb.append(", errorMessage='").append(errorMessage).append('\'');
        }
        if (eventCode != null) {
            sb.append(", eventCode='").append(eventCode).append('\'');
        }
        if (systemActions != null && !systemActions.isEmpty()) {
            sb.append(", systemActions=").append(systemActions.size()).append(" actions");
        }
        if (assignedMember != null) {
            sb.append(", assignedMember=").append(assignedMember.getName());
        }
        sb.append('}');
        return sb.toString();
    }
}

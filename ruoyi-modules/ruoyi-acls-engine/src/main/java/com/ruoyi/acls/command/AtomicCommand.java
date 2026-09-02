package com.ruoyi.acls.command;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.member.VirtualTeamMember;
import com.ruoyi.acls.npc.Duty;  // 复用你现有的 Duty
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 原子动作命令 —— 对应 JSON 中的 refAtomic
 * 包装对 VirtualTeamMember 的异步调用
 */
@Slf4j
@RequiredArgsConstructor
public class AtomicCommand implements ICommand {

    private final String commandId;
    private final Duty duty;
    private final String refAtomic;
    private final Map<String, Object> params;
    private final int durationSeconds;
    private final long timeoutMillis;

    @Override
    public String getCommandId() {
        return commandId;
    }

    @Override
    public long getTimeoutMillis() {
        return timeoutMillis > 0 ? timeoutMillis : (durationSeconds * 1000L + 5000);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String sessionId = context.getSessionId();
        String taskDesc = buildTaskDescription();

        // 1. 从 TeamManager 获取或创建虚拟组员（委托给上下文）
        VirtualTeamMember member = context.getTeamManager()
            .getOrCreateVirtualMember(sessionId, duty);

        if (member == null || !member.isAvailable()) {
            return CommandResult.failure("无可用成员执行职责: " + duty.getDisplayName());
        }

        // 2. 异步执行任务
        CompletableFuture<Boolean> future = member.executeTaskAsync(
            taskDesc,
            durationSeconds,
            context.getCaseContext()
        );

        // 3. 阻塞等待完成（带超时）
        try {
            Boolean success = future.get(getTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(success)) {
                return CommandResult.success(member.getFullName() + " 已完成: " + taskDesc);
            } else {
                return CommandResult.failure(member.getFullName() + " 任务执行失败");
            }
        } catch (java.util.concurrent.TimeoutException e) {
            member.cancelCurrentTask("超时取消");
            return CommandResult.failure("任务超时: " + taskDesc);
        } catch (Exception e) {
            log.error("原子命令执行异常", e);
            return CommandResult.failure("执行异常: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupted(CommandContext context) {
        // 由调度器调用，取消正在执行的组员任务
        String sessionId = context.getSessionId();
        VirtualTeamMember member = context.getTeamManager()
            .getVirtualMember(sessionId, duty);
        if (member != null) {
            member.cancelCurrentTask("被上级命令中断");
        }
    }

    private String buildTaskDescription() {
        StringBuilder sb = new StringBuilder(refAtomic);
        if (params != null && !params.isEmpty()) {
            sb.append(" ").append(params);
        }
        return sb.toString();
    }
}

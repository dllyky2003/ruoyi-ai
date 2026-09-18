package com.ruoyi.acls.router;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.TeamMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NpcSkillExecutor {

    private final TeamManager teamManager;
    private final ApplicationEventPublisher eventPublisher;   // ★ 新增

    public CommandResult execute(TeamMember member, EmsSkill skill,
                                 CommandContext context, Map<String, Object> slots) {

        String skillId = skill.getKeystepId();

        boolean started = member.startTask(skillId, 5);
        if (!started) {
            return CommandResult.failure(
                member.getName() + " 正在执行 " + member.getCurrentTask() +
                    "，无法同时执行 " + skillId
            );
        }

        try {
            CommandResult validation = skill.validateParams(context, slots);
            if (!validation.isSuccess()) {
                member.failTask(validation.getErrorMessage());
                return validation;
            }

            CommandResult result = skill.execute(context, slots, member);

            if (result.isSuccess()) {
                completeAndNotify(context.getSessionId(), member);   // ★ 改这一行
            } else {
                member.failTask(result.getErrorMessage());
            }

            return result;

        } catch (Exception e) {
            member.failTask(e.getMessage());
            log.error("💥 {} 执行 {} 异常", member.getName(), skillId, e);
            return CommandResult.failure("执行异常: " + e.getMessage());

        } finally {
            releaseMemberSafely(member, skillId);
        }
    }

    public CommandResult forceExecute(TeamMember member, EmsSkill skill,
                                      CommandContext context, Map<String, Object> slots,
                                      String interruptReason) {

        String skillId = skill.getKeystepId();

        member.forceStartTask(skillId, 5, interruptReason);

        try {
            CommandResult validation = skill.validateParams(context, slots);
            if (!validation.isSuccess()) {
                member.failTask(validation.getErrorMessage());
                return validation;
            }

            CommandResult result = skill.execute(context, slots, member);

            if (result.isSuccess()) {
                completeAndNotify(context.getSessionId(), member);   // ★ 改这一行
            } else {
                member.failTask(result.getErrorMessage());
            }

            return result;

        } catch (Exception e) {
            member.failTask(e.getMessage());
            log.error("💥 {} 强制执行 {} 异常", member.getName(), skillId, e);
            return CommandResult.failure("执行异常: " + e.getMessage());

        } finally {
            releaseMemberSafely(member, skillId);
        }
    }

    // ====================================================================
    // ★ 新增：完成任务 + 发布 TaskCompletedEvent
    // ====================================================================
    /**
     * 把"member.completeTask()"+"发布 TaskCompletedEvent"合并成一步。
     * 顺序：
     *   1. member.completeTask()  —— 设状态、打"✅ xxx 完成任务"日志
     *   2. 生成报告
     *   3. 发布 TaskCompletedEvent —— 触发 SkillCompletionStateDriver
     */
    private void completeAndNotify(String sessionId, TeamMember member) {
        if (member == null || sessionId == null) {
            log.warn("completeAndNotify: sessionId 或 member 为 null，跳过");
            return;
        }
        try {
            member.completeTask();
            String report = teamManager.generateReport(member);
            TaskCompletedEvent event = new TaskCompletedEvent(sessionId, member, true, report);
            eventPublisher.publishEvent(event);
            log.info("📢 技能完成事件已发布: session={}, member={}, task={}",
                sessionId, member.getName(), member.getCurrentTask());
        } catch (Exception e) {
            log.warn("发布 TaskCompletedEvent 失败: session={}, err={}",
                sessionId, e.getMessage());
        }
    }

    public CommandResult cancelCurrentTask(TeamMember member, String reason) {
        if (member == null || member.isIdle()) {
            return CommandResult.failure("没有正在执行的任务");
        }
        member.cancelTask(reason);
        return CommandResult.success(member.getName() + " 的任务已取消");
    }

    private void releaseMemberSafely(TeamMember member, String skillId) {
        if (member == null) return;
        try {
            member.resetToIdle();
            if (log.isDebugEnabled()) {
                log.debug("♻️ 成员已归还空闲池: {}, lastSkill={}", member.getName(), skillId);
            }
        } catch (Exception ex) {
            log.error("⚠️ 释放成员失败: member={}, skill={}, err={}",
                member.getName(), skillId, ex.getMessage());
        }
    }
}

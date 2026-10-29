package com.ruoyi.acls.router;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.npc.NpcSpeakerResolver;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.TeamMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ★ 2026-09-23 修改（配合 NpcSpeakerResolver 兜底）：
 * 在 skill.execute() 之前把"当前执行者"写入 NpcSpeakerResolver 的缓存，
 * 让技能内部生成 npc_dialogue / npc_action 时能解析到正确的 npcId。
 *
 * ★ 方案 A 修改：
 * 发布 TaskCompletedEvent 时把当前 intentCode 一并带出，
 * 供 SkillCompletionStateDriver 在"状态机无纯条件出边"时用 intent 重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NpcSkillExecutor {

    private final TeamManager teamManager;
    private final ApplicationEventPublisher eventPublisher;
    private final NpcSpeakerResolver npcSpeakerResolver;

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

        npcSpeakerResolver.setCurrentExecutor(context.getSessionId(), member.getId());

        try {
            CommandResult validation = skill.validateParams(context, slots);
            if (!validation.isSuccess()) {
                member.failTask(validation.getErrorMessage());
                return validation;
            }

            CommandResult result = skill.execute(context, slots, member);

            if (result.isSuccess()) {
                completeAndNotify(context, member);   // ★ 改：传 context
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

        npcSpeakerResolver.setCurrentExecutor(context.getSessionId(), member.getId());

        try {
            CommandResult validation = skill.validateParams(context, slots);
            if (!validation.isSuccess()) {
                member.failTask(validation.getErrorMessage());
                return validation;
            }

            CommandResult result = skill.execute(context, slots, member);

            if (result.isSuccess()) {
                completeAndNotify(context, member);   // ★ 改：传 context
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
    // ★ 完成任务 + 发布 TaskCompletedEvent
    // ====================================================================
    /**
     * ★ 方案 A：改为接收 CommandContext，从 context 里取 intentCode 填入事件。
     */
    private void completeAndNotify(CommandContext context, TeamMember member) {
        if (member == null || context == null || context.getSessionId() == null) {
            log.warn("completeAndNotify: context 或 member 为 null，跳过");
            return;
        }
        String sessionId = context.getSessionId();
        try {
            member.completeTask();
            String report = teamManager.generateReport(member);

            // ★ 方案 A：从 context 取 intentCode
            String intentCode = null;
            Object ic = context.getVariable("intentCode");
            if (ic != null) {
                intentCode = String.valueOf(ic);
            }

            TaskCompletedEvent event = new TaskCompletedEvent(
                sessionId, member, true, report, intentCode);
            eventPublisher.publishEvent(event);

            log.info("📢 技能完成事件已发布: session={}, member={}, task={}, intent={}",
                sessionId, member.getName(), member.getCurrentTask(), intentCode);
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

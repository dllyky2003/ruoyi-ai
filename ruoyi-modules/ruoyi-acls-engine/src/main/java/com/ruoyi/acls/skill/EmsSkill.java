package com.ruoyi.acls.skill;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;

import java.util.Map;

/**
 * EgoEMS 67 个关键步骤的统一技能接口
 * 所有急救操作（药物、按压、除颤、心电图等）均实现此接口
 */
public interface EmsSkill {

    /**
     * 技能唯一标识，对应 EgoEMS 的 keystep ID
     */
    String getKeystepId();

    /**
     * 该技能应由哪个职责的 NPC 执行
     */
    Duty getRequiredDuty();

    /**
     * ★ 探查性询问：NPC 用第一人称口头汇报当前状态
     * 示例："黄护士报告：肾上腺素 1mg 已完成推注（3秒前）"
     */
    String getQueryResponse(CommandContext context);

    /**
     * 参数校验（如剂量范围、能量值合法性）
     */
    CommandResult validateParams(CommandContext context, Map<String, Object> slots);

    /**
     * ★ 核心执行方法：由被委派的 NPC 调用
     * @param executor 执行此技能的 NPC 成员
     */
    CommandResult execute(CommandContext context, Map<String, Object> slots, TeamMember executor);

    /**
     * 默认的“宣告”处理（学员主动告知已完成）
     */
    default CommandResult handleDeclaration(CommandContext context, Map<String, Object> slots) {
        context.getCaseContext().setVar(getKeystepId() + "_declared", true);
        return CommandResult.success(getKeystepId() + " 已由组长宣告完成");
    }

    /** 动作类技能完成后需要置位的变量名；查询类技能返回 null */
    default String getSuccessVarName() {
        return null;
    }

    /**
     * 技能成功完成后需要置位的变量集合（逻辑变量名 → 值）。
     * 由 SkillCompletionStateDriver 统一写入 EmsContextManager。
     */
    default Map<String, Object> getSuccessOutputs() {
        return java.util.Collections.emptyMap();
    }
}

package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.constant.AssertKeys;
import com.ruoyi.ems.constant.PhysKeys;
import com.ruoyi.ems.constant.ProcessKeys;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;


@Slf4j
public class AssertQrsWidthSkill extends AbstractEmsSkill {

    public AssertQrsWidthSkill() {
        super(AssertKeys.ASSERT_QRS_WIDTH, Duty.MONITOR_DEFIB);
    }

    @Override
    public String getQueryResponse(CommandContext context) {
        // 探查性询问：返回当前已判读的结果
        String qrsWidth = context.getCaseContext().getVar(AssertKeys.ASSERT_QRS_WIDTH, String.class);
        if (qrsWidth != null) {
            String display = "narrow".equals(qrsWidth) ? "窄QRS" : "宽QRS";
            return "QRS宽度为" + display;
        }
        return "QRS宽度尚未判读，请先完成心电图判读。";
    }

    @Override
    public CommandResult validateParams(CommandContext context, Map<String, Object> slots) {
        // 前置条件：必须已完成 12导联心电图
        Boolean ecgRecorded = context.getCaseContext().getVar(ProcessKeys.PROCESS_ECG_RECORDED, Boolean.class);
        if (ecgRecorded == null || !ecgRecorded) {
            return CommandResult.failure("请先完成 12导联心电图记录，再进行QRS宽度判读！");
        }
        return CommandResult.success("前置条件通过");
    }

    @Override
    public CommandResult execute(CommandContext context, Map<String, Object> slots, TeamMember executor) {
        String sessionId = context.getSessionId();

        // 1. 执行判读：读取 QRS 时限，计算 QRS 宽度分类
        Double qrsDuration = context.getCaseContext().getVar(PhysKeys.PHYS_QRS_DURATION, Double.class);
        String qrsWidth = (qrsDuration != null && qrsDuration <= 0.12) ? "narrow" : "wide";

        // 2. ★ 写入判读结果
        context.getCaseContext().setVar(AssertKeys.ASSERT_QRS_WIDTH, qrsWidth, "SYSTEM");
        context.getCaseContext().setVar(ProcessKeys.PROCESS_QRS_WIDTH_CHECKED, true, "SYSTEM");

        // 3. NPC 口头汇报
        String display = "narrow".equals(qrsWidth) ? "窄QRS" : "宽QRS";
        String durationStr = (qrsDuration != null) ? String.format("%.2f", qrsDuration) : "未知";
        String report = executor.getName() + "报告：QRS宽度 " + durationStr + "秒，为" + display;
        context.getVrClientService().sendSpeech(sessionId, report);

        log.info("📐 QRS宽度判读完成: {} ({}秒)", qrsWidth, qrsDuration);
        return CommandResult.success("判读完成");
    }
}

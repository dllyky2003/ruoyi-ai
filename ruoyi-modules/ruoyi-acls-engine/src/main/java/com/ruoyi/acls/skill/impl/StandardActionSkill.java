package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class StandardActionSkill extends AbstractEmsSkill {

    private final String vrActionCode;
    private final int durationSeconds;
    private final String successVarName;
    private final String successSpeech;

    public StandardActionSkill(String keystepId, Duty requiredDuty,
                               String vrActionCode, int durationSeconds,
                               String successVarName, String successSpeech) {
        super(keystepId, requiredDuty);
        this.vrActionCode = vrActionCode;
        this.durationSeconds = durationSeconds;
        this.successVarName = successVarName;
        this.successSpeech = successSpeech;
    }

    @Override
    public String getQueryResponse(CommandContext context) {
        Boolean done = context.getCaseContext().getVar(successVarName, Boolean.class);
        return (done != null && done) ? successSpeech + "（已完成）" : successSpeech + "（未执行）";
    }

    @Override
    public CommandResult execute(CommandContext context, Map<String, Object> slots, TeamMember executor) {
        String sessionId = context.getSessionId();

        // 1. NPC 口头确认
        context.getVrClientService().sendSpeech(sessionId,
            executor.getName() + "收到，开始执行 " + keystepId);

        // 2. 发 VR 动作（不等待）
        context.getVrClientService().sendAction(
            sessionId, vrActionCode, slots, successSpeech, durationSeconds);

        // 3. ★ 立即返回。不阻塞、不置位、不回调
        log.info("🟡 {} 已启动 (executor={}, duration={}s)",
            keystepId, executor.getName(), durationSeconds);
        return CommandResult.success("已开始执行 " + keystepId);
    }
    @Override
    public Map<String, Object> getSuccessOutputs() {
        return Map.of(successVarName, true);
    }
    /** 供完成监听器读取 successVarName */
    public String getSuccessVarName() {
        return successVarName;
    }

    /** 供完成监听器读取 successSpeech */
    public String getSuccessSpeech() {
        return successSpeech;
    }

}

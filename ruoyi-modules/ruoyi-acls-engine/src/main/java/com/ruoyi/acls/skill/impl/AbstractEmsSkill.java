package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

@Slf4j
public abstract class AbstractEmsSkill implements EmsSkill {

    protected final String keystepId;
    protected final Duty requiredDuty;

    public AbstractEmsSkill(String keystepId, Duty requiredDuty) {
        this.keystepId = keystepId;
        this.requiredDuty = requiredDuty;
    }

    @Override
    public String getKeystepId() {
        return keystepId;
    }

    @Override
    public Duty getRequiredDuty() {
        return requiredDuty;
    }

    @Override
    public CommandResult validateParams(CommandContext context, Map<String, Object> slots) {
        return CommandResult.success("参数校验通过（默认）");
    }

    /**
     * 默认宣告逻辑
     */
    @Override
    public CommandResult handleDeclaration(CommandContext context, Map<String, Object> slots) {
        log.info("📢 [宣告] {} 已由组长宣告完成", keystepId);
        context.getCaseContext().setVar(keystepId + "_declared", true);
        context.getCaseContext().setVar(keystepId + "_declare_time", System.currentTimeMillis());
        return CommandResult.success(keystepId + " 已记录宣告");
    }

    // ==================== 通用复用：写回变量/发语音/渲染 ====================

    protected void setSystemVar(CommandContext context, String varName, Object value) {
        if (varName == null || varName.isBlank()) return;
        context.getCaseContext().setVar(varName, value, "SYSTEM");
    }

    protected void setSystemVars(CommandContext context, Collection<String> varNames, Object value) {
        if (varNames == null) return;
        for (String v : varNames) {
            setSystemVar(context, v, value);
        }
    }

    protected String mapValue(Map<String, String> valueMap, String rawValue) {
        if (rawValue == null) return null;
        if (valueMap == null || valueMap.isEmpty()) return rawValue;
        return valueMap.getOrDefault(rawValue, rawValue);
    }

    /**
     * 渲染 speech 模板：如果模板包含 "{value}" 就替换；否则原样返回。
     */
    protected String renderSpeechTemplate(String template, String displayValue) {
        if (template == null) return "";
        if (template.contains("{value}")) {
            return template.replace("{value}", Objects.toString(displayValue, ""));
        }
        return template;
    }

    /**
     * 统一：executorName + "报告：" + reportBody
     */
    protected void sendReportSpeech(CommandContext context, TeamMember executor, String reportBody) {
        if (executor == null) return;
        String sessionId = context.getSessionId();
        String fullSpeech = executor.getName() + "报告：" + reportBody;
        context.getVrClientService().sendSpeech(sessionId, fullSpeech);
    }

    /**
     * 统一：executorName + "收到，开始执行 " + actionText
     */
    protected void sendConfirmSpeech(CommandContext context, TeamMember executor, String actionText) {
        if (executor == null) return;
        String sessionId = context.getSessionId();
        String confirmSpeech = executor.getName() + "收到，开始执行 " + actionText;
        context.getVrClientService().sendSpeech(sessionId, confirmSpeech);
    }
}

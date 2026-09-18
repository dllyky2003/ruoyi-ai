package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.domain.VariableMeta;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * 纯查询技能：只读取上下文变量并格式化返回，无任何 VR 动作。
 *
 * <p>支持两种调用方式：
 * <ul>
 *   <li>{@link #getQueryResponse(CommandContext)}：只读查询，不写变量、不播报</li>
 *   <li>{@link #execute(CommandContext, Map, TeamMember)}：执行查询 → 通过 NPC 播报结果 → 返回成功</li>
 * </ul>
 *
 * <p>适用于：check_pulse, check_blood_pressure, check_oxygen_saturation 等。
 *
 * <p><b>响应渲染优先级</b>：
 * <ol>
 *   <li>skill 级 {@link #responseTemplate}（最具体）</li>
 *   <li>变量元数据 {@link VariableMeta#renderReadable(Object)}（通用，多个技能共享）</li>
 *   <li>{@link #DEFAULT_TEMPLATE}（最终兜底）</li>
 * </ol>
 */
@Slf4j
public class QuerySkill extends AbstractEmsSkill {

    /** responseTemplate 未配置、变量元数据也没有时的最终兜底 */
    private static final String DEFAULT_TEMPLATE = "已完成查询。";

    private final String targetVarName;
    private final String responseTemplate;
    private final Map<String, String> valueMap;

    /**
     * 基础构造方法（无 valueMap）
     */
    public QuerySkill(String keystepId, Duty requiredDuty, String targetVarName, String responseTemplate) {
        this(keystepId, requiredDuty, targetVarName, responseTemplate, Collections.emptyMap());
    }

    /**
     * 完整构造方法（带 valueMap）
     */
    public QuerySkill(String keystepId, Duty requiredDuty, String targetVarName,
                      String responseTemplate, Map<String, String> valueMap) {
        super(keystepId, requiredDuty);
        this.targetVarName = targetVarName;
        this.responseTemplate = responseTemplate;
        this.valueMap = valueMap != null ? valueMap : Collections.emptyMap();

        if (responseTemplate == null || responseTemplate.isEmpty()) {
            log.debug("QuerySkill [{}] 未配置 responseTemplate，将走变量元数据自动组装", keystepId);
        }
    }

    /**
     * 只读查询：读取目标变量，应用 valueMap 映射，填充模板，返回文本。
     *
     * <p>不写变量、不播报，供外部只读调用。
     */
    @Override
    public String getQueryResponse(CommandContext context) {
        Object value = context.getCaseContext().getVar(targetVarName, Object.class);
        return renderResponse(context, value);
    }

    /**
     * 执行查询：读变量 → 通过 NPC 播报结果 → 返回成功。
     */
    @Override
    public CommandResult execute(CommandContext context, Map<String, Object> slots, TeamMember executor) {
        Object value = context.getCaseContext().getVar(targetVarName, Object.class);
        String reportSpeech = renderResponse(context, value);

        sendReportSpeech(context, executor, reportSpeech);

        log.info("📐 {} 查询完成: {} = {} (播报: {})",
            keystepId, targetVarName, value, reportSpeech);

        return CommandResult.success(reportSpeech);
    }

    /**
     * 统一的响应渲染。
     *
     * <p>优先级：
     * <ol>
     *   <li>skill 级 {@link #responseTemplate}（最具体）</li>
     *   <li>变量元数据 {@link VariableMeta#renderReadable(Object)}（通用）</li>
     *   <li>{@link #DEFAULT_TEMPLATE}（最终兜底）</li>
     * </ol>
     */
    private String renderResponse(CommandContext context, Object value) {
        // 1. skill 级 responseTemplate 优先
        if (responseTemplate != null && !responseTemplate.isEmpty()) {
            String valueStr = (value != null) ? String.valueOf(value) : "unknown";
            String mapped = valueMap.getOrDefault(valueStr, valueStr);
            return responseTemplate.replace("{value}", mapped);
        }

        // 2. 变量元数据自动组装
        VariableMeta meta = lookupVariableMeta(context, targetVarName);
        if (meta != null) {
            return meta.renderReadable(value);
        }

        // 3. 最终兜底
        return DEFAULT_TEMPLATE;
    }

    /**
     * 从 CaseContext 反查变量元数据。
     *
     * <p>使用反射调用 {@code caseContext.getVariableMeta(String)}，以兼容
     * CaseContext 尚未实现该方法的过渡期：
     * <ul>
     *   <li>方法存在 → 正常返回元数据</li>
     *   <li>方法不存在 → 返回 null，走 {@link #DEFAULT_TEMPLATE} 兜底（不报错）</li>
     * </ul>
     *
     * <p><b>TODO</b>：长期方案应在 {@code CaseContext} 中显式实现
     * {@code public VariableMeta getVariableMeta(String)}，本方法可退化为直接调用。
     */
    private VariableMeta lookupVariableMeta(CommandContext context, String varName) {
        try {
            Object caseContext = context.getCaseContext();
            if (caseContext == null) {
                return null;
            }

            // 反射调用 getVariableMeta(String)
            Method m = caseContext.getClass().getMethod("getVariableMeta", String.class);
            Object result = m.invoke(caseContext, varName);
            return (result instanceof VariableMeta vm) ? vm : null;

        } catch (NoSuchMethodException e) {
            // CaseContext 尚未实现 getVariableMeta，静默跳过
            if (log.isDebugEnabled()) {
                log.debug("CaseContext 未实现 getVariableMeta，跳过变量元数据组装: var={}", varName);
            }
            return null;
        } catch (Exception e) {
            log.debug("查询变量元数据失败: var={}, err={}", varName, e.getMessage());
            return null;
        }
    }
}

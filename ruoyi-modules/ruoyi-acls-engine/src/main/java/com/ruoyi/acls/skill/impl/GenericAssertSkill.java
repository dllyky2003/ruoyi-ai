package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.*;

@Slf4j
public class GenericAssertSkill extends AbstractEmsSkill {

    private static final String DEFAULT_UNAVAILABLE_VALUE = "unknown";

    private final String assertExpression;
    private final String targetVar;
    private final String successSpeech;
    private final Map<String, String> valueMap;
    private final List<Prerequisite> prerequisites;
    private final SpELExpressionEvaluator spelEvaluator;

    /**
     * YAML 支持：
     * - sourceVar: "phys.qrsDuration"
     * - sourceVars: ["phys.systolicBp", ...]
     * 二者会合并为最终要检查的 sourceVars 列表。
     */
    private final List<String> sourceVars;

    private final String unavailableValue;

    /**
     * 完整构造方法（用于 YamlSkillLoader）
     */
    public GenericAssertSkill(
        String keystepId,
        Duty requiredDuty,
        String assertExpression,
        String targetVar,
        String successSpeech,
        Map<String, String> valueMap,
        List<Prerequisite> prerequisites,
        String sourceVar,
        List<String> sourceVars,
        String unavailableValue,
        SpELExpressionEvaluator spelEvaluator
    ) {
        super(keystepId, requiredDuty);
        this.assertExpression = assertExpression;
        this.targetVar = targetVar;
        this.successSpeech = successSpeech;

        this.valueMap = valueMap != null ? valueMap : Collections.emptyMap();
        this.prerequisites = prerequisites != null ? prerequisites : Collections.emptyList();
        this.spelEvaluator = spelEvaluator;

        List<String> merged = new ArrayList<>();
        if (sourceVar != null && !sourceVar.isBlank()) {
            merged.add(sourceVar.trim());
        }
        if (sourceVars != null) {
            for (String s : sourceVars) {
                if (s != null && !s.isBlank()) merged.add(s.trim());
            }
        }
        this.sourceVars = Collections.unmodifiableList(merged);

        this.unavailableValue =
            (unavailableValue != null && !unavailableValue.isBlank())
                ? unavailableValue
                : DEFAULT_UNAVAILABLE_VALUE;
    }

    @Override
    public String getQueryResponse(CommandContext context) {
        Object value = context.getCaseContext().getVar(targetVar, Object.class);
        if (value == null) {
            return "尚未判读，请执行判读操作。";
        }
        if (unavailableValue.equals(String.valueOf(value))) {
            return "尚未判读，请执行判读操作。";
        }
        String displayValue = mapValue(valueMap, String.valueOf(value));
        return renderSpeechTemplate(successSpeech, displayValue);
    }

    @Override
    public CommandResult validateParams(CommandContext context, Map<String, Object> slots) {
        for (Prerequisite prereq : prerequisites) {
            Object actual = context.getCaseContext().getVar(prereq.variable, Object.class);
            if (!matchesExpected(actual, prereq.expected)) {
                return CommandResult.failure(prereq.message);
            }
        }
        return CommandResult.success("前置条件通过");
    }

    @Override
    @SuppressWarnings("deprecation")
    public CommandResult execute(CommandContext context, Map<String, Object> slots, TeamMember executor) {
        String sessionId = context.getSessionId();

        // 1. 构建 SpEL 评估上下文
        StandardEvaluationContext evalCtx = buildEvalContext(context);

        // 2. sourceVars 缺失判定（配置驱动）
        if (!sourceVars.isEmpty()) {
            for (String sourceVar : sourceVars) {
                Object v = context.getCaseContext().getVar(sourceVar, Object.class);
                if (v == null) {
                    return reportUnavailable(context, executor, sessionId, "源变量缺失: " + sourceVar);
                }
            }
        }

        // 3. 执行判读
        Object result;
        try {
            result = spelEvaluator.evaluateValue(assertExpression, evalCtx, Object.class);
        } catch (Exception e) {
            log.error("判读表达式执行失败: {}", assertExpression, e);
            return CommandResult.failure("判读失败: " + e.getMessage());
        }

        // 4. SpEL 结果为 null：按 B 口径“成功但提示尚未判读”
        if (result == null) {
            return reportUnavailable(context, executor, sessionId, "判读结果为空");
        }

        // 5. 写入判读结果
        String resultStr = result.toString();
        setSystemVar(context, targetVar, resultStr);

        // 6. NPC 口头汇报（复用模板渲染/播报拼接）
        String displayValue = mapValue(valueMap, resultStr);
        String reportSpeech = renderSpeechTemplate(successSpeech, displayValue);
        sendReportSpeech(context, executor, reportSpeech);

        log.info("📐 {} 判读完成: {} = {} (显示: {})", keystepId, targetVar, resultStr, displayValue);
        return CommandResult.success(reportSpeech);
    }

    private CommandResult reportUnavailable(CommandContext context, TeamMember executor, String sessionId, String reason) {
        // 写回 unknown（保证状态一致；后续查询也能识别）
        setSystemVar(context, targetVar, unavailableValue);

        String displayValue = mapValue(valueMap, unavailableValue);
        String reportSpeech = renderSpeechTemplate(successSpeech, displayValue);

        // 复用统一播报拼接
        sendReportSpeech(context, executor, reportSpeech);

        log.info("🟡 {} 判读信息不足: {} = {} (原因: {})", keystepId, targetVar, unavailableValue, reason);
        return CommandResult.success(reportSpeech);
    }

    private StandardEvaluationContext buildEvalContext(CommandContext context) {
        StandardEvaluationContext evalCtx = new StandardEvaluationContext();

        Map<String, Object> vars = context.getCaseContext().getVariablesSnapshot();
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            evalCtx.setVariable(entry.getKey(), entry.getValue());
        }

        evalCtx.setVariable("context", context);
        evalCtx.setVariable("caseContext", context.getCaseContext());

        return evalCtx;
    }

    // ==================== 前置条件比较增强（沿用你原逻辑） ====================

    private boolean matchesExpected(Object actual, Object expected) {
        if (Objects.equals(actual, expected)) {
            return true;
        }
        // 兼容 Boolean/String 数值等轻微类型差异
        if (expected instanceof Boolean expBool) {
            Boolean actBool = coerceToBoolean(actual);
            return actBool != null && actBool.equals(expBool);
        }
        if (expected instanceof Number expNum) {
            Double actNum = coerceToDouble(actual);
            return actNum != null && actNum.equals(expNum.doubleValue());
        }
        // 兜底：字符串化后再比一次（避免 "true" vs true 这类问题）
        if (expected != null) {
            return expected.toString().equals(String.valueOf(actual));
        }
        return false;
    }

    private Boolean coerceToBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(t)) return true;
            if ("false".equals(t)) return false;
        }
        return null;
    }

    private Double coerceToDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            String t = s.trim();
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    // ==================== 前置条件模型 ====================

    public static class Prerequisite {
        private final String variable;
        private final Object expected;
        private final String message;

        public Prerequisite(String variable, Object expected, String message) {
            this.variable = variable;
            this.expected = expected;
            this.message = message;
        }

        public String getVariable() {
            return variable;
        }

        public Object getExpected() {
            return expected;
        }

        public String getMessage() {
            return message;
        }
    }
}

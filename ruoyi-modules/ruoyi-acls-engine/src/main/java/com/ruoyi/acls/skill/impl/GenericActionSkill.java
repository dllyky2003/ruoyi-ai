package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class GenericActionSkill extends AbstractEmsSkill {

    private static final String DEFAULT_UNAVAILABLE_VALUE = "unknown";

    private final String vrActionCode;
    private final int durationSeconds;
    private final String successSpeech;
    private final Map<String, Object> outputs;
    private final List<Prerequisite> prerequisites;
    private final Map<String, String> varMapping;

    // ★ 保留字段以兼容旧构造器，但不再使用（统一走异步路径，完成由 TeamManager 事件驱动）
    private final boolean waitForCompletion;
    private final int timeoutSeconds;

    // 参考 GenericAssertSkill 的新增能力（可选字段）
    private final List<String> sourceVars;
    private final String targetVar;
    private final String unavailableValue;
    private final Map<String, String> valueMap;

    // ==================== 兼容旧构造器（签名不变） ====================

    public GenericActionSkill(String keystepId, Duty requiredDuty,
                              String vrActionCode, int durationSeconds,
                              String successSpeech,
                              Map<String, Object> outputs,
                              List<Prerequisite> prerequisites,
                              Map<String, String> varMapping,
                              boolean waitForCompletion,
                              int timeoutSeconds) {
        super(keystepId, requiredDuty);
        this.vrActionCode = vrActionCode;
        this.durationSeconds = durationSeconds;
        this.successSpeech = successSpeech;
        this.outputs = outputs != null ? outputs : Collections.emptyMap();
        this.prerequisites = prerequisites != null ? prerequisites : Collections.emptyList();
        this.varMapping = varMapping != null ? varMapping : Collections.emptyMap();
        this.waitForCompletion = waitForCompletion;
        this.timeoutSeconds = timeoutSeconds;

        this.sourceVars = Collections.emptyList();
        this.targetVar = null;
        this.unavailableValue = DEFAULT_UNAVAILABLE_VALUE;
        this.valueMap = Collections.emptyMap();
    }

    // 保留你原先的其他签名（避免 loader/旧代码被打断）
    public GenericActionSkill(String keystepId, Duty requiredDuty,
                              String vrActionCode,
                              String speech,
                              Map<String, Object> outputs,
                              Map<String, String> varMapping,
                              List<Prerequisite> prerequisites,
                              com.ruoyi.ems.spel.SpELExpressionEvaluator spelEvaluator) {
        super(keystepId, requiredDuty);
        this.vrActionCode = vrActionCode;
        this.durationSeconds = 3;
        this.successSpeech = speech;
        this.outputs = outputs != null ? outputs : Collections.emptyMap();
        this.prerequisites = prerequisites != null ? prerequisites : Collections.emptyList();
        this.varMapping = varMapping != null ? varMapping : Collections.emptyMap();
        this.waitForCompletion = true;
        this.timeoutSeconds = this.durationSeconds + 3;

        this.sourceVars = Collections.emptyList();
        this.targetVar = null;
        this.unavailableValue = DEFAULT_UNAVAILABLE_VALUE;
        this.valueMap = Collections.emptyMap();
    }

    public GenericActionSkill(String keepstepId, Duty requiredDuty,
                              String vrActionCode, int durationSeconds,
                              String successSpeech,
                              Map<String, Object> outputs) {
        this(keepstepId, requiredDuty, vrActionCode, durationSeconds,
            successSpeech, outputs, null, null, true, durationSeconds + 3);
    }

    // ==================== 完整构造器（用于 YamlSkillLoader） ====================
    public GenericActionSkill(String keystepId,
                              Duty requiredDuty,
                              String vrActionCode,
                              int durationSeconds,
                              String successSpeech,
                              Map<String, Object> outputs,
                              List<Prerequisite> prerequisites,
                              Map<String, String> varMapping,
                              boolean waitForCompletion,
                              int timeoutSeconds,
                              String targetVar,
                              String unavailableValue,
                              Map<String, String> valueMap,
                              String sourceVar,
                              List<String> sourceVars) {
        super(keystepId, requiredDuty);

        this.vrActionCode = vrActionCode;
        this.durationSeconds = durationSeconds;
        this.successSpeech = successSpeech;
        this.outputs = outputs != null ? outputs : Collections.emptyMap();
        this.prerequisites = prerequisites != null ? prerequisites : Collections.emptyList();
        this.varMapping = varMapping != null ? varMapping : Collections.emptyMap();
        this.waitForCompletion = waitForCompletion;
        this.timeoutSeconds = timeoutSeconds;

        this.targetVar = (targetVar != null && !targetVar.isBlank()) ? targetVar.trim() : null;

        this.unavailableValue =
            (unavailableValue != null && !unavailableValue.isBlank())
                ? unavailableValue.trim()
                : DEFAULT_UNAVAILABLE_VALUE;

        this.valueMap = valueMap != null ? valueMap : Collections.emptyMap();

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
    }

    // ==================== ★ 新增：供 SkillCompletionStateDriver 统一处理 ====================

    /**
     * 返回"逻辑变量名 → 值"的映射。
     * 值里的 #xxx 引用由 SkillCompletionStateDriver 侧统一解析（只用 CaseContext 变量快照）。
     */
    @Override
    public Map<String, Object> getSuccessOutputs() {
        if (outputs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            String varName = resolveVar(entry.getKey());
            result.put(varName, entry.getValue());
        }
        return result;
    }

    // ==================== 行为逻辑 ====================

    @Override
    public String getQueryResponse(CommandContext context) {
        // 保持原行为：outputs 中只要有某个"完成标记"是 true，就认为已完成
        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            String varName = resolveVar(entry.getKey());
            Boolean done = context.getCaseContext().getVar(varName, Boolean.class);
            if (done != null && done) {
                return successSpeech + "（已完成）";
            }
        }
        return successSpeech + "（未执行）";
    }

    @Override
    public CommandResult validateParams(CommandContext context, Map<String, Object> slots) {
        for (Prerequisite prereq : prerequisites) {
            String varName = resolveVar(prereq.getVariable());
            Object actual = context.getCaseContext().getVar(varName, Object.class);
            if (!matchesExpected(actual, prereq.getExpected())) {
                return CommandResult.failure(prereq.getMessage());
            }
        }
        return CommandResult.success("前置条件通过");
    }

    /**
     * ★ 精简版 execute：只发动作，立即返回。
     * - 完成信号由 TeamManager.updateStatus 的 TaskCompletedEvent 驱动
     * - 变量置位由 SkillCompletionStateDriver 统一处理（见 getSuccessOutputs）
     * - 不再 future.get，不再写 outputs，不再发完成汇报
     */
    @Override
    public CommandResult execute(CommandContext context, Map<String, Object> slots, TeamMember executor) {
        String sessionId = context.getSessionId();

        // 0) sourceVars 不齐全 -> unknown 口径，不执行 VR 动作
        if (!sourceVars.isEmpty()) {
            for (String sourceVar : sourceVars) {
                Object v = context.getCaseContext().getVar(sourceVar, Object.class);
                if (v == null) {
                    return reportUnavailable(context, executor, sessionId,
                        "源变量缺失: " + sourceVar);
                }
            }
        }

        // 1) NPC 口头确认
        sendConfirmSpeech(context, executor, keystepId);

        // 2) 发 VR 动作（不等待）
        context.getVrClientService().sendAction(
            sessionId, vrActionCode, slots, successSpeech, durationSeconds);

        // ★ 以下全部删除：
        //   - future.get(timeoutSeconds, TimeUnit.SECONDS)
        //   - outputs 写入循环
        //   - sendSpeech 完成汇报

        log.info("🟡 {} 已启动 (executor={}, duration={}s)",
            keystepId, executor.getName(), durationSeconds);
        return CommandResult.success("已开始执行 " + keystepId);
    }

    // ==================== unknown 口径：复用 AbstractEmsSkill ====================

    /**
     * 源变量缺失时的失败路径。
     * 注意：这条路径走 setSystemVar 直接写 unknown，不经过 SkillCompletionStateDriver，
     * 因为 TaskCompletedEvent 的 success=false 会被 driver 跳过，语义上是"未执行"而非"已完成"。
     */
    private CommandResult reportUnavailable(CommandContext context, TeamMember executor,
                                            String sessionId, String reason) {
        // 写回 unknown：优先 targetVar；否则写回 outputs 指定的所有变量
        if (targetVar != null && !targetVar.isBlank()) {
            setSystemVar(context, targetVar, unavailableValue);
        } else {
            for (String logicalKey : outputs.keySet()) {
                String varName = resolveVar(logicalKey);
                setSystemVar(context, varName, unavailableValue);
            }
        }

        String displayValue = mapValue(valueMap, unavailableValue);

        String renderedSpeech;
        if (successSpeech != null && successSpeech.contains("{value}")) {
            renderedSpeech = renderSpeechTemplate(successSpeech, displayValue);
        } else {
            renderedSpeech = successSpeech + "（信息不足，操作未执行）";
        }

        sendReportSpeech(context, executor, renderedSpeech);

        log.info("🟡 {} action 未执行: {} (原因: {})", keystepId, unavailableValue, reason);
        return CommandResult.success(renderedSpeech);
    }

    // ==================== 前置条件比较增强 ====================

    private boolean matchesExpected(Object actual, Object expected) {
        if (Objects.equals(actual, expected)) return true;

        if (expected instanceof Boolean expBool) {
            Boolean actBool = coerceToBoolean(actual);
            return actBool != null && actBool.equals(expBool);
        }

        if (expected instanceof Number expNum) {
            Double actNum = coerceToDouble(actual);
            return actNum != null && actNum.equals(expNum.doubleValue());
        }

        if (expected != null) {
            return expected.toString().equals(String.valueOf(actual));
        }
        return false;
    }

    private Boolean coerceToBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;

        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == 0d) return false;
            if (d == 1d) return true;
            return null;
        }

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

    // ==================== 变量解析 ====================

    private String resolveVar(String logicalName) {
        return varMapping.getOrDefault(logicalName, logicalName);
    }

    // ★ buildEvalMap 已删除：outputs 的 #xxx 解析搬到 SkillCompletionStateDriver，
    //   driver 侧只用 CaseContext 变量快照 + elapsedSeconds + currentState

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

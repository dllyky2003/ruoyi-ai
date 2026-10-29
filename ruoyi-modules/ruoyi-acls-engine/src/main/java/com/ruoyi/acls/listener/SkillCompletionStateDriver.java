package com.ruoyi.acls.listener;

import com.ruoyi.acls.branch.StudentResponseBranch;
import com.ruoyi.acls.branch.StudentResponseResolver;
import com.ruoyi.acls.contract.diagnose.RejectionRecorder;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.Drugs;
import com.ruoyi.ems.effect.DrugIndicationEvaluator;
import com.ruoyi.ems.effect.EffectBranch;
import com.ruoyi.ems.effect.EffectManager;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.engine.StateTransitionResult;
import com.ruoyi.ems.event.TaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能完成 → 置位变量 → 驱动状态机 → 触发药物/操作效果。
 *
 * <p>★ 方案 A 修改：当变量驱动重评估（纯条件出边）失败时，
 * 用 {@link TaskCompletedEvent#getIntentCode()} 携带的意图编码
 * 再走一次带 intent 的重评估，让带 intentCode 的 self-loop 与出边能被匹配。</p>
 *
 * <p>★ 诊断埋点：状态机拒绝时把现场记入 {@link RejectionRecorder}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCompletionStateDriver {

    private final SkillRegistry skillRegistry;
    private final EmsContextManager contextManager;
    private final StateMachineFacade stateMachineFacade;
    private final DrugIndicationEvaluator indicationEvaluator;
    private final EffectManager effectManager;
    private final StudentResponseResolver responseResolver;

    // ★ 诊断埋点组件
    private final RejectionRecorder rejectionRecorder;

    private static final String LAST_INTERVENTION_TIME = "process.lastInterventionTime";

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (!event.isSuccess()) {
            log.info("任务失败，跳过状态驱动: task={}", event.getMember().getCurrentTask());
            return;
        }

        String sessionId = event.getSessionId();
        String taskId = event.getMember().getCurrentTask();

        EmsSkill skill = skillRegistry.getSkill(taskId);
        if (skill == null) {
            log.warn("技能未找到: {}", taskId);
            return;
        }

        // 1. 拿技能输出
        Map<String, Object> outputs = skill.getSuccessOutputs();
        if (outputs == null || outputs.isEmpty()) {
            String varName = skill.getSuccessVarName();
            if (varName == null) {
                log.info("技能 {} 无输出变量，跳过", taskId);
                return;
            }
            outputs = Map.of(varName, true);
        }

        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            log.warn("上下文不存在: {}", sessionId);
            return;
        }

        // 2. 解析 #xxx 引用
        Map<String, Object> evalMap = new HashMap<>(ctx.getVariablesSnapshot());
        evalMap.put("caseContext", ctx);
        evalMap.put("elapsedSeconds", ctx.getElapsedSeconds());
        evalMap.put("currentState", ctx.getCurrentState());

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : outputs.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();

            if (LAST_INTERVENTION_TIME.equals(key)) {
                long now = System.currentTimeMillis();
                resolved.put(key, now);
                log.info("⏱️ 强制更新 lastInterventionTime 为当前时间戳: session={}, task={}, now={}",
                    sessionId, taskId, now);
                continue;
            }

            resolved.put(key, resolveValue(value, evalMap));
        }

        // 3. 变量批量写入
        long version = ctx.getVersion();
        boolean ok = contextManager.batchSetVarsWithVersion(
            sessionId, resolved, "SYSTEM", version);

        if (!ok) {
            log.warn("变量批量写入失败（版本冲突？）: session={}, task={}, vars={}",
                sessionId, taskId, resolved.keySet());
            return;
        }
        log.info("📌 变量批量置位: {} (task={})", resolved, taskId);

        // 4. 驱动状态机
        StateTransitionResult transition =
            stateMachineFacade.reEvaluateOnContextChange(sessionId, taskId);

        // ★ 方案 A：纯条件出边失败时，用 intentCode 重试
        if (transition == null || !transition.isSuccess()) {
            String intentCode = event.getIntentCode();
            if (intentCode != null && !intentCode.isBlank()) {
                log.info("🔁 无纯条件出边，尝试带 intent 重试: task={}, intent={}",
                    taskId, intentCode);
                transition = stateMachineFacade.reEvaluateWithIntent(sessionId, intentCode);
            }
            if (transition == null || !transition.isSuccess()) {
                log.info("⛔ 状态机未成功转移（{}），跳过效果触发: task={}",
                    transition != null ? transition.getReason() : "null", taskId);

                // ★ 诊断埋点：记录拒绝现场
                rejectionRecorder.record(
                    ctx.getCaseType(),
                    sessionId,
                    ctx.getCurrentState(),
                    event.getIntentCode(),
                    taskId);

                return;
            }
        }

        // 5. EffectGate 三态分派
        StudentResponseBranch branch = responseResolver.resolve(sessionId);
        log.info("[EffectGate][Branch] session={} task={} branch={}",
            sessionId, taskId, branch);

        switch (branch) {
            case NORMAL:
                tryTriggerEffect(sessionId, taskId, resolved);
                break;
            case ERROR:
                tryTriggerErrorEffect(sessionId, taskId, resolved);
                break;
            case DELAYED:
                tryTriggerDelayedEffect(sessionId, taskId, resolved);
                break;
        }
    }

    // ============================================================
    // 三个分支入口
    // ============================================================

    private void tryTriggerEffect(String sessionId, String taskId,
                                  Map<String, Object> resolved) {
        doTriggerEffect(sessionId, taskId, resolved, EffectBranch.NORMAL);
    }

    private void tryTriggerErrorEffect(String sessionId, String taskId,
                                       Map<String, Object> resolved) {
        doTriggerEffect(sessionId, taskId, resolved, EffectBranch.ERROR);
    }

    private void tryTriggerDelayedEffect(String sessionId, String taskId,
                                         Map<String, Object> resolved) {
        doTriggerEffect(sessionId, taskId, resolved, EffectBranch.DELAYED);
    }

    private void doTriggerEffect(String sessionId, String taskId,
                                 Map<String, Object> resolved, EffectBranch branch) {
        try {
            CaseContext ctx = contextManager.getSnapshot(sessionId);
            if (ctx == null) return;

            String caseType = ctx.getCaseType();
            if (caseType == null || caseType.isBlank()) return;

            String effectKey = deriveEffectKey(taskId, resolved, ctx);
            if (effectKey == null) {
                log.info("技能 {} 无对应效果 key，跳过 (branch={})", taskId, branch);
                return;
            }

            String condition;
            if (branch == EffectBranch.NORMAL) {
                String drugCode = extractDrugCode(effectKey);
                condition = (drugCode != null)
                    ? indicationEvaluator.resolve(caseType, drugCode, ctx)
                    : "correct";
            } else {
                condition = branch.getDefaultCondition();
            }

            log.info("💊 效果触发: task={}, effectKey={}, condition={}, branch={}",
                taskId, effectKey, condition, branch);

            effectManager.triggerEffect(effectKey, condition, branch, ctx);

        } catch (Exception e) {
            log.warn("doTriggerEffect 失败: session={}, task={}, branch={}, err={}",
                sessionId, taskId, branch, e.getMessage(), e);
        }
    }

    private String deriveEffectKey(String taskId,
                                   Map<String, Object> resolved,
                                   CaseContext ctx) {

        if (taskId.startsWith("drug_push_")) {
            String drugCode = taskId.substring("drug_push_".length());
            String dose = ctx.getVar("intervention.lastDrugDose", String.class);
            if (dose == null || dose.isBlank()) {
                Object d = resolved.get("intervention.lastDrugDose");
                if (d != null) dose = String.valueOf(d);
            }
            return (dose != null && !dose.isBlank())
                ? Drugs.getEffectId(drugCode, dose)
                : null;
        }

        if ("administer_sedation".equals(taskId)) {
            Object drug = resolved.get("intervention.sedation.drug");
            Object dose = resolved.get("intervention.sedation.dose");
            if (drug != null && dose != null) {
                return Drugs.getEffectId(String.valueOf(drug), String.valueOf(dose));
            }
            return null;
        }

        if ("deliver_sync_shock".equals(taskId)) {
            Object energy = resolved.get("intervention.cardioversion.lastEnergy");
            if (energy instanceof Number n) {
                return "CARDIOVERSION_" + n.intValue() + "J";
            }
            return null;
        }

        return null;
    }

    private String extractDrugCode(String effectKey) {
        if (effectKey == null) return null;
        for (String code : Drugs.getAllDrugCodes()) {
            if (effectKey.startsWith(code + "_")) {
                return code;
            }
        }
        return null;
    }

    private Object resolveValue(Object value, Map<String, Object> evalMap) {
        if (!(value instanceof String s)) return value;
        if (!s.startsWith("#")) return value;

        String expr = s.trim();

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^#([a-zA-Z0-9_.]+)\\s*([+\\-])\\s*(\\d+)$")
            .matcher(expr);
        if (m.matches()) {
            String varKey = m.group(1);
            String op = m.group(2);
            int num = Integer.parseInt(m.group(3));
            Object base = evalMap.get(varKey);
            int baseInt = (base instanceof Number n) ? n.intValue() : 0;
            int result = op.equals("+") ? baseInt + num : baseInt - num;
            log.info("算术解析: {} = {} {} {} = {}", expr, baseInt, op, num, result);
            return result;
        }

        String varKey = expr.substring(1);
        if (evalMap.containsKey(varKey)) {
            return evalMap.get(varKey);
        }
        log.warn("变量 {} 不存在，保留原值", varKey);
        return value;
    }
}

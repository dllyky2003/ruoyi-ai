package com.ruoyi.acls.listener;


import com.ruoyi.acls.branch.StudentResponseBranch;
import com.ruoyi.acls.branch.StudentResponseResolver;
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
 * <p>★ 2026-09-23 修改（路径 A：统一 effect 触发入口）：</p>
 * <ul>
 *   <li>变量写入 + 状态机驱动后，新增第 5 步：从技能输出推导 effectKey，
 *       调 {@link DrugIndicationEvaluator} 判定 condition，
 *       调 {@link EffectManager#triggerEffect(String, String, CaseContext)} 触发效果。</li>
 *   <li>覆盖所有技能路径（YAML generic、Spring Bean），不再依赖 DrugPushSkill 自身的钩子。</li>
 * </ul>
 *
 * <p>★ 2026-09-23 修改（P1：修复 Effect 泄漏）：</p>
 * <ul>
 *   <li>第 4 步改为接收 {@link StateTransitionResult}，第 5 步前置判断：
 *       状态机未成功转移（含 Tutor 拦截、无匹配转移、异常）时，跳过效果触发。</li>
 *   <li>修复现象：学员说"用二线药物"被状态机拒绝后，
 *       {@code drug_push_diltiazem} 的 effectKey 仍被触发。</li>
 * </ul>
 *
 * <p>★ 2026-09-23 修改（螺旋 3：EffectGate 三态）：</p>
 * <ul>
 *   <li>第 5 步由单一 tryTriggerEffect 扩展为按 {@link StudentResponseBranch}
 *       分派：NORMAL → tryTriggerEffect；ERROR → tryTriggerErrorEffect；
 *       DELAYED → tryTriggerDelayedEffect。</li>
 *   <li>branch 由 {@link StudentResponseResolver#resolve(String)} 提供，
 *       反映 AclsCommandEngine 最近一次写入的学员操作质量信号。</li>
 *   <li>三个入口统一收敛到 {@link #doTriggerEffect}，按 {@link EffectBranch}
 *       派生 condition：NORMAL → indicationEvaluator 推导；
 *       ERROR → {@code wrong_critical}；DELAYED → {@code delayed}。</li>
 * </ul>
 *
 * <p>★ 2026-09-24 修改（螺旋 4 G4-06 第三步：腺苷二次推药 WARN 下沉）：</p>
 * <ul>
 *   <li>腺苷二次推药 WARN 逻辑已<b>下沉到技能层</b>
 *       （{@code com.ruoyi.acls.skill.impl.DrugPushAdenosineSkill}），
 *       因为 {@code drug_push_adenosine} 走 Spring Bean 通道，无 successOutputs，
 *       本 Driver 在“无输出变量”分支会提前返回，无法承担该职责。</li>
 *   <li>本类职责回归纯粹：变量置位 → 状态机驱动 → EffectGate 分派。</li>
 * </ul>
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

    // ★ 螺旋 3：学员操作三态分支
    private final StudentResponseResolver responseResolver;

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
        // ★ 2026-09-23 修改：接收转移结果，作为第 5 步的前置闸门
        StateTransitionResult transition =
            stateMachineFacade.reEvaluateOnContextChange(sessionId, taskId);

        // ★ 新增：状态机未成功转移 → 跳过效果触发
        //   覆盖三类情况：
        //     - Tutor 拦截（transition.isSuccess() == false, reason = "Tutor 拦截: ..."）
        //     - 无匹配转移（reason = "无匹配转移"）
        //     - 转移异常或配置缺失
        if (transition == null || !transition.isSuccess()) {
            log.info("⛔ 状态机未成功转移（{}），跳过效果触发: task={}",
                transition != null ? transition.getReason() : "null", taskId);
            return;
        }

        // 5. ★ 螺旋 3：三态 EffectGate 分派
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
    // ★ 路径 A 新增：统一触发效果
    // ============================================================

    /**
     * NORMAL 分支入口：condition 由 {@link DrugIndicationEvaluator} 按患者状态推导。
     */
    private void tryTriggerEffect(String sessionId, String taskId,
                                  Map<String, Object> resolved) {
        doTriggerEffect(sessionId, taskId, resolved, EffectBranch.NORMAL);
    }

    /**
     * ★ 螺旋 3：错误操作分支入口。
     *
     * <p>condition 使用 {@link EffectBranch#ERROR} 的默认值 {@code wrong_critical}，
     * 命中 YAML 中的 {@code effectKey__wrong_critical} 效果模板。</p>
     */
    private void tryTriggerErrorEffect(String sessionId, String taskId,
                                       Map<String, Object> resolved) {
        doTriggerEffect(sessionId, taskId, resolved, EffectBranch.ERROR);
    }

    /**
     * ★ 螺旋 3：延时操作分支入口。
     *
     * <p>condition 使用 {@link EffectBranch#DELAYED} 的默认值 {@code delayed}，
     * 命中 YAML 中的 {@code effectKey__delayed} 效果模板。</p>
     */
    private void tryTriggerDelayedEffect(String sessionId, String taskId,
                                         Map<String, Object> resolved) {
        doTriggerEffect(sessionId, taskId, resolved, EffectBranch.DELAYED);
    }

    /**
     * ★ 螺旋 3：统一效果触发（按 branch 分派 condition）。
     *
     * <p>effectKey 推导规则（当前为 Java 端规则，后续可改 YAML 声明）：</p>
     * <ul>
     *   <li>{@code drug_push_<drug>}：从 {@code intervention.lastDrugDose} 取剂量
     *       → effectKey = {@code <drug>_<dose>}</li>
     *   <li>{@code administer_sedation}：从 {@code intervention.sedation.drug/dose}
     *       → effectKey = {@code <drug>_<dose>}</li>
     *   <li>{@code deliver_sync_shock}：从 {@code intervention.cardioversion.lastEnergy}
     *       → effectKey = {@code CARDIOVERSION_<N>J}</li>
     *   <li>其他技能：不触发，返回 null</li>
     * </ul>
     *
     * <p>condition 派生规则：</p>
     * <ul>
     *   <li>NORMAL → 走 {@link DrugIndicationEvaluator#resolve}，非药物默认 {@code "correct"}</li>
     *   <li>ERROR / DELAYED → 取 {@link EffectBranch#getDefaultCondition()}</li>
     * </ul>
     */
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

            // 判定 condition
            String condition;
            if (branch == EffectBranch.NORMAL) {
                // 原有逻辑：由 indicationEvaluator 按患者状态推导
                String drugCode = extractDrugCode(effectKey);
                condition = (drugCode != null)
                    ? indicationEvaluator.resolve(caseType, drugCode, ctx)
                    : "correct";
            } else {
                // ERROR / DELAYED：直接用 branch 的默认 condition
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

    /**
     * 从技能输出推导 effectKey。返回 null 表示该技能不触发效果。
     */
    private String deriveEffectKey(String taskId,
                                   Map<String, Object> resolved,
                                   CaseContext ctx) {

        // 1. drug_push_<drug>
        if (taskId.startsWith("drug_push_")) {
            String drugCode = taskId.substring("drug_push_".length());
            String dose = ctx.getVar("intervention.lastDrugDose", String.class);
            if (dose == null || dose.isBlank()) {
                // 兜底：从 resolved 里读
                Object d = resolved.get("intervention.lastDrugDose");
                if (d != null) dose = String.valueOf(d);
            }
            return (dose != null && !dose.isBlank())
                ? Drugs.getEffectId(drugCode, dose)
                : null;
        }

        // 2. administer_sedation
        if ("administer_sedation".equals(taskId)) {
            Object drug = resolved.get("intervention.sedation.drug");
            Object dose = resolved.get("intervention.sedation.dose");
            if (drug != null && dose != null) {
                return Drugs.getEffectId(String.valueOf(drug), String.valueOf(dose));
            }
            return null;
        }

        // 3. deliver_sync_shock
        if ("deliver_sync_shock".equals(taskId)) {
            Object energy = resolved.get("intervention.cardioversion.lastEnergy");
            if (energy instanceof Number n) {
                return "CARDIOVERSION_" + n.intValue() + "J";
            }
            return null;
        }

        // 4. 其他技能暂不触发
        return null;
    }

    /**
     * 从 effectKey 中提取药物代码。
     * 如 "adenosine_6mg" → "adenosine"；非药物返回 null。
     */
    private String extractDrugCode(String effectKey) {
        if (effectKey == null) return null;
        for (String code : Drugs.getAllDrugCodes()) {
            if (effectKey.startsWith(code + "_")) {
                return code;
            }
        }
        return null;
    }

    // ============================================================
    // 原有 resolveValue（保持不变）
    // ============================================================

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

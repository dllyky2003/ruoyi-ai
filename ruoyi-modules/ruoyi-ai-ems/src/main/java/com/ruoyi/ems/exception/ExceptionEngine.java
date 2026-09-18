package com.ruoyi.ems.exception;

import com.ruoyi.ems.config.loader.ExceptionRuleLoader;
import com.ruoyi.ems.domain.CaseContext;

import com.ruoyi.ems.domain.ExceptionRule.ExceptionRule;
import com.ruoyi.ems.rule.IRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 异常规则评估引擎。
 *
 * <p>职责：给定 (caseType, intentId, phase, CaseContext)，返回所有命中的异常规则。
 *
 * <p>设计定位：<b>无副作用</b>。不写 effects、不跳转、不记 errorChain、不发 TTS。
 * 副作用全部由调用方（AclsCommandEngine 第 7.5 步）按当前模式执行。
 *
 * <p>不依赖教学模式。三模式下"哪些规则命中"完全一致，
 * 差异只在"命中后怎么消费"（教学讲解 / 学习打断 / 考核静默），由调用方处理。
 *
 * <p>命中列表排序：按 severity 降序（CRITICAL > MINOR > TIMING）；
 * 同 severity 保持规则定义顺序（稳定排序）。调用方取
 * {@code hits.get(0).getJumpTo()} 即为"severity 最高的一条"的跳转。
 */
@Slf4j
@Component
public class ExceptionEngine {

    private final ExceptionRuleLoader ruleLoader;
    private final IRuleEvaluator ruleEvaluator;

    public ExceptionEngine(ExceptionRuleLoader ruleLoader, IRuleEvaluator ruleEvaluator) {
        this.ruleLoader = ruleLoader;
        this.ruleEvaluator = ruleEvaluator;
    }

    /**
     * 评估异常规则。
     *
     * @param caseType 案例 id，用于取该案例的合并后规则（含全局）
     * @param intentId 当前意图 id
     * @param phase    当前阶段，用于范围限定；可为空 = 不限定
     * @param ctx      CaseContext，用于 SpEL 条件求值
     * @return 命中规则列表（不可变），按 severity 降序；无命中返回空列表
     */
    public List<ExceptionRule> evaluate(String caseType,
                                        String intentId,
                                        String phase,
                                        CaseContext ctx) {
        if (!StringUtils.hasText(caseType)) {
            log.debug("ExceptionEngine: caseType 为空，返回空列表");
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(intentId)) {
            log.debug("ExceptionEngine: intentId 为空，返回空列表");
            return Collections.emptyList();
        }
        if (ctx == null) {
            log.warn("ExceptionEngine: CaseContext 为空，返回空列表");
            return Collections.emptyList();
        }

        List<ExceptionRule> all = ruleLoader.getRulesForCase(caseType);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExceptionRule> hits = new ArrayList<>();
        for (ExceptionRule rule : all) {
            if (!matchesPhase(rule, phase))      continue;
            if (!matchesIntent(rule, intentId))  continue;
            if (!matchesCondition(rule, ctx))    continue;
            hits.add(rule);
        }

        if (hits.isEmpty()) {
            return Collections.emptyList();
        }

        // 稳定排序：同 severity 保持规则定义顺序
        hits.sort(Comparator
            .comparingInt((ExceptionRule r) -> severityWeight(r.getSeverity()))
            .reversed());

        if (log.isDebugEnabled()) {
            log.debug("异常评估命中: case={}, intent={}, phase={}, 候选={}, 命中={}",
                caseType, intentId, phase, all.size(), hits.size());
            for (ExceptionRule r : hits) {
                log.debug("  - {} [{}] jumpTo={}", r.getId(), r.getSeverity(), r.getJumpTo());
            }
        }

        return Collections.unmodifiableList(hits);
    }

    // ====================================================================
    // 匹配
    // ====================================================================

    /**
     * phase 范围限定：
     *   - 规则未声明 phase → 不限阶段
     *   - 调用方未传 phase → 不限阶段
     *   - 都非空但不等 → 不匹配
     */
    private boolean matchesPhase(ExceptionRule rule, String phase) {
        if (!StringUtils.hasText(rule.getPhase())) return true;
        if (!StringUtils.hasText(phase))           return true;
        return phase.equals(rule.getPhase());
    }

    /**
     * triggerIntent 匹配：
     *   - 规则未声明 triggerIntent → 不限定意图（纯 condition 规则）
     *   - 否则必须严格等于当前 intentId
     */
    private boolean matchesIntent(ExceptionRule rule, String intentId) {
        String t = rule.getTriggerIntent();
        if (!StringUtils.hasText(t)) return true;
        return t.equals(intentId);
    }

    /**
     * condition 求值：
     *   - 规则未声明 condition → 视为命中（已在 phase + intent 过滤）
     *   - 否则用 IRuleEvaluator 求值；求值异常 → 记 error，视为不命中（保守）
     *
     * 注意：只走 evaluateCondition(expression, CaseContext) 重载。
     * SpELExpressionEvaluator 的 Map 版本重载直接抛 UnsupportedOperationException（Bug P1-1）。
     */
    private boolean matchesCondition(ExceptionRule rule, CaseContext ctx) {
        String cond = rule.getCondition();
        if (!StringUtils.hasText(cond)) return true;
        try {
            return ruleEvaluator.evaluateCondition(cond, ctx);
        } catch (Exception e) {
            log.error("异常规则 condition 求值失败: id={}, condition={}",
                rule.getId(), cond, e);
            return false;
        }
    }

    private int severityWeight(String severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case "CRITICAL" -> 3;
            case "MINOR"    -> 2;
            case "TIMING"   -> 1;
            default         -> 0;
        };
    }
}

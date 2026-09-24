package com.ruoyi.acls.tutor.integration;

import com.ruoyi.acls.tutor.domain.CaseOutcomeBranch;
import com.ruoyi.acls.tutor.domain.ClinicalRule;
import com.ruoyi.acls.tutor.domain.EffectSpecStatic;
import com.ruoyi.acls.tutor.feedback.FeedbackMessageResolver;
import com.ruoyi.acls.tutor.repository.RuleRepository;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import com.ruoyi.ems.spi.ClinicalRuleService;
import com.ruoyi.ems.spi.dto.ClinicalRuleContext;
import com.ruoyi.ems.spi.dto.EffectSpec;
import com.ruoyi.ems.spi.dto.ExecutionStrategy;
import com.ruoyi.ems.spi.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 临床规则 SPI 的 tutor 实现。
 *
 * <p>V3.1 扩展：命中规则时，把 rule.effects（静态模板）转换为已解析的
 * {@link EffectSpec} 列表填入 {@code ValidationResult.allowedEffects}。</p>
 *
 * <p><b>求值语义</b>：{@code findMatch} 只做 intent + state 的静态匹配；
 * {@code applies_when} 条件表达式在本层用 SpEL 求值，未通过则视为无匹配。</p>
 */
@Slf4j
@Component
public class TutorClinicalRuleService implements ClinicalRuleService {

    @Autowired(required = false)
    private RuleRepository ruleRepository;

    @Autowired(required = false)
    private FeedbackMessageResolver feedbackMessageResolver;

    /** SpEL 求值器：复用 ems 模块的实现，接受 CaseContext 作为上下文。 */
    @Autowired(required = false)
    private SpELExpressionEvaluator spelEvaluator;

    /** 上下文管理器：用 sessionId 拿 CaseContext 供 SpEL 求值。 */
    @Autowired(required = false)
    private EmsContextManager emsContextManager;

    @Override
    public ValidationResult validate(ClinicalRuleContext context) {
        ValidationResult r = ValidationResult.allowWithoutTutor();
        if (context == null) {
            return r;
        }

        ClinicalRule match = null;
        if (ruleRepository != null) {
            match = ruleRepository.findMatch(
                context.getCaseType(), context.getIntentCode(), context.getCurrentState());
        }

        // ★ 新增：applies_when 条件闸门
        if (match != null && !evaluateAppliesWhen(match, context)) {
            log.info("[Tutor][ClinicalRule] rule={} applies_when 未通过，视为无匹配",
                match.getRuleId());
            match = null;
        }

        if (match != null) {
            // ---- decision ----
            try {
                r.setDecision(ExecutionStrategy.valueOf(match.getDecision()));
            } catch (Exception ignored) {
                // 未知 decision 保持 ALLOW
            }
            r.setSeverity(match.getSeverity() != null ? match.getSeverity() : "none");
            r.setPrimaryRuleId(match.getRuleId());
            r.getAllMatchedRuleIds().add(match.getRuleId());

            // ---- assessment tags ----
            if (match.getAssessmentTags() != null && !match.getAssessmentTags().isEmpty()) {
                r.getAssessmentTags().addAll(match.getAssessmentTags());
            }

            // ---- feedback ----
            if (match.getFeedbackKey() != null) {
                r.getFeedbackKeys().add(match.getFeedbackKey());

                if (feedbackMessageResolver != null) {
                    String text = feedbackMessageResolver.resolve(match.getFeedbackKey(), null);
                    r.setFeedbackText(text);
                } else {
                    r.setFeedbackText(match.getFeedbackKey());
                }
            }

            // ---- V3.1 新增：已解析授权 ----
            List<EffectSpec> effects = convertEffects(match, context.getCaseType());
            r.setAllowedEffects(effects);

            log.info("[Tutor][ClinicalRule] session={}, case={}, state={}, intent={}, rev={}, "
                    + "decision={}, ruleId={}, severity={}, feedbackKey={}, feedbackText={}, allowedEffects={}",
                context.getSessionId(), context.getCaseType(), context.getCurrentState(),
                context.getIntentCode(), context.getStateRevision(),
                r.getDecision(), r.getPrimaryRuleId(), r.getSeverity(),
                match.getFeedbackKey(), r.getFeedbackText(), effects.size());
        } else {
            log.info("[Tutor][ClinicalRule] session={}, case={}, state={}, intent={}, rev={}, "
                    + "decision=ALLOW, ruleId=<no match>",
                context.getSessionId(), context.getCaseType(), context.getCurrentState(),
                context.getIntentCode(), context.getStateRevision());
        }

        return r;
    }

    // ==================== ★ 新增：applies_when 求值 ====================

    /**
     * 求值 {@code rule.applies_when}。
     *
     * <p>语义：
     * <ul>
     *   <li>{@code appliesWhen} 为空 → 恒真</li>
     *   <li>{@code appliesWhen.expression} 为合法 SpEL → 结果为 true 才通过</li>
     *   <li>拿不到 CaseContext / evaluator 未装配 / 求值异常 → 保守放行（true）</li>
     * </ul>
     *
     * <p>SpEL 可用变量由 ems 模块的 {@link SpELExpressionEvaluator} 从
     * CaseContext + EmsContextManager 动态组装，支持
     * {@code #phys.*}、{@code #process.*}、{@code #intervention.*}、
     * {@code #device.*}、{@code #clinical.*}、{@code #derived.*} 前缀。</p>
     */
    private boolean evaluateAppliesWhen(ClinicalRule rule, ClinicalRuleContext context) {
        Map<String, Object> appliesWhen = rule.getAppliesWhen();
        if (appliesWhen == null || appliesWhen.isEmpty()) {
            return true;
        }

        Object exprObj = appliesWhen.get("expression");
        if (!(exprObj instanceof String expr) || expr.isBlank()) {
            log.warn("[Tutor][ClinicalRule] rule={} 的 applies_when 无 expression，默认放行",
                rule.getRuleId());
            return true;
        }

        if (spelEvaluator == null) {
            log.warn("[Tutor][ClinicalRule] SpEL 求值器未装配，rule={} 的条件被忽略: {}",
                rule.getRuleId(), expr);
            return true;
        }

        // 用 sessionId 从 EmsContextManager 取 CaseContext
        CaseContext caseContext = null;
        if (emsContextManager != null && context.getSessionId() != null) {
            caseContext = emsContextManager.getSnapshot(context.getSessionId());
        }

        if (caseContext == null) {
            log.warn("[Tutor][ClinicalRule] rule={} 无法获取 CaseContext（session={}），applies_when 保守放行",
                rule.getRuleId(), context.getSessionId());
            return true;
        }

        try {
            // 复用 ems 的 SpEL 求值器；变量由 CaseContext + EmsContextManager 动态提供
            return spelEvaluator.evaluateCondition(expr, caseContext);
        } catch (Exception e) {
            log.error("[Tutor][ClinicalRule] rule={} applies_when 求值失败: {}",
                rule.getRuleId(), expr, e);
            return true;   // 求值异常保守放行
        }
    }

    // ==================== 原 convertEffects 不变 ====================

    private List<EffectSpec> convertEffects(ClinicalRule rule, String caseId) {
        if (rule.getEffects() == null || rule.getEffects().isEmpty()) {
            return new ArrayList<>();
        }

        List<EffectSpec> result = new ArrayList<>(rule.getEffects().size());
        String authId = "auth-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        for (EffectSpecStatic s : rule.getEffects()) {
            if (s == null
                || s.getEffectId() == null || s.getEffectId().isBlank()
                || s.getBranchId() == null || s.getBranchId().isBlank()) {
                log.warn("[Tutor][ClinicalRule] 跳过无效 effect: ruleId={}, effectId={}, branchId={}",
                    rule.getRuleId(),
                    s == null ? null : s.getEffectId(),
                    s == null ? null : s.getBranchId());
                continue;
            }

            if (ruleRepository != null && caseId != null) {
                CaseOutcomeBranch branch = ruleRepository.findBranch(caseId, s.getBranchId());
                if (branch == null) {
                    log.warn("[Tutor][ClinicalRule] effect 引用了不存在的 branch_id: "
                            + "ruleId={}, branchId={}（契约诊断阶段将报错）",
                        rule.getRuleId(), s.getBranchId());
                }
            }

            EffectSpec e = new EffectSpec();
            e.setAuthorizationId(authId);
            e.setAuthorizedByRuleIds(Collections.singletonList(rule.getRuleId()));
            e.setEffectId(s.getEffectId());
            e.setBranchId(s.getBranchId());
            e.setResolved(true);
            result.add(e);
        }

        return result;
    }
}

package com.ruoyi.acls.tutor.integration;

import com.ruoyi.acls.tutor.domain.CaseOutcomeBranch;
import com.ruoyi.acls.tutor.domain.ClinicalRule;
import com.ruoyi.acls.tutor.domain.EffectSpecStatic;
import com.ruoyi.acls.tutor.feedback.FeedbackMessageResolver;
import com.ruoyi.acls.tutor.repository.RuleRepository;
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
import java.util.UUID;

/**
 * 临床规则 SPI 的 tutor 实现。
 *
 * <p>V3.1 扩展：命中规则时，把 rule.effects（静态模板）转换为已解析的
 * {@link EffectSpec} 列表填入 {@code ValidationResult.allowedEffects}。</p>
 *
 * <p><b>求值语义</b>：{@code resolved=true} 表示"已通过 findMatch 的静态匹配
 * （intent + state）"。完整 constraints 求值（条件 DSL）留给后续阶段。</p>
 */
@Slf4j
@Component
public class TutorClinicalRuleService implements ClinicalRuleService {

    @Autowired(required = false)
    private RuleRepository ruleRepository;

    @Autowired(required = false)
    private FeedbackMessageResolver feedbackMessageResolver;

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
                    // resolver 未装配（异常情况），退化为 key
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

    /**
     * 把规则里的静态效果模板转换为已解析的 EffectSpec 列表。
     *
     * <p>约束求值说明：当前 {@code findMatch} 已完成 intent + state 的静态匹配，
     * 视为通过。{@code constraints} 字段暂不参与求值，交由后续阶段的
     * 条件 DSL 引擎处理。</p>
     *
     * <p>若 {@code branch_id} 在 {@code RuleRepository} 中找不到对应的
     * {@code case_outcome_branches} 条目，记录警告但**不阻断**——
     * 该配置错误应在契约诊断阶段被发现。</p>
     */
    private List<EffectSpec> convertEffects(ClinicalRule rule, String caseId) {
        if (rule.getEffects() == null || rule.getEffects().isEmpty()) {
            return new ArrayList<>();
        }

        List<EffectSpec> result = new ArrayList<>(rule.getEffects().size());
        // 同一次 validate 调用内所有 effect 共享一个 authorizationId（同一次授权）
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

            // 交叉校验（非致命）
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

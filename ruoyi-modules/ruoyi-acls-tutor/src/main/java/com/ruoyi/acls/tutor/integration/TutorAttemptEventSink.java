package com.ruoyi.acls.tutor.integration;

import com.ruoyi.acls.tutor.domain.BranchClinicalState;
import com.ruoyi.acls.tutor.domain.CaseOutcomeBranch;
import com.ruoyi.acls.tutor.domain.ScenarioBeat;
import com.ruoyi.acls.tutor.engine.AttemptEventProcessor;
import com.ruoyi.acls.tutor.engine.ScenarioBeatEvaluator;
import com.ruoyi.acls.tutor.repository.RuleRepository;
import com.ruoyi.acls.tutor.service.AssessmentEngine;
import com.ruoyi.ems.spi.AttemptEventSink;
import com.ruoyi.ems.spi.OutcomeBranchExecutor;
import com.ruoyi.ems.spi.dto.AttemptEventContext;
import com.ruoyi.ems.spi.dto.OutcomeBranchRequest;
import com.ruoyi.ems.spi.dto.OutcomeBranchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TutorAttemptEventSink implements AttemptEventSink {

    private final AttemptEventProcessor attemptEventProcessor;
    private final AssessmentEngine assessmentEngine;
    private final RuleRepository ruleRepository;
    private final ObjectProvider<OutcomeBranchExecutor> outcomeBranchExecutorProvider;

    @Override
    public void onTransitionCommitted(AttemptEventContext context) {
        if (context == null) {
            log.warn("[Tutor][AttemptEventSink] context 为 null，跳过");
            return;
        }

        log.info("[Tutor][AttemptEventSink] session={}, case={}, {} -> {}, intent={}, stateRev={}, rule={}, allowedEffects={}, vars={}",
            context.getSessionId(), context.getCaseType(),
            context.getFromState(), context.getToState(), context.getIntentCode(),
            context.getStateRevision(), context.getPrimaryRuleId(),
            context.getAllowedEffects() != null ? context.getAllowedEffects().size() : 0,
            context.getVariables() != null ? context.getVariables().size() : 0);

        try {
            ScenarioBeatEvaluator.SelectionInput input = new ScenarioBeatEvaluator.SelectionInput();
            input.caseId = context.getCaseType();
            input.currentState = context.getToState();
            input.intentCode = context.getIntentCode();
            input.primaryRuleId = context.getPrimaryRuleId();
            input.instructorDemoMode = false;
            input.allowedEffects = context.getAllowedEffects() != null
                ? context.getAllowedEffects()
                : new ArrayList<>();

            ScenarioBeatEvaluator.SelectionResult result =
                attemptEventProcessor.process(null, input);

            if (result == null) {
                log.warn("[Tutor][AttemptEventSink] beat 评估返回 null");
                return;
            }

            log.info("[Tutor][AttemptEventSink] beat 评估结果: selected={}, reason={}, conflict={}, candidates={}",
                result.selected != null ? result.selected.getBeatId() : null,
                result.reason, result.conflictDetected,
                result.candidates != null ? result.candidates.size() : 0);

            // 步骤7：只对命中/冲突写入评分证据
            boolean worthRecording = (result.selected != null) || result.conflictDetected;
            if (!worthRecording) {
                log.debug("[Tutor][Assessment] beat 未命中，跳过证据写入: session={}, reason={}",
                    context.getSessionId(), result.reason);
                return;
            }

            try {
                var record = assessmentEngine.recordBeatSelection(context.getSessionId(), result);
                if (record != null) {
                    log.info("[Tutor][Assessment] beat 证据已记录: session={}, beatId={}, outcomeRef={}, conflict={}, reason={}",
                        context.getSessionId(), record.getBeatId(), record.getOutcomeRef(),
                        record.isConflictDetected(), record.getReason());
                }
            } catch (Exception e) {
                log.warn("[Tutor][Assessment] beat 证据写入失败（不影响状态机）: {}", e.getMessage(), e);
            }

            // ★ 步骤9：应用 proposed_effect
            applyProposedEffectIfNeeded(context, result);

        } catch (Exception e) {
            log.warn("[Tutor][AttemptEventSink] beat 评估失败（不影响状态机）: {}", e.getMessage(), e);
        }
    }

    /**
     * 若 beat 命中的 proposed_effect 是 trigger_case_outcome_branch，
     * 调 ems SPI 让 branch 生效。
     */
    private void applyProposedEffectIfNeeded(AttemptEventContext context,
                                             ScenarioBeatEvaluator.SelectionResult result) {
        if (result == null || result.selected == null) return;

        ScenarioBeat beat = result.selected;
        ScenarioBeat.ProposedEffect pe = beat.getProposedEffect();
        if (pe == null || pe.getEffectId() == null) return;

        if (!"trigger_case_outcome_branch".equals(pe.getEffectId())) {
            log.debug("[Tutor][OutcomeBranch] 非 trigger_case_outcome_branch effect，跳过: effectId={}", pe.getEffectId());
            return;
        }

        String branchId = pe.getBranchId();
        if (branchId == null || branchId.isBlank()) {
            log.warn("[Tutor][OutcomeBranch] branchId 为空: beatId={}", beat.getBeatId());
            return;
        }

        CaseOutcomeBranch branch = ruleRepository.findBranch(context.getCaseType(), branchId);
        if (branch == null) {
            log.warn("[Tutor][OutcomeBranch] branch 未找到: case={}, branchId={}",
                context.getCaseType(), branchId);
            return;
        }

        Map<String, Object> vals = mapClinicalStateToVars(branch.getClinicalState());
        if (vals.isEmpty()) {
            log.warn("[Tutor][OutcomeBranch] clinical_state 无有效映射: branchId={}", branchId);
            return;
        }

        OutcomeBranchExecutor exec = outcomeBranchExecutorProvider.getIfAvailable();
        if (exec == null) {
            log.warn("[Tutor][OutcomeBranch] OutcomeBranchExecutor 未装配，跳过");
            return;
        }

        OutcomeBranchRequest req = new OutcomeBranchRequest();
        req.setSessionId(context.getSessionId());
        req.setCaseType(context.getCaseType());
        req.setBranchId(branchId);
        req.setSourceBeatId(beat.getBeatId());
        req.setClinicalStateValues(vals);

        OutcomeBranchResult res = exec.execute(req);
        if (res != null && res.isSuccess()) {
            log.info("[Tutor][OutcomeBranch] 应用成功: session={}, branchId={}, vars={}, {} -> {}, skipped={}",
                context.getSessionId(), branchId, vals.keySet(),
                res.getStateBefore(), res.getStateAfter(), res.isSkipped());
        } else {
            log.warn("[Tutor][OutcomeBranch] 应用失败: session={}, branchId={}, msg={}",
                context.getSessionId(), branchId,
                res != null ? res.getErrorMessage() : "null result");
        }
    }

    /**
     * BranchClinicalState → ems 变量名映射。
     *
     * <p>映射规则：
     * rhythm → phys.rhythm（值原样）
     * pulse → phys.hasPulse（present→true, absent→false）
     * consciousness → phys.consciousness（值原样）
     * perfusion / ventilation / oxygenation 暂不映射（待确认 ems 变量名）。</p>
     */
    private Map<String, Object> mapClinicalStateToVars(BranchClinicalState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) return m;

        if (s.getRhythm() != null) {
            m.put("phys.rhythm", s.getRhythm());
        }
        if (s.getPulse() != null) {
            m.put("phys.hasPulse", "present".equals(s.getPulse()));
        }
        if (s.getConsciousness() != null) {
            m.put("phys.consciousness", s.getConsciousness());
        }
        return m;
    }
}

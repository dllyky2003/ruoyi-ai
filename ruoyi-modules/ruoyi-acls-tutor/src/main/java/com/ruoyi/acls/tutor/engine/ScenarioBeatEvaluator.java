package com.ruoyi.acls.tutor.engine;

import com.ruoyi.acls.tutor.domain.ScenarioBeat;
import com.ruoyi.acls.tutor.repository.LessonPlanRepository;
import com.ruoyi.ems.spi.dto.EffectSpec;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 教案剧情节点评估器（V3.1 §6.3.1）。
 *
 * <p><b>职责</b>（仅此 5 条）：
 * <ol>
 *   <li>beat 的教案触发条件成立；</li>
 *   <li>当前训练模式允许该 beat（确定性演示仅在 INSTRUCTOR_DEMO 模式）；</li>
 *   <li>存在 {@code resolved=true} 的同一 effect_id + branch_id 授权；</li>
 *   <li>该授权的 authorized_by_rule_ids 与 beat 的 allowed_by_rule_ids 有交集；</li>
 *   <li>通过 selection_group 与 selection_priority 选出唯一候选。</li>
 * </ol>
 * </p>
 *
 * <p><b>不做</b>：不读取或重新执行 constraints（P0-7）；不改临床状态；
 * 不判断 rules 是否允许（那是 ClinicalRuleEngine 的职责）。</p>
 */
@Slf4j
@Component
public class ScenarioBeatEvaluator {

    @Autowired(required = false)
    private LessonPlanRepository lessonPlanRepository;

    @PostConstruct
    public void init() {
        if (lessonPlanRepository == null) {
            log.info("[Tutor][BeatEvaluator] LessonPlanRepository 未就绪，评估器待命");
            return;
        }
        log.info("[Tutor][BeatEvaluator] ScenarioBeatEvaluator 已就绪");
    }

    // ====================================================================
    // 主入口
    // ====================================================================

    public SelectionResult select(SelectionInput input) {
        SelectionResult r = new SelectionResult();

        if (input == null || input.caseId == null || lessonPlanRepository == null) {
            r.reason = "no input or repository";
            return r;
        }

        List<ScenarioBeat> all = lessonPlanRepository.getBeats(input.caseId);
        if (all.isEmpty()) {
            r.reason = "no beats loaded";
            return r;
        }

        // ---- 1. 收集允许的效果 key ----
        Set<String> allowedKeys = new HashSet<>();
        if (input.allowedEffects != null) {
            for (EffectSpec e : input.allowedEffects) {
                if (e != null && e.isResolved()
                    && e.getEffectId() != null && e.getBranchId() != null) {
                    allowedKeys.add(e.getEffectId() + "|" + e.getBranchId());
                }
            }
        }

        // ---- 2. 逐条过滤 ----
        List<ScenarioBeat> matched = new ArrayList<>();
        for (ScenarioBeat beat : all) {
            if (!"branch_selection".equals(beat.getBeatMode())) continue;
            if (isDeterministic(beat) && !input.instructorDemoMode) continue;
            if (!triggerMatches(beat.getTrigger(), input)) continue;
            if (!effectAuthorized(beat.getProposedEffect(), input.allowedEffects, allowedKeys)) continue;
            matched.add(beat);
            r.candidates.add(beat);
        }

        if (matched.isEmpty()) {
            r.reason = "no beats matched";
            return r;
        }

        // ---- 3. 按 selection_priority 降序 ----
        matched.sort((a, b) -> Integer.compare(priority(b), priority(a)));
        ScenarioBeat top = matched.get(0);

        // ---- 4. 冲突检测：同 group + 同 priority + 不同 outcome ----
        for (ScenarioBeat b : matched) {
            if (b == top) continue;
            if (Objects.equals(b.getSelectionGroup(), top.getSelectionGroup())
                && priority(b) == priority(top)
                && !Objects.equals(b.getCaseOutcomeRef(), top.getCaseOutcomeRef())) {
                r.conflictDetected = true;
                r.conflictBeatIds.add(b.getBeatId());
            }
        }

        r.selected = top;
        r.reason = r.conflictDetected ? "conflict: same group/priority, different outcome" : "ok";

        log.info("[Tutor][BeatEvaluator] case={}, state={}, intent={}, allowedEffects={}, matched={}, selected={}, outcomeRef={}, conflict={}",
            input.caseId, input.currentState, input.intentCode,
            allowedKeys.size(), matched.size(),
            top.getBeatId(), top.getCaseOutcomeRef(), r.conflictDetected);

        return r;
    }

    // ====================================================================
    // 内部判断
    // ====================================================================

    private static int priority(ScenarioBeat b) {
        return b.getSelectionPriority() != null ? b.getSelectionPriority() : 0;
    }

    private static boolean isDeterministic(ScenarioBeat beat) {
        ScenarioBeat.Trigger t = beat.getTrigger();
        return t != null && "deterministic_teaching_demo".equals(t.getType());
    }

    /**
     * 触发条件匹配（简化版，仅覆盖 V3.1 定义的 5 类触发）。
     */
    private boolean triggerMatches(ScenarioBeat.Trigger t, SelectionInput in) {
        if (t == null || t.getType() == null) return false;
        switch (t.getType()) {
            case "initial":
                return t.getState() == null || t.getState().equals(in.currentState);

            case "event":
                if (t.getInterventionId() != null) {
                    return t.getInterventionId().equals(in.intentCode);
                }
                if (t.getIntentCode() != null) {
                    return t.getIntentCode().equals(in.intentCode);
                }
                return false;

            case "rule_hit":
                return t.getRuleId() != null && t.getRuleId().equals(in.primaryRuleId);

            case "compound":
                if (t.getAllOf() == null || t.getAllOf().isEmpty()) return false;
                for (ScenarioBeat.Trigger sub : t.getAllOf()) {
                    if (!triggerMatches(sub, in)) return false;
                }
                return true;

            case "deterministic_teaching_demo":
                return in.instructorDemoMode;

            default:
                return false;
        }
    }

    /**
     * 效果授权匹配（V3.1 §6.3.1 第 3、4 条）。
     *
     * <p>条件：
     * <ul>
     *   <li>allowedEffects 中存在 resolved=true 的同一 effect_id + branch_id；</li>
     *   <li>该授权的 authorized_by_rule_ids 与 beat 的 allowed_by_rule_ids 有交集。</li>
     * </ul>
     * </p>
     */
    private boolean effectAuthorized(ScenarioBeat.ProposedEffect pe,
                                     List<EffectSpec> allowed,
                                     Set<String> allowedKeys) {
        if (pe == null || pe.getEffectId() == null || pe.getBranchId() == null) return false;
        if (!allowedKeys.contains(pe.getEffectId() + "|" + pe.getBranchId())) return false;

        if (pe.getAllowedByRuleIds() == null || pe.getAllowedByRuleIds().isEmpty()) return false;
        if (allowed == null) return false;

        for (EffectSpec e : allowed) {
            if (e == null || !e.isResolved()) continue;
            if (!Objects.equals(e.getEffectId(), pe.getEffectId())) continue;
            if (!Objects.equals(e.getBranchId(), pe.getBranchId())) continue;
            if (e.getAuthorizedByRuleIds() == null) continue;
            for (String rid : e.getAuthorizedByRuleIds()) {
                if (pe.getAllowedByRuleIds().contains(rid)) return true;
            }
        }
        return false;
    }

    // ====================================================================
    // 输入 / 输出
    // ====================================================================

    @Data
    public static class SelectionInput {
        public String caseId;
        public String currentState;
        public String intentCode;
        public String primaryRuleId;
        public boolean instructorDemoMode;
        public List<EffectSpec> allowedEffects;
    }

    @Data
    public static class SelectionResult {
        public ScenarioBeat selected;
        public List<ScenarioBeat> candidates = new ArrayList<>();
        public boolean conflictDetected;
        public List<String> conflictBeatIds = new ArrayList<>();
        public String reason;
    }
}

package com.ruoyi.ems.engine.scorer;

import com.ruoyi.ems.domain.dto.ClinicalAction;
import com.ruoyi.ems.domain.dto.ScoreResult;
import com.ruoyi.ems.domain.dto.TeamPerformance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 确定性评分引擎（硬性裁决，不受LLM幻觉影响）
 */
@Slf4j
@Service
public class ScoringEngine {

    private static final int PASS_THRESHOLD = 70;
    private static final int MAX_SCORE = 100;

    // 预期操作序列（稳定窄QRS心动过速）
    private static final List<String> EXPECTED_SEQUENCE = Arrays.asList(
        "vagal_maneuver",
        "adenosine_6mg",
        "adenosine_12mg",
        "beta_blocker_or_ccb",
        "expert_consult"
    );

    /**
     * 执行确定性评分
     */
    public ScoreResult evaluate(TeamPerformance performance) {
        log.info("开始确定性评分");
        ScoreResult result = ScoreResult.builder()
            .score(MAX_SCORE)
            .criticalErrors(new ArrayList<>())
            .warnings(new ArrayList<>())
            .dimensionScores(new LinkedHashMap<>())
            .build();

        // 1. 检测致命错误
        detectFatalErrors(performance, result);

        // 2. 评估准确性（30分）
        int assessmentScore = evaluateAssessment(performance, result);
        result.getDimensionScores().put("评估准确性", assessmentScore);

        // 3. 治疗顺序（35分）
        int sequenceScore = evaluateSequence(performance, result);
        result.getDimensionScores().put("治疗顺序", sequenceScore);

        // 4. 药物剂量（20分）
        int dosageScore = evaluateDosage(performance, result);
        result.getDimensionScores().put("药物剂量", dosageScore);

        // 5. 沟通质量（15分）
        int commScore = evaluateCommunication(performance, result);
        result.getDimensionScores().put("沟通质量", commScore);

        // 6. 计算加权总分
        int total = (int) Math.round(
            assessmentScore * 0.30 +
                sequenceScore * 0.35 +
                dosageScore * 0.20 +
                commScore * 0.15
        );
        result.setScore(Math.min(MAX_SCORE, Math.max(0, total)));

        // 7. 是否有致命错误
        boolean hasCritical = !result.getCriticalErrors().isEmpty();
        result.setPass(result.getScore() >= PASS_THRESHOLD && !hasCritical);

        log.info("评分完成: 总分={}, 通过={}, 致命错误数={}",
            result.getScore(), result.getPass(), result.getCriticalErrors().size());
        return result;
    }

    /**
     * 检测致命错误
     */
    private void detectFatalErrors(TeamPerformance performance, ScoreResult result) {
        var judgments = performance.getJudgments();
        var actions = performance.getActions();
        List<String> details = actions.stream().map(ClinicalAction::getDetail).collect(Collectors.toList());

        // 致命1: 未识别窄QRS
        if (judgments.getQrsWidth() == null || !"NARROW".equals(judgments.getQrsWidth())) {
            result.getCriticalErrors().add("CRITICAL: 未识别窄QRS特征，导致算法路径错误");
            result.setScore(result.getScore() - 30);
        }

        // 致命2: 跳过迷走神经刺激
        boolean hasVagal = details.contains("vagal_maneuver");
        boolean hasAdenosine = details.contains("adenosine");
        if (!hasVagal && hasAdenosine) {
            result.getCriticalErrors().add("CRITICAL: 跳过迷走神经刺激直接使用腺苷");
            result.setScore(result.getScore() - 20);
        }

        // 致命3: WPW综合征使用禁忌药物
        if (Boolean.TRUE.equals(judgments.getWpwSyndrome())) {
            boolean hasContraindicated = details.stream().anyMatch(d ->
                d.contains("adenosine") || d.contains("digoxin") ||
                    d.contains("beta_blocker") || d.contains("ccb") ||
                    d.contains("amiodarone"));
            if (hasContraindicated) {
                result.getCriticalErrors().add("CRITICAL: WPW综合征使用了禁忌药物");
                result.setScore(result.getScore() - 40);
            }
        }
    }

    /**
     * 评估准确性（30分）
     */
    private int evaluateAssessment(TeamPerformance performance, ScoreResult result) {
        var judgments = performance.getJudgments();
        int score = 30;

        if (!Boolean.TRUE.equals(judgments.getHeartRateGt150())) {
            score -= 8;
            result.getWarnings().add("未正确识别心率>150");
        }
        if (!Boolean.TRUE.equals(judgments.getPulsePresent())) {
            score -= 8;
            result.getWarnings().add("未确认脉搏存在");
        }
        if (judgments.getUnstableSigns() != null && !judgments.getUnstableSigns().isEmpty()) {
            score -= 6;
            result.getWarnings().add("存在不稳定体征但未正确处理");
        }
        if (judgments.getQrsWidth() == null || "UNKNOWN".equals(judgments.getQrsWidth())) {
            score -= 8;
            result.getWarnings().add("未明确QRS宽度判断");
        }

        return Math.max(0, score);
    }

    /**
     * 治疗顺序（35分）
     */
    private int evaluateSequence(TeamPerformance performance, ScoreResult result) {
        List<String> details = performance.getActions().stream()
            .map(ClinicalAction::getDetail)
            .collect(Collectors.toList());

        // 计算预期序列的匹配度
        int matchCount = 0;
        int expectedIdx = 0;
        for (String detail : details) {
            if (expectedIdx < EXPECTED_SEQUENCE.size() &&
                detail.equals(EXPECTED_SEQUENCE.get(expectedIdx))) {
                matchCount++;
                expectedIdx++;
            }
        }

        double matchRate = EXPECTED_SEQUENCE.isEmpty() ? 1.0 :
            (double) matchCount / EXPECTED_SEQUENCE.size();
        int score = (int) Math.round(matchRate * 35);

        // 检查顺序错乱（例如：先给药再迷走）
        if (details.contains("adenosine") && details.contains("vagal_maneuver")) {
            int vagalIdx = details.indexOf("vagal_maneuver");
            int adenoIdx = details.indexOf("adenosine");
            if (adenoIdx < vagalIdx && vagalIdx != -1 && adenoIdx != -1) {
                score -= 10;
                result.getWarnings().add("给药顺序错误：腺苷在迷走神经刺激之前");
            }
        }

        if (matchRate < 0.5) {
            result.getWarnings().add("治疗顺序严重偏离预期路径");
        }

        return Math.max(0, Math.min(35, score));
    }

    /**
     * 药物剂量（20分）
     */
    private int evaluateDosage(TeamPerformance performance, ScoreResult result) {
        int score = 20;
        List<ClinicalAction> adenosineActions = performance.getActions().stream()
            .filter(a -> "adenosine".equals(a.getDetail()))
            .collect(Collectors.toList());

        if (adenosineActions.isEmpty()) {
            return 10; // 未使用腺苷，部分得分
        }

        for (ClinicalAction action : adenosineActions) {
            String value = action.getValue();
            if (value == null) {
                score -= 5;
                result.getWarnings().add("腺苷剂量未明确");
                continue;
            }

            if (!value.matches(".*6.*") && !value.matches(".*12.*")) {
                score -= 10;
                result.getCriticalErrors().add("CRITICAL: 腺苷剂量异常: " + value + "（应为6mg或12mg）");
            }

            // 检查冲管（根据sourceText）
            String source = action.getSourceText() != null ? action.getSourceText() : "";
            if (!source.contains("冲管") && !source.contains("盐水") && !source.contains("flush")) {
                score -= 5;
                result.getWarnings().add("腺苷给药后未提及生理盐水冲管");
            }
        }

        // 首次剂量应为6mg
        ClinicalAction firstAdenosine = adenosineActions.get(0);
        if (firstAdenosine.getValue() != null && firstAdenosine.getValue().contains("12")) {
            score -= 8;
            result.getWarnings().add("首次腺苷剂量应为6mg，但使用了12mg");
        }

        return Math.max(0, Math.min(20, score));
    }

    /**
     * 沟通质量（15分）
     */
    private int evaluateCommunication(TeamPerformance performance, ScoreResult result) {
        int score = 15;
        List<ClinicalAction> actions = performance.getActions();

        // 是否有闭环沟通
        boolean hasClosedLoop = actions.stream()
            .anyMatch(a -> "COMMUNICATION".equals(a.getActionType()) &&
                a.getDetail() != null &&
                (a.getDetail().contains("confirm") || a.getDetail().contains("确认")));
        if (!hasClosedLoop) {
            score -= 5;
            result.getWarnings().add("缺少闭环沟通确认");
        }

        // 是否清晰报告操作
        long interventionCount = actions.stream()
            .filter(a -> "INTERVENTION".equals(a.getActionType()))
            .count();
        long reportCount = actions.stream()
            .filter(a -> "COMMUNICATION".equals(a.getActionType()) ||
                (a.getActionType() != null && a.getActionType().contains("REPORT")))
            .count();

        if (interventionCount > 0 && reportCount < interventionCount / 2) {
            score -= 3;
            result.getWarnings().add("干预操作报告不足");
        }

        // 是否包含专家会诊沟通
        boolean hasExpertConsult = actions.stream()
            .anyMatch(a -> "expert_consult".equals(a.getDetail()));
        if (!hasExpertConsult) {
            score -= 3;
            result.getWarnings().add("未提及专家会诊");
        }

        return Math.max(0, Math.min(15, score));
    }
}

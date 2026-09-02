package com.ruoyi.ems.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.domain.dto.*;
import com.ruoyi.ems.domain.entity.EmsAgentEvaluation;
import com.ruoyi.ems.domain.entity.EmsEvaluation;
import com.ruoyi.ems.engine.multiagent.dto.AgentInput;

import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import com.ruoyi.ems.engine.multiagent.supervisor.SupervisorAgent;
import com.ruoyi.ems.engine.multiagent.supervisor.SupervisorResult;
import com.ruoyi.ems.engine.scorer.ScoringEngine;
import com.ruoyi.ems.mapper.EmsAgentEvaluationMapper;
import com.ruoyi.ems.mapper.EmsEvaluationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor   // 构造器注入所有 final 字段
public class EvaluationService {

    private final EmsEvaluationMapper evaluationMapper;          // 注入 Mapper
    private final EmsAgentEvaluationMapper agentEvaluationMapper;
    private final SessionService sessionService;
    private final SupervisorAgent supervisorAgent;
    private final ScoringEngine scoringEngine;
    private final ObjectMapper objectMapper;

    @Transactional
    public EmsEvaluation triggerFinalEvaluation(String sessionId) {
        log.info("触发最终评估: {}", sessionId);

        // 1. 获取会话数据
        TeamPerformance performance = sessionService.getPerformance(sessionId);
        if (performance == null) {
            log.warn("会话 {} 无性能数据，使用空数据", sessionId);
            performance = TeamPerformance.builder()
                .actions(new ArrayList<>())
                .judgments(new ScenarioJudgment())
                .build();
        }

        // 2. 执行多智能体评估
        AgentInput agentInput = AgentInput.builder()
            .sessionId(sessionId)
            .performance(performance)
            .traces((List<Map<String, Object>>) sessionService.getSessionContext(sessionId).get("traces"))
            .scenarioConfig(new HashMap<>())
            .build();

        SupervisorResult supervisorResult = supervisorAgent.execute(agentInput);

        // 3. 执行确定性规则评分
        ScoreResult ruleResult = scoringEngine.evaluate(performance);

        // 4. 融合最终结果
        MultiAgentEvaluationResult finalResult = MultiAgentEvaluationResult.builder()
            .agentFusionScore(supervisorResult.getFusedScore() != null ? supervisorResult.getFusedScore().intValue() : 70)
            .ruleScore(ruleResult.getScore())
            .criticalErrors(ruleResult.getCriticalErrors())
            .warnings(ruleResult.getWarnings())
            .agentOutputs(supervisorResult.getAgentOutputs())
            .build();

        // 融合策略：智能体60% + 规则40%
        double finalScore = finalResult.getAgentFusionScore() * 0.6 + finalResult.getRuleScore() * 0.4;
        if (ruleResult.getCriticalErrors() != null && !ruleResult.getCriticalErrors().isEmpty()) {
            finalScore = Math.min(finalScore, 50);
        }
        int finalScoreInt = (int) Math.round(Math.max(0, Math.min(100, finalScore)));
        finalResult.setFinalScore(finalScoreInt);
        finalResult.setPass(finalScoreInt >= 70 &&
            (ruleResult.getCriticalErrors() == null || ruleResult.getCriticalErrors().isEmpty()));

        // 生成评语
        String report = generateReport(finalResult, performance);

        // 5. 保存评估结果（使用 mapper）
        EmsEvaluation evaluation = new EmsEvaluation();
        evaluation.setId(UUID.randomUUID().toString().replace("-", ""));
        evaluation.setSessionId(sessionId);
        evaluation.setTotalScore(finalResult.getFinalScore());
        evaluation.setIsPass(finalResult.getPass());
        evaluation.setDimensionScores(toJson(ruleResult.getDimensionScores()));
        evaluation.setCriticalErrors(toJson(ruleResult.getCriticalErrors()));
        evaluation.setWarnings(toJson(ruleResult.getWarnings()));
        evaluation.setReportText(report);
        evaluation.setAgentFusionScore(finalResult.getAgentFusionScore());
        evaluation.setAgentDetails(toJson(supervisorResult.getAgentOutputs()));
        evaluation.setCreateTime(LocalDateTime.now());

        evaluationMapper.insert(evaluation);   // 直接使用 mapper

        // 6. 保存各智能体详细记录
        saveAgentEvaluations(sessionId, supervisorResult);

        log.info("评估完成: sessionId={}, score={}, pass={}",
            sessionId, finalResult.getFinalScore(), finalResult.getPass());

        return evaluation;
    }

    /**
     * 保存智能体评估记录（使用 mapper）
     */
    private void saveAgentEvaluations(String sessionId, SupervisorResult supervisorResult) {
        if (supervisorResult.getAgentOutputs() == null) return;

        for (Map.Entry<String, AgentOutput> entry : supervisorResult.getAgentOutputs().entrySet()) {
            EmsAgentEvaluation agentEval = new EmsAgentEvaluation();
            agentEval.setId(UUID.randomUUID().toString().replace("-", ""));
            agentEval.setSessionId(sessionId);
            agentEval.setAgentName(entry.getKey());
            agentEval.setAgentScore(entry.getValue().getScore() != null ? entry.getValue().getScore() : 0);
            agentEval.setRawOutput(toJson(entry.getValue()));
            agentEval.setTraceLog(entry.getValue().getTrace());
            agentEval.setCreateTime(LocalDateTime.now());

            agentEvaluationMapper.insert(agentEval);   // 直接使用 mapper
        }
    }

    /**
     * 生成报告
     */
    private String generateReport(MultiAgentEvaluationResult result, TeamPerformance performance) {
        StringBuilder report = new StringBuilder();

        report.append("【ACLS心动过速算法考核评价报告】\n");
        report.append("总分: ").append(result.getFinalScore()).append("/100\n");
        report.append("判定: ").append(result.getPass() ? "✅ 通过" : "❌ 不通过").append("\n\n");

        report.append("【各维度评估】\n");
        if (result.getAgentOutputs() != null) {
            for (Map.Entry<String, AgentOutput> entry : result.getAgentOutputs().entrySet()) {
                AgentOutput output = entry.getValue();
                report.append("- ").append(entry.getKey())
                    .append(": ").append(output.getScore()).append("分\n");
                report.append("  理由: ").append(output.getReasoning()).append("\n");
            }
        }

        report.append("\n【规则引擎检测】\n");
        if (result.getCriticalErrors() != null && !result.getCriticalErrors().isEmpty()) {
            report.append("致命错误:\n");
            for (String error : result.getCriticalErrors()) {
                report.append("  ❌ ").append(error).append("\n");
            }
        }

        report.append("\n【教学建议】\n");
        if (result.getAgentOutputs() != null) {
            for (Map.Entry<String, AgentOutput> entry : result.getAgentOutputs().entrySet()) {
                AgentOutput output = entry.getValue();
                if (output.getRecommendations() != null) {
                    for (String rec : output.getRecommendations()) {
                        report.append("- ").append(rec).append("\n");
                    }
                }
            }
        }

        return report.toString();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 获取评估结果（使用 mapper 查询）
     */
    public EmsEvaluation getEvaluationBySessionId(String sessionId) {
        // 使用 MyBatis-Plus 的 Wrapper 查询
        return evaluationMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmsEvaluation>()
                .eq(EmsEvaluation::getSessionId, sessionId)
        );
    }
}

package com.ruoyi.ems.engine.multiagent.supervisor;


import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 加权融合策略
 */
@Slf4j
public class WeightedFusionStrategy implements FusionStrategy {

    // 智能体权重配置
    private static final Map<String, Double> WEIGHTS = Map.of(
        "临床评估智能体", 0.30,
        "路径决策智能体", 0.35,
        "安全稽查智能体", 0.20,
        "教学反馈智能体", 0.15
    );

    // 默认权重
    private static final double DEFAULT_WEIGHT = 0.25;

    @Override
    public SupervisorResult fuse(Map<String, AgentOutput> outputs) {
        log.info("开始融合 {} 个智能体的输出", outputs.size());

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        boolean hasCritical = false;
        List<String> allRecommendations = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();
        List<String> criticalViolations = new ArrayList<>();

        for (Map.Entry<String, AgentOutput> entry : outputs.entrySet()) {
            String agentName = entry.getKey();
            AgentOutput output = entry.getValue();

            // 获取权重
            double weight = WEIGHTS.getOrDefault(agentName, DEFAULT_WEIGHT);
            weightedSum += (output.getScore() != null ? output.getScore() : 50) * weight;
            totalWeight += weight;

            // 收集警告和建议
            if (output.getWarnings() != null) {
                allWarnings.addAll(output.getWarnings());
            }
            if (output.getRecommendations() != null) {
                allRecommendations.addAll(output.getRecommendations());
            }

            // 检测致命违规
            if (output.getHasCriticalViolation() != null && output.getHasCriticalViolation()) {
                hasCritical = true;
                if (output.getCriticalViolations() != null) {
                    criticalViolations.addAll(output.getCriticalViolations());
                }
            }
        }

        // 计算融合得分
        double fusedScore = totalWeight > 0 ? weightedSum / totalWeight : 50.0;
        fusedScore = Math.round(Math.max(0, Math.min(100, fusedScore)));

        // 去重
        allRecommendations = allRecommendations.stream().distinct().collect(Collectors.toList());
        allWarnings = allWarnings.stream().distinct().collect(Collectors.toList());

        // 如果存在致命违规，得分打折
        if (hasCritical && !criticalViolations.isEmpty()) {
            fusedScore = Math.max(0, fusedScore - 20);
            log.warn("检测到致命违规: {}", criticalViolations);
        }

        return SupervisorResult.builder()
            .fusedScore(fusedScore)
            .hasCriticalViolation(hasCritical)
            .aggregatedRecommendations(allRecommendations)
            .aggregatedWarnings(allWarnings)
            .agentOutputs(outputs)
            .build();
    }
}

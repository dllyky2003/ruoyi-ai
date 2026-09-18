package com.ruoyi.ems.engine.multiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 智能体统一输出对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOutput {

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 评分 0-100
     */
    private Integer score;

    /**
     * 推理过程说明
     */
    private String reasoning;

    /**
     * 详细发现
     */
    private Map<String, Object> detailedFindings;

    /**
     * 警告列表
     */
    private List<String> warnings;

    /**
     * 建议列表
     */
    private List<String> recommendations;

    /**
     * 是否存在致命违规
     */
    private Boolean hasCriticalViolation;

    /**
     * 致命违规详情
     */
    private List<String> criticalViolations;

    /**
     * 完整分析轨迹（用于可解释性）
     */
    private String trace;

    /**
     * 各维度评分（子维度）
     */
    private Map<String, Integer> subScores;

    /**
     * 检查是否通过（得分 >= 60 且无致命违规）
     */
    public boolean isPass() {
        return (score != null && score >= 60) &&
            (hasCriticalViolation == null || !hasCriticalViolation);
    }

    /**
     * 合并多个智能体输出（用于汇总）
     */
    public static AgentOutput merge(List<AgentOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }

        double avgScore = outputs.stream()
            .filter(o -> o.getScore() != null)
            .mapToInt(AgentOutput::getScore)
            .average()
            .orElse(50);

        List<String> allWarnings = outputs.stream()
            .flatMap(o -> o.getWarnings() != null ? o.getWarnings().stream() : java.util.stream.Stream.empty())
            .distinct()
            .toList();

        List<String> allRecommendations = outputs.stream()
            .flatMap(o -> o.getRecommendations() != null ? o.getRecommendations().stream() : java.util.stream.Stream.empty())
            .distinct()
            .toList();

        boolean hasCritical = outputs.stream()
            .anyMatch(o -> Boolean.TRUE.equals(o.getHasCriticalViolation()));

        return AgentOutput.builder()
            .agentName("MERGED")
            .score((int) Math.round(avgScore))
            .reasoning("合并 " + outputs.size() + " 个智能体结果")
            .warnings(allWarnings)
            .recommendations(allRecommendations)
            .hasCriticalViolation(hasCritical)
            .build();
    }

    @Override
    public String toString() {
        return String.format("AgentOutput{agent='%s', score=%d, critical=%s}",
            agentName, score, hasCriticalViolation);
    }
}

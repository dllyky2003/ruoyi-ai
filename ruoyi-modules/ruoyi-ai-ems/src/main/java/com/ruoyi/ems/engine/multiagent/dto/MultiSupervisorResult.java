package com.ruoyi.ems.engine.multiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Supervisor 融合结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiSupervisorResult {

    /**
     * 融合得分
     */
    private Double fusedScore;

    /**
     * 是否存在致命违规
     */
    private Boolean hasCriticalViolation;

    /**
     * 汇总建议
     */
    private List<String> aggregatedRecommendations;

    /**
     * 汇总警告
     */
    private List<String> aggregatedWarnings;

    /**
     * 各智能体原始输出
     */
    private Map<String, AgentOutput> agentOutputs;

    /**
     * 总耗时（毫秒）
     */
    private Long totalTimeMs;

    /**
     * 获取指定智能体的输出
     */
    public AgentOutput getAgentOutput(String agentName) {
        return agentOutputs != null ? agentOutputs.get(agentName) : null;
    }

    /**
     * 检查是否通过
     */
    public boolean isPass() {
        return (fusedScore != null && fusedScore >= 60) &&
            (hasCriticalViolation == null || !hasCriticalViolation);
    }

    @Override
    public String toString() {
        return String.format("SupervisorResult{fusedScore=%.1f, critical=%s, agents=%d, time=%dms}",
            fusedScore != null ? fusedScore : 0,
            hasCriticalViolation,
            agentOutputs != null ? agentOutputs.size() : 0,
            totalTimeMs != null ? totalTimeMs : 0
        );
    }
}

package com.ruoyi.ems.engine.multiagent.supervisor;

import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisorResult {
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
     * 各智能体原始输出（修正：应为 AgentOutput 而不是 AgentInput）
     */
    private Map<String, AgentOutput> agentOutputs;   // 修改这里

    /**
     * 总耗时（毫秒）
     */
    private Long totalTimeMs;
}

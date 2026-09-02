package com.ruoyi.ems.domain.dto;

import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 多智能体综合评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAgentEvaluationResult {
    /**
     * 智能体融合得分
     */
    private Integer agentFusionScore;

    /**
     * 规则引擎得分
     */
    private Integer ruleScore;

    /**
     * 最终得分（融合后）
     */
    private Integer finalScore;

    /**
     * 是否通过
     */
    private Boolean pass;

    /**
     * 致命错误（来自规则引擎）
     */
    private List<String> criticalErrors;

    /**
     * 各智能体详细输出
     */
    private Map<String, AgentOutput> agentOutputs;
    private List<String> warnings;
    /**
     * 综合评语草稿
     */
    private String draftReport;
}

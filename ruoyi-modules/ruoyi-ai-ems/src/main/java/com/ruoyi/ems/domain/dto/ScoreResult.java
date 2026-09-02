package com.ruoyi.ems.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 规则引擎评分结果（确定性裁决）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResult {
    /**
     * 规则引擎原始得分
     */
    private Integer score;

    /**
     * 是否通过
     */
    private Boolean pass;

    /**
     * 致命错误列表
     */
    private List<String> criticalErrors;

    /**
     * 警告列表
     */
    private List<String> warnings;

    /**
     * 各维度得分
     */
    private Map<String, Integer> dimensionScores;
}

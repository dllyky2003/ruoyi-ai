package com.ruoyi.ems.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentCandidate {
    private String intentCode;          // 候选意图码
    private Double confidence;          // 该候选的置信度 "high"/"medium"/"low" 或分数 "0.85"
    private String reason;              // 可选：匹配原因（供调试/日志）
    private Double score;               // 可选：数值分数，便于精确排序
}

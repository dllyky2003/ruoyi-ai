package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentCandidate {
    private String intentCode;
    private String nameCn;
    private Double confidence;
    private String reason;
    private Double score;
    private Boolean valid;
    /**
     * 从分数构建候选（用于向量分类）
     */
    public IntentCandidate(String intentCode, double score) {
        this.intentCode = intentCode;
        this.score = score;
        this.confidence = score;
        this.reason = String.format("相似度: %.3f", score);
    }

    /**
     * 从分数构建候选（静态工厂方法）
     */
    public static IntentCandidate fromScore(String intentCode, double score) {
        return new IntentCandidate(intentCode, score);
    }

    /**
     * 构建排名描述
     */
    public String getRankDescription(int rank) {
        return String.format("#%d %s (%.3f)", rank, intentCode, score != null ? score : 0);
    }

    /**
     * 判断候选是否有效（分数存在且大于 0）
     */
    @JsonIgnore
    public boolean isValid() {
        return score != null && score > 0;
    }
}

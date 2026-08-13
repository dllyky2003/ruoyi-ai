package org.ruoyi.ecg.domain.intent;

/**
 * 候选意图
 */
public class CandidateIntent {
    /** 意图ID */
    private String intentId;

    /** 匹配分数 */
    private float score;

    /** 意图定义 */
    private EcgIntentDefinition intentDefinition;

    /** 提取的参数 */
    private java.util.Map<String, Object> extractedParams;

    // ====== Getter / Setter ======
    public String getIntentId() {
        return intentId;
    }

    public void setIntentId(String intentId) {
        this.intentId = intentId;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public EcgIntentDefinition getIntentDefinition() {
        return intentDefinition;
    }

    public void setIntentDefinition(EcgIntentDefinition intentDefinition) {
        this.intentDefinition = intentDefinition;
    }

    public java.util.Map<String, Object> getExtractedParams() {
        return extractedParams;
    }

    public void setExtractedParams(java.util.Map<String, Object> extractedParams) {
        this.extractedParams = extractedParams;
    }
}

package org.ruoyi.ecg.domain.intent;

import java.util.List;
import java.util.Map;

/**
 * 意图识别结果
 */
public class RecognitionResult {
    /** 是否成功 */
    private boolean success;

    /** 意图ID */
    private String intentId;

    /** 意图描述 */
    private String intentDescription;

    /** 置信度 */
    private float confidence;

    /** 提取的参数 */
    private Map<String, Object> params;

    /** 缺失的槽位列表 */
    private List<String> missingSlots;

    /** 推荐的后续意图列表 */
    private List<String> suggestedNextIntents;

    /** 消息 */
    private String message;

    // ====== 构造方法 ======
    private RecognitionResult() {}

    public static RecognitionResult success(
            EcgIntentDefinition intent,
            float confidence,
            Map<String, Object> params,
            List<String> missingSlots,
            List<String> suggestedNext) {
        RecognitionResult result = new RecognitionResult();
        result.setSuccess(true);
        result.setIntentId(intent.getIntentId());
        result.setIntentDescription(intent.getDescription());
        result.setConfidence(confidence);
        result.setParams(params);
        result.setMissingSlots(missingSlots);
        result.setSuggestedNextIntents(suggestedNext);
        return result;
    }

    public static RecognitionResult unknown(String message) {
        RecognitionResult result = new RecognitionResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    // ====== Getter / Setter ======
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getIntentId() {
        return intentId;
    }

    public void setIntentId(String intentId) {
        this.intentId = intentId;
    }

    public String getIntentDescription() {
        return intentDescription;
    }

    public void setIntentDescription(String intentDescription) {
        this.intentDescription = intentDescription;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public List<String> getMissingSlots() {
        return missingSlots;
    }

    public void setMissingSlots(List<String> missingSlots) {
        this.missingSlots = missingSlots;
    }

    public List<String> getSuggestedNextIntents() {
        return suggestedNextIntents;
    }

    public void setSuggestedNextIntents(List<String> suggestedNextIntents) {
        this.suggestedNextIntents = suggestedNextIntents;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

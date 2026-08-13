package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.util.Map;

/**
 * AI解析层输出契约：将自然语言指令转换为结构化请求
 * 用于 FlowEngine 的输入
 *
 * @author ecg-system
 */
public class ActionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String caseId;
    private String ruleVersion;
    private String timestamp;
    private String intent;          // 固定 "EXECUTE_ACTION"
    private String actionType;      // defibrillate / administer_drug / ...
    private String targetRole;      // npc_nurse / npc_doctor / ...
    private double confidence;
    private Map<String, Object> params;
    private String originalTranscript;
    private String audioFileUrl;
    private boolean isCompoundCommand;
    private DeliveryMetrics deliveryMetrics;

    // ====== 构造方法 ======
    public ActionRequest() {}

    /**
     * 全参构造（用于快速构建）
     */
    public ActionRequest(String sessionId, String caseId, String ruleVersion,
                         String timestamp, String intent, String actionType,
                         String targetRole, double confidence, Map<String, Object> params,
                         String originalTranscript, String audioFileUrl,
                         boolean isCompoundCommand, DeliveryMetrics deliveryMetrics) {
        this.sessionId = sessionId;
        this.caseId = caseId;
        this.ruleVersion = ruleVersion;
        this.timestamp = timestamp;
        this.intent = intent;
        this.actionType = actionType;
        this.targetRole = targetRole;
        this.confidence = confidence;
        this.params = params;
        this.originalTranscript = originalTranscript;
        this.audioFileUrl = audioFileUrl;
        this.isCompoundCommand = isCompoundCommand;
        this.deliveryMetrics = deliveryMetrics;
    }

    // ====== Getter / Setter ======
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public String getOriginalTranscript() { return originalTranscript; }
    public void setOriginalTranscript(String originalTranscript) { this.originalTranscript = originalTranscript; }

    public String getAudioFileUrl() { return audioFileUrl; }
    public void setAudioFileUrl(String audioFileUrl) { this.audioFileUrl = audioFileUrl; }

    public boolean isCompoundCommand() { return isCompoundCommand; }
    public void setCompoundCommand(boolean compoundCommand) { isCompoundCommand = compoundCommand; }

    public DeliveryMetrics getDeliveryMetrics() { return deliveryMetrics; }
    public void setDeliveryMetrics(DeliveryMetrics deliveryMetrics) { this.deliveryMetrics = deliveryMetrics; }

    // ====== 静态构造器（替代 Builder，避免 Lombok） ======
    public static ActionRequestBuilder builder() {
        return new ActionRequestBuilder();
    }

    /**
     * 内部 Builder 类（手动实现）
     */
    public static class ActionRequestBuilder {
        private String sessionId;
        private String caseId;
        private String ruleVersion;
        private String timestamp;
        private String intent;
        private String actionType;
        private String targetRole;
        private double confidence;
        private Map<String, Object> params;
        private String originalTranscript;
        private String audioFileUrl;
        private boolean isCompoundCommand;
        private DeliveryMetrics deliveryMetrics;

        public ActionRequestBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        public ActionRequestBuilder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }
        public ActionRequestBuilder ruleVersion(String ruleVersion) {
            this.ruleVersion = ruleVersion;
            return this;
        }
        public ActionRequestBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        public ActionRequestBuilder intent(String intent) {
            this.intent = intent;
            return this;
        }
        public ActionRequestBuilder actionType(String actionType) {
            this.actionType = actionType;
            return this;
        }
        public ActionRequestBuilder targetRole(String targetRole) {
            this.targetRole = targetRole;
            return this;
        }
        public ActionRequestBuilder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }
        public ActionRequestBuilder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }
        public ActionRequestBuilder originalTranscript(String originalTranscript) {
            this.originalTranscript = originalTranscript;
            return this;
        }
        public ActionRequestBuilder audioFileUrl(String audioFileUrl) {
            this.audioFileUrl = audioFileUrl;
            return this;
        }
        public ActionRequestBuilder isCompoundCommand(boolean isCompoundCommand) {
            this.isCompoundCommand = isCompoundCommand;
            return this;
        }
        public ActionRequestBuilder deliveryMetrics(DeliveryMetrics deliveryMetrics) {
            this.deliveryMetrics = deliveryMetrics;
            return this;
        }

        public ActionRequest build() {
            return new ActionRequest(sessionId, caseId, ruleVersion, timestamp,
                    intent, actionType, targetRole, confidence, params,
                    originalTranscript, audioFileUrl, isCompoundCommand, deliveryMetrics);
        }
    }
}

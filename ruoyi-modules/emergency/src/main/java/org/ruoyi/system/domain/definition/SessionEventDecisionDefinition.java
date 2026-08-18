package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 会话事件决策结果（对应 emergency_session_event_log.decision_json）
 * 存储规则引擎对事件的处理决策
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
public class SessionEventDecisionDefinition {

    // ==================== 决策结果 ====================

    /**
     * 决策是否通过
     */
    @JsonProperty("approved")
    private Boolean approved;

    /**
     * 决策结果码（如 SUCCESS、FAIL、WARNING）
     */
    @JsonProperty("resultCode")
    private String resultCode;

    /**
     * 决策消息
     */
    @JsonProperty("message")
    private String message;

    /**
     * 决策时间
     */
    @JsonProperty("decidedAt")
    private String decidedAt;

    /**
     * 决策者类型（rule_engine / instructor / system）
     */
    @JsonProperty("deciderType")
    private String deciderType;

    /**
     * 决策者ID（如规则ID、教官ID）
     */
    @JsonProperty("deciderId")
    private String deciderId;

    // ==================== 触发的规则 ====================

    /**
     * 触发的规则列表
     */
    @JsonProperty("triggeredRules")
    private java.util.List<TriggeredRule> triggeredRules;

    /**
     * 应用的事件列表
     */
    @JsonProperty("appliedEvents")
    private java.util.List<AppliedEvent> appliedEvents;

    // ==================== 状态变更 ====================

    /**
     * 阶段切换目标
     */
    @JsonProperty("targetPhaseId")
    private String targetPhaseId;

    /**
     * 轨迹节点切换目标
     */
    @JsonProperty("targetTrajectoryNodeId")
    private String targetTrajectoryNodeId;

    /**
     * 患者状态变更（快照）
     */
    @JsonProperty("patientStateDelta")
    private Map<String, Object> patientStateDelta;

    /**
     * 需要追加的时间线消息
     */
    @JsonProperty("timelineMessages")
    private java.util.List<TimelineMessage> timelineMessages;

    /**
     * 标记的证据码列表
     */
    @JsonProperty("markedEvidenceCodes")
    private java.util.List<String> markedEvidenceCodes;

    /**
     * 扩展数据
     */
    @JsonProperty("extras")
    private Map<String, Object> extras;

    // ==================== 内部嵌套类 ====================

    @Data
    public static class TriggeredRule {
        /**
         * 规则ID
         */
        @JsonProperty("ruleId")
        private String ruleId;

        /**
         * 规则名称
         */
        @JsonProperty("ruleName")
        private String ruleName;

        /**
         * 规则触发时间
         */
        @JsonProperty("triggeredAt")
        private String triggeredAt;

        /**
         * 规则触发结果
         */
        @JsonProperty("result")
        private String result;

        /**
         * 规则触发的条件评估详情
         */
        @JsonProperty("conditionDetails")
        private Map<String, Object> conditionDetails;
    }

    @Data
    public static class AppliedEvent {
        /**
         * 事件ID
         */
        @JsonProperty("eventId")
        private String eventId;

        /**
         * 事件名称
         */
        @JsonProperty("eventName")
        private String eventName;

        /**
         * 事件类型
         */
        @JsonProperty("eventType")
        private String eventType;

        /**
         * 事件应用时间
         */
        @JsonProperty("appliedAt")
        private String appliedAt;

        /**
         * 事件效果描述
         */
        @JsonProperty("effects")
        private java.util.List<String> effects;
    }

    @Data
    public static class TimelineMessage {
        /**
         * 消息内容
         */
        @JsonProperty("message")
        private String message;

        /**
         * 受众（instructor/learner/all）
         */
        @JsonProperty("audience")
        private String audience;

        /**
         * 消息时间
         */
        @JsonProperty("timestamp")
        private String timestamp;
    }

    // ==================== 便捷查询方法 ====================

    /**
     * 判断决策是否成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(approved) && "SUCCESS".equals(resultCode);
    }

    /**
     * 判断决策是否失败
     */
    public boolean isFailure() {
        return !Boolean.TRUE.equals(approved) && "FAIL".equals(resultCode);
    }

    /**
     * 获取所有触发的规则ID列表
     */
    public java.util.List<String> getTriggeredRuleIds() {
        if (triggeredRules == null) return java.util.Collections.emptyList();
        return triggeredRules.stream()
            .map(TriggeredRule::getRuleId)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取所有应用的事件ID列表
     */
    public java.util.List<String> getAppliedEventIds() {
        if (appliedEvents == null) return java.util.Collections.emptyList();
        return appliedEvents.stream()
            .map(AppliedEvent::getEventId)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 检查是否进行了阶段切换
     */
    public boolean hasPhaseTransition() {
        return targetPhaseId != null && !targetPhaseId.isEmpty();
    }

    /**
     * 获取给特定受众的时间线消息
     */
    public java.util.List<String> getTimelineMessagesForAudience(String audience) {
        if (timelineMessages == null) return java.util.Collections.emptyList();
        return timelineMessages.stream()
            .filter(m -> m.getAudience() == null || "all".equals(m.getAudience()) || audience.equals(m.getAudience()))
            .map(TimelineMessage::getMessage)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取扩展属性值
     */
    public Object getExtraValue(String key) {
        if (extras == null) return null;
        return extras.get(key);
    }
}

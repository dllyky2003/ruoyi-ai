package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 会话评分证据（对应 emergency_session_score_record.evidence_json）
 * 存储评分项的详细证据，包括评分依据、相关事件、时间线等
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
public class SessionScoreEvidence {

    // ==================== 基础信息 ====================

    /**
     * 评分项代码（metricCode）
     */
    @JsonProperty("metricCode")
    private String metricCode;

    /**
     * 评分项名称
     */
    @JsonProperty("metricName")
    private String metricName;

    /**
     * 评分结果（PASS/FAIL/PARTIAL）
     */
    @JsonProperty("result")
    private String result;

    /**
     * 得分
     */
    @JsonProperty("score")
    private Double score;

    /**
     * 满分
     */
    @JsonProperty("maxScore")
    private Double maxScore;

    /**
     * 得分百分比
     */
    @JsonProperty("scorePercentage")
    private Double scorePercentage;

    // ==================== 评分依据 ====================

    /**
     * 评分依据列表
     */
    @JsonProperty("justifications")
    private List<Justification> justifications;

    /**
     * 关键事件列表（支持评分的事件）
     */
    @JsonProperty("keyEvents")
    private List<KeyEvent> keyEvents;

    /**
     * 缺失的事件列表（导致扣分）
     */
    @JsonProperty("missingEvents")
    private List<String> missingEvents;

    /**
     * 违规事件列表（导致扣分/失败）
     */
    @JsonProperty("violations")
    private List<Violation> violations;

    // ==================== 时间与状态 ====================

    /**
     * 评估开始时间
     */
    @JsonProperty("evaluationStartAt")
    private String evaluationStartAt;

    /**
     * 评估结束时间
     */
    @JsonProperty("evaluationEndAt")
    private String evaluationEndAt;

    /**
     * 评估耗时（秒）
     */
    @JsonProperty("evaluationDurationSec")
    private Integer evaluationDurationSec;

    /**
     * 评估时的阶段ID
     */
    @JsonProperty("phaseId")
    private String phaseId;

    /**
     * 评估时的周期号
     */
    @JsonProperty("cycleNo")
    private Integer cycleNo;

    // ==================== 原始数据 ====================

    /**
     * 原始证据数据（如传感器数据、日志原文等）
     */
    @JsonProperty("rawData")
    private Map<String, Object> rawData;

    /**
     * 扩展数据
     */
    @JsonProperty("extras")
    private Map<String, Object> extras;

    // ==================== 内部嵌套类 ====================

    @Data
    public static class Justification {
        /**
         * 依据类型（observation/action/time/guideline）
         */
        @JsonProperty("type")
        private String type;

        /**
         * 依据描述
         */
        @JsonProperty("description")
        private String description;

        /**
         * 依据分值（正分加分，负分扣分）
         */
        @JsonProperty("points")
        private Double points;

        /**
         * 关联的事件ID
         */
        @JsonProperty("eventId")
        private String eventId;

        /**
         * 关联的规则ID
         */
        @JsonProperty("ruleId")
        private String ruleId;

        /**
         * 依据的时间戳
         */
        @JsonProperty("timestamp")
        private String timestamp;

        /**
         * 引用来源（如指南章节）
         */
        @JsonProperty("reference")
        private String reference;
    }

    @Data
    public static class KeyEvent {
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
         * 发生时间
         */
        @JsonProperty("occurredAt")
        private String occurredAt;

        /**
         * 事件是否被正确执行
         */
        @JsonProperty("isCorrect")
        private Boolean isCorrect;

        /**
         * 事件分值
         */
        @JsonProperty("points")
        private Double points;

        /**
         * 事件详情（如具体参数）
         */
        @JsonProperty("details")
        private Map<String, Object> details;
    }

    @Data
    public static class Violation {
        /**
         * 违规代码
         */
        @JsonProperty("code")
        private String code;

        /**
         * 违规描述
         */
        @JsonProperty("description")
        private String description;

        /**
         * 违规严重程度（critical/major/minor）
         */
        @JsonProperty("severity")
        private String severity;

        /**
         * 违规发生时间
         */
        @JsonProperty("occurredAt")
        private String occurredAt;

        /**
         * 关联的事件ID
         */
        @JsonProperty("eventId")
        private String eventId;

        /**
         * 是否导致自动失败
         */
        @JsonProperty("isCriticalFailure")
        private Boolean isCriticalFailure;
    }

    // ==================== 便捷查询方法 ====================

    /**
     * 判断是否通过
     */
    public boolean isPassed() {
        return "PASS".equals(result) || (score != null && maxScore != null && score >= maxScore * 0.7);
    }

    /**
     * 判断是否失败
     */
    public boolean isFailed() {
        return "FAIL".equals(result);
    }

    /**
     * 获取总得分百分比
     */
    public Double getScorePercentage() {
        if (score == null || maxScore == null || maxScore == 0) return 0.0;
        return (score / maxScore) * 100;
    }

    /**
     * 获取所有扣分项的总分
     */
    public Double getDeductionPoints() {
        if (justifications == null) return 0.0;
        return justifications.stream()
            .filter(j -> j.getPoints() != null && j.getPoints() < 0)
            .mapToDouble(Justification::getPoints)
            .sum();
    }

    /**
     * 获取所有加分项的总分
     */
    public Double getBonusPoints() {
        if (justifications == null) return 0.0;
        return justifications.stream()
            .filter(j -> j.getPoints() != null && j.getPoints() > 0)
            .mapToDouble(Justification::getPoints)
            .sum();
    }

    /**
     * 检查是否存在严重违规
     */
    public boolean hasCriticalViolation() {
        if (violations == null) return false;
        return violations.stream().anyMatch(v -> Boolean.TRUE.equals(v.getIsCriticalFailure()));
    }

    /**
     * 获取所有严重违规的描述
     */
    public List<String> getCriticalViolationDescriptions() {
        if (violations == null) return List.of();
        return violations.stream()
            .filter(v -> Boolean.TRUE.equals(v.getIsCriticalFailure()))
            .map(Violation::getDescription)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据类型获取关键事件
     */
    public List<KeyEvent> getKeyEventsByType(String eventType) {
        if (keyEvents == null) return List.of();
        return keyEvents.stream()
            .filter(e -> eventType.equals(e.getEventType()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取正确的关键事件列表
     */
    public List<KeyEvent> getCorrectKeyEvents() {
        if (keyEvents == null) return List.of();
        return keyEvents.stream()
            .filter(e -> Boolean.TRUE.equals(e.getIsCorrect()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取错误的关键事件列表
     */
    public List<KeyEvent> getIncorrectKeyEvents() {
        if (keyEvents == null) return List.of();
        return keyEvents.stream()
            .filter(e -> !Boolean.TRUE.equals(e.getIsCorrect()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取扩展属性值
     */
    public Object getExtraValue(String key) {
        if (extras == null) return null;
        return extras.get(key);
    }

    /**
     * 获取原始数据中的某个值
     */
    public Object getRawDataValue(String key) {
        if (rawData == null) return null;
        return rawData.get(key);
    }
}

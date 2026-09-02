package com.ruoyi.ems.event;


import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局事件接收事件
 *
 * 当分类器识别出 G类全局意图（如 G4-1 报告血压、G1-1 安全清场）时发布。
 *
 * 全局事件的特征：
 * 1. 不改变状态机主流程状态（状态保持不变）
 * 2. 需要记录到事件日志（审计）
 * 3. 可能触发系统动作（如更新上下文变量、推送前端）
 * 4. 可用于评分（如学员是否及时报告了生命体征）
 *
 * 与 StateTransitionEvent 的区别：
 * - StateTransitionEvent：状态发生转移时发布
 * - GlobalEventReceivedEvent：识别到全局意图时发布，状态不变
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Getter
@ToString
public class GlobalEventReceivedEvent {

    /**
     * 案例实例ID
     */
    private final String caseInstanceId;

    /**
     * 意图编码（G类）
     * 如: G4-1, G1-1, G2-3
     */
    private final String intentCode;

    /**
     * 事件编码（系统内部）
     * 如: G4_1_REPORTED
     */
    private final String eventCode;

    /**
     * 当前状态（事件发生时状态机所在状态）
     */
    private final String currentState;

    /**
     * 原始语音文本
     */
    private final String rawText;

    /**
     * 提取的槽位
     * 如: {systolicBP: 120, diastolicBP: 80}
     */
    private final Map<String, Object> slots;

    /**
     * 时间戳
     */
    private final Instant timestamp;

    /**
     * 置信度
     */
    private final double confidence;

    // ==================== 构造器 ====================

    private GlobalEventReceivedEvent(Builder builder) {
        this.caseInstanceId = builder.caseInstanceId;
        this.intentCode = builder.intentCode;
        this.eventCode = builder.eventCode;
        this.currentState = builder.currentState;
        this.rawText = builder.rawText;
        this.slots = builder.slots != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.slots))
            : Collections.emptyMap();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.confidence = builder.confidence;
    }

    // ==================== 静态工厂方法 ====================

    public static Builder builder() {
        return new Builder();
    }

    public static GlobalEventReceivedEvent of(String caseInstanceId,
                                              String intentCode,
                                              String eventCode,
                                              String currentState,
                                              String rawText,
                                              Map<String, Object> slots) {
        return builder()
            .caseInstanceId(caseInstanceId)
            .intentCode(intentCode)
            .eventCode(eventCode)
            .currentState(currentState)
            .rawText(rawText)
            .slots(slots)
            .build();
    }

    // ==================== Builder ====================

    public static class Builder {
        private String caseInstanceId;
        private String intentCode;
        private String eventCode;
        private String currentState;
        private String rawText;
        private Map<String, Object> slots;
        private Instant timestamp;
        private double confidence = 0.9;

        public Builder caseInstanceId(String caseInstanceId) {
            this.caseInstanceId = caseInstanceId;
            return this;
        }

        public Builder intentCode(String intentCode) {
            this.intentCode = intentCode;
            return this;
        }

        public Builder eventCode(String eventCode) {
            this.eventCode = eventCode;
            return this;
        }

        public Builder currentState(String currentState) {
            this.currentState = currentState;
            return this;
        }

        public Builder rawText(String rawText) {
            this.rawText = rawText;
            return this;
        }

        public Builder slots(Map<String, Object> slots) {
            this.slots = slots;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public GlobalEventReceivedEvent build() {
            if (caseInstanceId == null) {
                throw new IllegalArgumentException("caseInstanceId 不能为空");
            }
            if (intentCode == null) {
                throw new IllegalArgumentException("intentCode 不能为空");
            }
            return new GlobalEventReceivedEvent(this);
        }
    }

    // ==================== 便捷判断方法 ====================

    /**
     * 检查是否为特定G类意图
     */
    public boolean isIntent(String code) {
        return code != null && code.equals(intentCode);
    }

    /**
     * 检查是否为生命体征报告（G4-1 ~ G4-4）
     */
    public boolean isVitalSignReport() {
        return intentCode != null && intentCode.startsWith("G4-");
    }

    /**
     * 检查是否为安全相关（G1-1 ~ G1-3）
     */
    public boolean isSafetyRelated() {
        return intentCode != null && intentCode.startsWith("G1-");
    }

    /**
     * 检查是否为团队协作（G2-1 ~ G2-8）
     */
    public boolean isTeamRelated() {
        return intentCode != null && intentCode.startsWith("G2-");
    }

    /**
     * 获取槽位值
     */
    @SuppressWarnings("unchecked")
    public <T> T getSlot(String key, Class<T> type) {
        Object value = slots != null ? slots.get(key) : null;
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        return null;
    }

    public String getSlotAsString(String key) {
        return getSlot(key, String.class);
    }

    public Integer getSlotAsInteger(String key) {
        return getSlot(key, Integer.class);
    }

    public Boolean getSlotAsBoolean(String key) {
        return getSlot(key, Boolean.class);
    }

    /**
     * 获取有效生命体征槽位
     */
    public Map<String, Object> getVitalSignSlots() {
        Map<String, Object> vitals = new HashMap<>();
        if (slots == null) return vitals;

        String[] vitalKeys = {"systolicBP", "systolic_bp", "diastolicBP", "diastolic_bp",
            "heartRate", "heart_rate", "spo2", "consciousness", "gcs"};
        for (String key : vitalKeys) {
            if (slots.containsKey(key)) {
                vitals.put(key, slots.get(key));
            }
        }
        return vitals;
    }
}

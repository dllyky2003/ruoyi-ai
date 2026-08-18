package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 会话事件载荷（对应 emergency_session_event_log.payload_json）
 * 存储事件的具体数据，如动作信息、证据、患者状态等
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
public class SessionEventPayloadDefinition {

    // ==================== 动作信息 ====================

    /**
     * 动作编码（如 manual_defibrillation、amiodarone_administration）
     */
    @JsonProperty("actionCode")
    private String actionCode;

    /**
     * 动作名称
     */
    @JsonProperty("actionName")
    private String actionName;

    /**
     * 动作实例ID
     */
    @JsonProperty("actionInstanceId")
    private String actionInstanceId;

    /**
     * 动作触发时间
     */
    @JsonProperty("actionAt")
    private String actionAt;

    /**
     * 动作执行者ID
     */
    @JsonProperty("performedBy")
    private Long performedBy;

    // ==================== 证据数据 ====================

    /**
     * 动作证据（如能量、剂量等）
     */
    @JsonProperty("evidence")
    private Evidence evidence;

    // ==================== 患者状态 ====================

    /**
     * 患者潜在状态
     */
    @JsonProperty("patientState")
    private PatientState patientState;

    /**
     * 患者观察状态
     */
    @JsonProperty("observationState")
    private ObservationState observationState;

    // ==================== 规则相关信息 ====================

    /**
     * 触发的规则ID列表
     */
    @JsonProperty("triggeredRuleIds")
    private java.util.List<String> triggeredRuleIds;

    /**
     * 当前阶段ID
     */
    @JsonProperty("currentPhaseId")
    private String currentPhaseId;

    /**
     * 当前周期号
     */
    @JsonProperty("cycleNo")
    private Integer cycleNo;

    /**
     * 扩展数据
     */
    @JsonProperty("extras")
    private Map<String, Object> extras;

    // ==================== 内部嵌套类 ====================

    @Data
    public static class Evidence {
        /**
         * 能量（焦耳），用于除颤
         */
        @JsonProperty("energyJ")
        private Integer energyJ;

        /**
         * 剂量（毫克），用于药物
         */
        @JsonProperty("doseMg")
        private Integer doseMg;

        /**
         * 给药途径（IV/IO）
         */
        @JsonProperty("route")
        private String route;

        /**
         * 除颤前是否确认所有人清场
         */
        @JsonProperty("allClearConfirmed")
        private Boolean allClearConfirmed;

        /**
         * 除颤是否已放电
         */
        @JsonProperty("shockDelivered")
        private Boolean shockDelivered;

        /**
         * 是否成功给药
         */
        @JsonProperty("administered")
        private Boolean administered;

        /**
         * 给药时间
         */
        @JsonProperty("administeredAt")
        private String administeredAt;

        /**
         * 其他证据字段
         */
        @JsonProperty("extras")
        private Map<String, Object> extras;
    }

    @Data
    public static class PatientState {
        /**
         * 原生心律（VF/pVT/ROSC等）
         */
        @JsonProperty("nativeRhythm")
        private String nativeRhythm;

        /**
         * 除颤计数
         */
        @JsonProperty("shockCount")
        private Integer shockCount;

        /**
         * 是否ROSC
         */
        @JsonProperty("rosc")
        private Boolean rosc;

        /**
         * 核心温度（摄氏度）
         */
        @JsonProperty("coreTemperatureC")
        private Double coreTemperatureC;

        /**
         * 收缩压
         */
        @JsonProperty("systolicBp")
        private Integer systolicBp;

        /**
         * 舒张压
         */
        @JsonProperty("diastolicBp")
        private Integer diastolicBp;

        /**
         * 心率
         */
        @JsonProperty("heartRate")
        private Integer heartRate;

        /**
         * 血氧饱和度
         */
        @JsonProperty("spo2")
        private Integer spo2;

        /**
         * 其他状态字段
         */
        @JsonProperty("extras")
        private Map<String, Object> extras;
    }

    @Data
    public static class ObservationState {
        /**
         * 皮肤状态
         */
        @JsonProperty("skin")
        private String skin;

        /**
         * 瞳孔状态
         */
        @JsonProperty("pupils")
        private String pupils;

        /**
         * 呼吸状态
         */
        @JsonProperty("respiration")
        private String respiration;

        /**
         * 其他观察字段
         */
        @JsonProperty("extras")
        private Map<String, Object> extras;
    }

    // ==================== 便捷查询方法 ====================

    /**
     * 判断是否为除颤动作
     */
    public boolean isDefibrillation() {
        return "manual_defibrillation".equals(actionCode);
    }

    /**
     * 判断是否为药物给药动作
     */
    public boolean isMedication() {
        return actionCode != null && actionCode.endsWith("_administration");
    }

    /**
     * 获取除颤能量（如果存在）
     */
    public Integer getDefibrillationEnergy() {
        if (evidence == null) return null;
        return evidence.getEnergyJ();
    }

    /**
     * 获取给药剂量（如果存在）
     */
    public Integer getMedicationDose() {
        if (evidence == null) return null;
        return evidence.getDoseMg();
    }

    /**
     * 检查是否有效除颤（能量>=200J且已放电）
     */
    public boolean isValidShock() {
        if (evidence == null) return false;
        return Boolean.TRUE.equals(evidence.getShockDelivered())
            && evidence.getEnergyJ() != null
            && evidence.getEnergyJ() >= 200;
    }

    /**
     * 获取扩展属性值
     */
    public Object getExtraValue(String key) {
        if (extras == null) return null;
        return extras.get(key);
    }
}

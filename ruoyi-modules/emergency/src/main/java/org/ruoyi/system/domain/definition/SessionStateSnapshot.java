package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 会话状态快照（对应 emergency_session_state_snapshot.state_json）
 * 存储训练会话在某一时刻的完整状态，包括患者状态、训练进度、阶段信息等
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
public class SessionStateSnapshot {

    // ==================== 会话基本信息 ====================

    /**
     * 会话ID
     */
    @JsonProperty("sessionId")
    private Long sessionId;

    /**
     * 序列号
     */
    @JsonProperty("sequenceNo")
    private Long sequenceNo;

    /**
     * 快照时间
     */
    @JsonProperty("snapshotAt")
    private String snapshotAt;

    /**
     * 会话状态（created/running/paused/completed/aborted）
     */
    @JsonProperty("sessionStatus")
    private String sessionStatus;

    /**
     * 已运行时长（秒）
     */
    @JsonProperty("elapsedSeconds")
    private Integer elapsedSeconds;

    // ==================== 患者状态 ====================

    /**
     * 患者潜在状态（生理指标）
     */
    @JsonProperty("latentState")
    private LatentState latentState;

    /**
     * 患者观察状态（体征表现）
     */
    @JsonProperty("observationState")
    private ObservationState observationState;

    // ==================== 训练进度 ====================

    /**
     * 当前阶段ID
     */
    @JsonProperty("currentPhaseId")
    private String currentPhaseId;

    /**
     * 当前阶段名称
     */
    @JsonProperty("currentPhaseName")
    private String currentPhaseName;

    /**
     * 当前阶段已运行时长（秒）
     */
    @JsonProperty("phaseElapsedSeconds")
    private Integer phaseElapsedSeconds;

    /**
     * 已完成阶段ID列表
     */
    @JsonProperty("completedPhaseIds")
    private List<String> completedPhaseIds;

    /**
     * 当前周期号（用于除颤周期等）
     */
    @JsonProperty("cycleNo")
    private Integer cycleNo;

    // ==================== 动作与事件 ====================

    /**
     * 已执行的动作列表
     */
    @JsonProperty("performedActions")
    private List<PerformedAction> performedActions;

    /**
     * 已触发的事件列表
     */
    @JsonProperty("triggeredEvents")
    private List<TriggeredEvent> triggeredEvents;

    /**
     * 已标记的证据码列表
     */
    @JsonProperty("markedEvidenceCodes")
    private List<String> markedEvidenceCodes;

    // ==================== 资源状态 ====================

    /**
     * 资源使用情况
     */
    @JsonProperty("resourceStatus")
    private Map<String, ResourceStatus> resourceStatus;

    // ==================== 评分状态 ====================

    /**
     * 当前得分
     */
    @JsonProperty("currentScore")
    private Double currentScore;

    /**
     * 当前得分详情
     */
    @JsonProperty("scoreDetails")
    private Map<String, Double> scoreDetails;

    /**
     * 是否已产生关键失败
     */
    @JsonProperty("hasCriticalFailure")
    private Boolean hasCriticalFailure;

    /**
     * 关键失败列表
     */
    @JsonProperty("criticalFailures")
    private List<CriticalFailure> criticalFailures;

    // ==================== 扩展数据 ====================

    /**
     * 扩展数据
     */
    @JsonProperty("extras")
    private Map<String, Object> extras;

    // ==================== 内部嵌套类 ====================

    @Data
    public static class LatentState {
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
         * 收缩压（mmHg）
         */
        @JsonProperty("systolicBp")
        private Integer systolicBp;

        /**
         * 舒张压（mmHg）
         */
        @JsonProperty("diastolicBp")
        private Integer diastolicBp;

        /**
         * 心率（次/分）
         */
        @JsonProperty("heartRate")
        private Integer heartRate;

        /**
         * 呼吸频率（次/分）
         */
        @JsonProperty("respiratoryRate")
        private Integer respiratoryRate;

        /**
         * 血氧饱和度（%）
         */
        @JsonProperty("spo2")
        private Integer spo2;

        /**
         * 呼气末二氧化碳（mmHg）
         */
        @JsonProperty("etco2")
        private Integer etco2;

        /**
         * 是否气管插管
         */
        @JsonProperty("isIntubated")
        private Boolean isIntubated;

        /**
         * 当前使用的药物列表
         */
        @JsonProperty("administeredMedications")
        private List<AdministeredMedication> administeredMedications;

        /**
         * 扩展状态字段
         */
        @JsonProperty("extras")
        private Map<String, Object> extras;
    }

    @Data
    public static class ObservationState {
        /**
         * 皮肤状态（如 pale_cyanotic、pink、diaphoretic）
         */
        @JsonProperty("skin")
        private String skin;

        /**
         * 瞳孔状态（如 dilated、constricted、reactive）
         */
        @JsonProperty("pupils")
        private String pupils;

        /**
         * 呼吸状态（如 absent、agonal、normal）
         */
        @JsonProperty("respiration")
        private String respiration;

        /**
         * 意识水平（如 unresponsive、pain_only、verbal）
         */
        @JsonProperty("consciousness")
        private String consciousness;

        /**
         * 扩展观察字段
         */
        @JsonProperty("extras")
        private Map<String, Object> extras;
    }

    @Data
    public static class PerformedAction {
        /**
         * 动作ID
         */
        @JsonProperty("actionId")
        private String actionId;

        /**
         * 动作编码
         */
        @JsonProperty("actionCode")
        private String actionCode;

        /**
         * 动作名称
         */
        @JsonProperty("actionName")
        private String actionName;

        /**
         * 动作执行时间
         */
        @JsonProperty("performedAt")
        private String performedAt;

        /**
         * 执行者ID
         */
        @JsonProperty("performedBy")
        private Long performedBy;

        /**
         * 动作参数
         */
        @JsonProperty("parameters")
        private Map<String, Object> parameters;

        /**
         * 动作结果（success/failure）
         */
        @JsonProperty("result")
        private String result;
    }

    @Data
    public static class TriggeredEvent {
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
         * 触发时间
         */
        @JsonProperty("triggeredAt")
        private String triggeredAt;

        /**
         * 触发规则ID
         */
        @JsonProperty("triggerRuleId")
        private String triggerRuleId;
    }

    @Data
    public static class AdministeredMedication {
        /**
         * 药物编码
         */
        @JsonProperty("medicationCode")
        private String medicationCode;

        /**
         * 药物名称
         */
        @JsonProperty("medicationName")
        private String medicationName;

        /**
         * 剂量（mg）
         */
        @JsonProperty("doseMg")
        private Integer doseMg;

        /**
         * 给药途径（IV/IO）
         */
        @JsonProperty("route")
        private String route;

        /**
         * 给药时间
         */
        @JsonProperty("administeredAt")
        private String administeredAt;

        /**
         * 给药次数（第几次）
         */
        @JsonProperty("doseNumber")
        private Integer doseNumber;
    }

    @Data
    public static class ResourceStatus {
        /**
         * 资源编码
         */
        @JsonProperty("resourceCode")
        private String resourceCode;

        /**
         * 资源名称
         */
        @JsonProperty("resourceName")
        private String resourceName;

        /**
         * 当前库存
         */
        @JsonProperty("currentStock")
        private Integer currentStock;

        /**
         * 初始库存
         */
        @JsonProperty("initialStock")
        private Integer initialStock;

        /**
         * 已消耗数量
         */
        @JsonProperty("consumedCount")
        private Integer consumedCount;

        /**
         * 是否可用
         */
        @JsonProperty("available")
        private Boolean available;
    }

    @Data
    public static class CriticalFailure {
        /**
         * 失败码
         */
        @JsonProperty("failureCode")
        private String failureCode;

        /**
         * 失败描述
         */
        @JsonProperty("description")
        private String description;

        /**
         * 发生时间
         */
        @JsonProperty("occurredAt")
        private String occurredAt;

        /**
         * 关联事件ID
         */
        @JsonProperty("eventId")
        private String eventId;

        /**
         * 是否需要教官确认
         */
        @JsonProperty("requiresInstructorConfirmation")
        private Boolean requiresInstructorConfirmation;

        /**
         * 是否已确认
         */
        @JsonProperty("confirmed")
        private Boolean confirmed;

        /**
         * 确认时间
         */
        @JsonProperty("confirmedAt")
        private String confirmedAt;

        /**
         * 确认人ID
         */
        @JsonProperty("confirmedBy")
        private Long confirmedBy;
    }

    // ==================== 便捷查询方法 ====================

    /**
     * 判断患者是否ROSC
     */
    public boolean isRosc() {
        return latentState != null && Boolean.TRUE.equals(latentState.getRosc());
    }

    /**
     * 判断患者是否为可电击心律（VF/pVT）
     */
    public boolean isShockableRhythm() {
        if (latentState == null || latentState.getNativeRhythm() == null) return false;
        String rhythm = latentState.getNativeRhythm();
        return "VF".equals(rhythm) || "pVT".equals(rhythm);
    }

    /**
     * 判断会话是否已完成
     */
    public boolean isSessionCompleted() {
        return "completed".equals(sessionStatus) || "aborted".equals(sessionStatus);
    }

    /**
     * 获取除颤次数
     */
    public Integer getShockCount() {
        return latentState != null ? latentState.getShockCount() : 0;
    }

    /**
     * 获取已给药的药物列表
     */
    public List<AdministeredMedication> getAdministeredMedications() {
        return latentState != null ? latentState.getAdministeredMedications() : null;
    }

    /**
     * 检查某个药物是否已给药
     */
    public boolean isMedicationAdministered(String medicationCode) {
        List<AdministeredMedication> medications = getAdministeredMedications();
        if (medications == null) return false;
        return medications.stream()
            .anyMatch(m -> medicationCode.equals(m.getMedicationCode()));
    }

    /**
     * 获取药物给药次数
     */
    public Integer getMedicationDoseCount(String medicationCode) {
        List<AdministeredMedication> medications = getAdministeredMedications();
        if (medications == null) return 0;
        return (int) medications.stream()
            .filter(m -> medicationCode.equals(m.getMedicationCode()))
            .count();
    }

    /**
     * 检查是否存在关键失败
     */
    public boolean hasCriticalFailure() {
        return Boolean.TRUE.equals(hasCriticalFailure)
            && criticalFailures != null && !criticalFailures.isEmpty();
    }

    /**
     * 获取未确认的关键失败列表
     */
    public List<CriticalFailure> getUnconfirmedCriticalFailures() {
        if (criticalFailures == null) return List.of();
        return criticalFailures.stream()
            .filter(f -> !Boolean.TRUE.equals(f.getConfirmed()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据编码获取资源状态
     */
    public ResourceStatus getResourceStatusByCode(String resourceCode) {
        if (resourceStatus == null) return null;
        return resourceStatus.get(resourceCode);
    }

    /**
     * 检查资源是否可用
     */
    public boolean isResourceAvailable(String resourceCode) {
        ResourceStatus status = getResourceStatusByCode(resourceCode);
        return status != null && Boolean.TRUE.equals(status.getAvailable());
    }

    /**
     * 获取扩展属性值
     */
    public Object getExtraValue(String key) {
        if (extras == null) return null;
        return extras.get(key);
    }

    /**
     * 获取当前得分百分比
     */
    public Double getScorePercentage(Double maxTotalScore) {
        if (currentScore == null || maxTotalScore == null || maxTotalScore == 0) return 0.0;
        return (currentScore / maxTotalScore) * 100;
    }
}

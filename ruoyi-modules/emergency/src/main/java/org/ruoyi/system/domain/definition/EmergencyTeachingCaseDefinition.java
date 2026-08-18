package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 病例版本定义（对应 emergency_teaching_case_version.definition_json）
 * 存储完整的规则引擎配置，包括规则、事件、阶段、评分等
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
public class EmergencyTeachingCaseDefinition {

    // ==================== 顶层字段 ====================

    /** 规则列表 */
    private List<Rule> rules;

    /** 事件列表 */
    private List<Event> events;

    /** 阶段列表 */
    private List<Phase> phases;

    /** 技能领域列表 */
    private List<Track> tracks;

    /** 评分配置 */
    private Scoring scoring;

    /** 病例元数据 */
    private CaseMeta caseMeta;

    /** 设置 */
    private Settings settings;

    /** 治理信息 */
    private Governance governance;

    /** Schema版本 */
    private String schemaVersion;

    /** 操作绑定列表 */
    @JsonProperty("actionBindings")
    private List<ActionBinding> actionBindings;

    /** 临床上下文 */
    private ClinicalContext clinicalContext;

    /** 资源绑定列表 */
    @JsonProperty("resourceBindings")
    private List<ResourceBinding> resourceBindings;

    /** 临床轨迹 */
    private ClinicalTrajectory clinicalTrajectory;

    // ==================== 内部嵌套类 ====================

    @Data
    public static class Rule {
        /** 规则ID */
        private String id;

        /** 规则名称 */
        private String name;

        /** 规则动作列表 */
        private List<ThenAction> then;

        /** 触发条件 */
        private WhenCondition when;

        /** 作用域（phase/global） */
        private String scope;

        /** 是否启用 */
        private Boolean enabled;

        /** 优先级（数值越小优先级越高） */
        private Integer priority;

        /** 触发策略（once/once_per_cycle） */
        private String firePolicy;

        /** 幂等键 */
        private String idempotencyKey;
    }

    @Data
    public static class ThenAction {
        /** 动作类型：emit_event/mark_evidence/append_timeline/phase_transition/latent_state_transition */
        private String type;

        /** 事件ID（type=emit_event时使用） */
        @JsonProperty("eventId")
        private String eventId;

        /** 证据码（type=mark_evidence时使用） */
        @JsonProperty("evidenceCode")
        private String evidenceCode;

        /** 消息内容（type=append_timeline时使用） */
        private String message;

        /** 受众：instructor/learner/all（type=append_timeline时使用） */
        private String audience;

        /** 目标阶段ID（type=phase_transition时使用） */
        @JsonProperty("targetPhaseId")
        private String targetPhaseId;

        /** 目标轨迹节点ID（type=latent_state_transition时使用） */
        @JsonProperty("targetTrajectoryNodeId")
        private String targetTrajectoryNodeId;
    }

    @Data
    public static class WhenCondition {
        /** 所有条件（AND逻辑） */
        private List<Condition> all;
    }

    @Data
    public static class Condition {
        /** 操作符：eq/gte/in/exists等 */
        private String op;

        /** 事实路径（如 action.code, patient.latentState.nativeRhythm） */
        private String fact;

        /** 比较值（可以是字符串、数字、布尔、数组） */
        private Object value;
    }

    @Data
    public static class Event {
        /** 事件ID */
        private String id;

        /** 事件名称 */
        private String name;

        /** 事件效果列表 */
        private List<Effect> effects;

        /** 事件类型 */
        private String eventType;

        /** 触发该事件的规则ID */
        @JsonProperty("triggerRuleId")
        private String triggerRuleId;
    }

    @Data
    public static class Effect {
        /** 效果类型：append_timeline/latent_state_transition */
        private String type;

        /** 消息内容（type=append_timeline时使用） */
        private String message;

        /** 受众（type=append_timeline时使用） */
        private String audience;

        /** 目标轨迹节点ID（type=latent_state_transition时使用） */
        @JsonProperty("targetTrajectoryNodeId")
        private String targetTrajectoryNodeId;
    }

    @Data
    public static class Phase {
        /** 阶段ID */
        private String id;

        /** 阶段名称 */
        private String name;

        /** 顺序号 */
        private Integer sequence;

        /** 退出规则ID */
        @JsonProperty("exitRuleId")
        private String exitRuleId;

        /** 进入规则ID */
        @JsonProperty("entryRuleId")
        private String entryRuleId;

        /** 激活的技能领域ID列表 */
        @JsonProperty("activeTrackIds")
        private List<String> activeTrackIds;

        /** 最大持续时间（秒） */
        @JsonProperty("maxDurationSec")
        private Integer maxDurationSec;

        /** 名义持续时间（秒） */
        @JsonProperty("nominalDurationSec")
        private Integer nominalDurationSec;
    }

    @Data
    public static class Track {
        /** 领域ID */
        private String id;

        /** 领域名称 */
        private String name;
    }

    @Data
    public static class Scoring {
        /** 评分维度列表 */
        private List<Domain> domains;

        /** 及格策略 */
        private PassingPolicy passingPolicy;

        /** 指标绑定列表 */
        private List<MetricBinding> metricBindings;

        /** 关键失败策略 */
        private CriticalFailurePolicy criticalFailurePolicy;
    }

    @Data
    public static class Domain {
        /** 领域编码 */
        private String code;

        /** 权重 */
        private Integer weight;
    }

    @Data
    public static class PassingPolicy {
        /** 总分阈值 */
        private Integer totalScoreThreshold;

        /** 各领域要求阈值 */
        private Map<String, Integer> requiredDomainThresholds;
    }

    @Data
    public static class MetricBinding {
        /** 绑定ID */
        private String id;

        /** 权重 */
        private Integer weight;

        /** 是否启用 */
        private Boolean enabled;

        /** 覆盖配置 */
        private OverrideConfig override;

        /** 指标引用 */
        private MetricRef metricRef;

        /** 所属领域编码 */
        private String domainCode;

        /** 机会规则ID */
        private String opportunityRuleId;
    }

    @Data
    public static class OverrideConfig {
        /** 阈值（秒） */
        private Integer thresholdSec;
    }

    @Data
    public static class MetricRef {
        /** 条目编码 */
        private String itemCode;
    }

    @Data
    public static class CriticalFailurePolicy {
        /** 结果（fail） */
        private String result;

        /** 是否启用 */
        private Boolean enabled;

        /** 失败码列表 */
        private List<String> failureCodes;

        /** 是否需要教官确认 */
        private Boolean requiresInstructorConfirmation;
    }

    @Data
    public static class CaseMeta {
        /** 标题 */
        private String title;

        /** 难度 */
        private String difficulty;

        /** 描述 */
        private String description;

        /** 目标受众 */
        private List<String> targetAudience;

        /** 预计时长（分钟） */
        private Integer estimatedDurationMin;
    }

    @Data
    public static class Settings {
        /** 是否允许暂停 */
        private Boolean allowPause;

        /** 最大持续时间（秒） */
        private Integer maxDurationSec;

        /** 是否自动推进阶段 */
        private Boolean autoAdvancePhases;

        /** 是否允许教官覆盖 */
        private Boolean allowInstructorOverride;
    }

    @Data
    public static class Governance {
        /** 语言 */
        private String language;

        /** 辖区代码 */
        private String jurisdictionCode;

        /** 组织SOP引用 */
        private OrgSopRef organizationSopRef;

        /** 指南配置引用 */
        private GuidelineProfileRef guidelineProfileRef;
    }

    @Data
    public static class OrgSopRef {
        private String itemCode;
    }

    @Data
    public static class GuidelineProfileRef {
        private String version;
        private String itemCode;
    }

    @Data
    public static class ActionBinding {
        /** 绑定ID */
        private String id;

        /** 是否启用 */
        private Boolean enabled;

        /** 库引用 */
        private LibraryRef libraryRef;

        /** 允许的技能领域ID列表 */
        private List<String> allowedTrackIds;

        /** 可用的阶段ID列表 */
        private List<String> availablePhaseIds;
    }

    @Data
    public static class LibraryRef {
        private String itemCode;
    }

    @Data
    public static class ClinicalContext {
        /** 场景信息 */
        private Scene scene;

        /** 患者信息 */
        private Patient patient;

        /** 初始状态 */
        private InitialState initialState;
    }

    @Data
    public static class Scene {
        /** 地点 */
        private String location;

        /** 调度信息 */
        private String dispatchInfo;

        /** 环境标签 */
        private List<String> environmentTags;
    }

    @Data
    public static class Patient {
        /** 病史背景 */
        private String background;

        /** 人口统计学信息 */
        private Demographics demographics;
    }

    @Data
    public static class Demographics {
        private Integer age;
        private String sex;
        private Integer weightKg;
    }

    @Data
    public static class InitialState {
        /** 覆盖配置 */
        private OverrideState override;

        /** 库引用 */
        private LibraryRef libraryRef;
    }

    @Data
    public static class OverrideState {
        /** 潜在状态 */
        private LatentState latentState;

        /** 观察状态 */
        private ObservationState observationState;
    }

    @Data
    public static class LatentState {
        /** 核心温度（摄氏度） */
        private Double coreTemperatureC;

        /** 原生心律 */
        private String nativeRhythm;

        /** 除颤计数 */
        private Integer shockCount;

        /** 是否ROSC */
        private Boolean rosc;
    }

    @Data
    public static class ObservationState {
        /** 皮肤状态 */
        private String skin;
    }

    @Data
    public static class ResourceBinding {
        /** 绑定ID */
        private String id;

        /** 是否启用 */
        private Boolean enabled;

        /** 覆盖配置 */
        private OverrideResource override;

        /** 库引用 */
        private LibraryRef libraryRef;

        /** 初始库存 */
        private Integer initialStock;
    }

    @Data
    public static class OverrideResource {
        /** 能量协议 */
        private EnergyProtocol energyProtocol;
    }

    @Data
    public static class EnergyProtocol {
        /** 初始能量（焦耳） */
        private Integer initialEnergyJ;

        /** 可用能量选项 */
        private List<Integer> availableEnergyJ;

        /** 后续策略 */
        private String subsequentPolicy;
    }

    @Data
    public static class ClinicalTrajectory {
        /** 模式（deterministic） */
        private String mode;

        /** 轨迹节点列表 */
        private List<TrajectoryNode> nodes;

        /** 随机种子策略 */
        private RandomSeedPolicy randomSeedPolicy;
    }

    @Data
    public static class TrajectoryNode {
        /** 节点ID */
        private String id;

        /** 状态引用 */
        private LibraryRef stateRef;

        /** 进入规则ID */
        private String entryRuleId;
    }

    @Data
    public static class RandomSeedPolicy {
        private String value;
    }

    // ==================== 便捷查询方法 ====================

    /**
     * 根据规则ID获取规则
     */
    public Rule getRuleById(String ruleId) {
        if (rules == null) return null;
        return rules.stream()
            .filter(r -> Objects.equals(r.getId(), ruleId))
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取所有启用的规则（按优先级升序）
     */
    public List<Rule> getEnabledRulesSortedByPriority() {
        if (rules == null) return Collections.emptyList();
        return rules.stream()
            .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
            .sorted(Comparator.comparingInt(Rule::getPriority))
            .collect(Collectors.toList());
    }

    /**
     * 根据阶段ID获取阶段配置
     */
    public Phase getPhaseById(String phaseId) {
        if (phases == null) return null;
        return phases.stream()
            .filter(p -> Objects.equals(p.getId(), phaseId))
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取按顺序排列的阶段列表
     */
    public List<Phase> getPhasesSortedBySequence() {
        if (phases == null) return Collections.emptyList();
        return phases.stream()
            .sorted(Comparator.comparingInt(Phase::getSequence))
            .collect(Collectors.toList());
    }

    /**
     * 根据事件ID获取事件
     */
    public Event getEventById(String eventId) {
        if (events == null) return null;
        return events.stream()
            .filter(e -> Objects.equals(e.getId(), eventId))
            .findFirst()
            .orElse(null);
    }

    /**
     * 根据事件类型获取所有事件
     */
    public List<Event> getEventsByType(String eventType) {
        if (events == null) return Collections.emptyList();
        return events.stream()
            .filter(e -> Objects.equals(e.getEventType(), eventType))
            .collect(Collectors.toList());
    }

    /**
     * 获取规则触发的所有事件ID列表
     */
    public List<String> getEventIdsByRuleId(String ruleId) {
        Rule rule = getRuleById(ruleId);
        if (rule == null || rule.getThen() == null) return Collections.emptyList();
        return rule.getThen().stream()
            .filter(a -> "emit_event".equals(a.getType()))
            .map(ThenAction::getEventId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取指定阶段可用的操作绑定
     */
    public List<ActionBinding> getActionBindingsByPhaseId(String phaseId) {
        if (actionBindings == null) return Collections.emptyList();
        return actionBindings.stream()
            .filter(b -> Boolean.TRUE.equals(b.getEnabled()))
            .filter(b -> b.getAvailablePhaseIds() != null && b.getAvailablePhaseIds().contains(phaseId))
            .collect(Collectors.toList());
    }

    /**
     * 获取指定轨迹ID对应的节点
     */
    public TrajectoryNode getTrajectoryNodeById(String nodeId) {
        if (clinicalTrajectory == null || clinicalTrajectory.getNodes() == null) return null;
        return clinicalTrajectory.getNodes().stream()
            .filter(n -> Objects.equals(n.getId(), nodeId))
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取评分配置的领域权重
     */
    public Integer getDomainWeight(String domainCode) {
        if (scoring == null || scoring.getDomains() == null) return null;
        return scoring.getDomains().stream()
            .filter(d -> Objects.equals(d.getCode(), domainCode))
            .map(Domain::getWeight)
            .findFirst()
            .orElse(null);
    }

    /**
     * 检查是否存在指定的失败码
     */
    public boolean hasCriticalFailure(String failureCode) {
        if (scoring == null || scoring.getCriticalFailurePolicy() == null) return false;
        List<String> failureCodes = scoring.getCriticalFailurePolicy().getFailureCodes();
        return failureCodes != null && failureCodes.contains(failureCode);
    }

    /**
     * 获取病例标题
     */
    public String getCaseTitle() {
        return caseMeta != null ? caseMeta.getTitle() : null;
    }

    /**
     * 获取病例难度
     */
    public String getCaseDifficulty() {
        return caseMeta != null ? caseMeta.getDifficulty() : null;
    }

    /**
     * 获取预计时长（分钟）
     */
    public Integer getEstimatedDurationMin() {
        return caseMeta != null ? caseMeta.getEstimatedDurationMin() : null;
    }
}

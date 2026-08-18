package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 公共库条目版本定义（对应 emergency_library_item_version.definition_json）
 * 包含规则引擎配置、病例方案、评分指标等
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
public class EmergencyLibraryItemDefinition {

    // ==================== 顶层字段 ====================
    private List<Rule> rules;
    private List<Event> events;
    private List<Phase> phases;
    private List<Track> tracks;
    private Scoring scoring;
    private CaseMeta caseMeta;
    private Settings settings;
    private Governance governance;
    private String schemaVersion;

    @JsonProperty("actionBindings")
    private List<ActionBinding> actionBindings;

    private ClinicalContext clinicalContext;

    @JsonProperty("resourceBindings")
    private List<ResourceBinding> resourceBindings;

    private ClinicalTrajectory clinicalTrajectory;

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
     * 获取所有启用的规则（按优先级降序）
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
        if (rules == null) return Collections.emptyList();
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

    // ==================== 内部嵌套类 ====================

    @Data
    public static class Rule {
        private String id;
        private String name;
        private List<ThenAction> then;
        private WhenCondition when;
        private String scope;
        private Boolean enabled;
        private Integer priority;
        private String firePolicy;
        private String idempotencyKey;
    }

    @Data
    public static class ThenAction {
        private String type;
        @JsonProperty("eventId")
        private String eventId;
        @JsonProperty("evidenceCode")
        private String evidenceCode;
        private String message;
        private String audience;
        @JsonProperty("targetPhaseId")
        private String targetPhaseId;
        @JsonProperty("targetTrajectoryNodeId")
        private String targetTrajectoryNodeId;
    }

    @Data
    public static class WhenCondition {
        private List<Map<String, Object>> all;
    }

    @Data
    public static class Event {
        private String id;
        private String name;
        private List<Effect> effects;
        private String eventType;
        @JsonProperty("triggerRuleId")
        private String triggerRuleId;
    }

    @Data
    public static class Effect {
        private String type;
        private String message;
        private String audience;
        @JsonProperty("targetTrajectoryNodeId")
        private String targetTrajectoryNodeId;
    }

    @Data
    public static class Phase {
        private String id;
        private String name;
        private Integer sequence;
        @JsonProperty("exitRuleId")
        private String exitRuleId;
        @JsonProperty("entryRuleId")
        private String entryRuleId;
        @JsonProperty("activeTrackIds")
        private List<String> activeTrackIds;
        @JsonProperty("maxDurationSec")
        private Integer maxDurationSec;
        @JsonProperty("nominalDurationSec")
        private Integer nominalDurationSec;
    }

    @Data
    public static class Track {
        private String id;
        private String name;
    }

    @Data
    public static class Scoring {
        private List<Domain> domains;
        private PassingPolicy passingPolicy;
        private List<MetricBinding> metricBindings;
        private CriticalFailurePolicy criticalFailurePolicy;
    }

    @Data
    public static class Domain {
        private String code;
        private Integer weight;
    }

    @Data
    public static class PassingPolicy {
        private Integer totalScoreThreshold;
        private Map<String, Integer> requiredDomainThresholds;
    }

    @Data
    public static class MetricBinding {
        private String id;
        private Integer weight;
        private Boolean enabled;
        private OverrideConfig override;
        private MetricRef metricRef;
        private String domainCode;
        private String opportunityRuleId;
    }

    @Data
    public static class OverrideConfig {
        private Integer thresholdSec;
    }

    @Data
    public static class MetricRef {
        private String itemCode;
    }

    @Data
    public static class CriticalFailurePolicy {
        private String result;
        private Boolean enabled;
        private List<String> failureCodes;
        private Boolean requiresInstructorConfirmation;
    }

    @Data
    public static class CaseMeta {
        private String title;
        private String difficulty;
        private String description;
        private List<String> targetAudience;
        private Integer estimatedDurationMin;
    }

    @Data
    public static class Settings {
        private Boolean allowPause;
        private Integer maxDurationSec;
        private Boolean autoAdvancePhases;
        private Boolean allowInstructorOverride;
    }

    @Data
    public static class Governance {
        private String language;
        private String jurisdictionCode;
        private OrgSopRef organizationSopRef;
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
        private String id;
        private Boolean enabled;
        private LibraryRef libraryRef;
        private List<String> allowedTrackIds;
        private List<String> availablePhaseIds;
    }

    @Data
    public static class LibraryRef {
        private String itemCode;
    }

    @Data
    public static class ClinicalContext {
        private Scene scene;
        private Patient patient;
        private InitialState initialState;
    }

    @Data
    public static class Scene {
        private String location;
        private String dispatchInfo;
        private List<String> environmentTags;
    }

    @Data
    public static class Patient {
        private String background;
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
        private OverrideState override;
        private LibraryRef libraryRef;
    }

    @Data
    public static class OverrideState {
        private LatentState latentState;
        private ObservationState observationState;
    }

    @Data
    public static class LatentState {
        private Double coreTemperatureC;
        private String nativeRhythm;
        private Integer shockCount;
        private Boolean rosc;
    }

    @Data
    public static class ObservationState {
        private String skin;
    }

    @Data
    public static class ResourceBinding {
        private String id;
        private Boolean enabled;
        private OverrideResource override;
        private LibraryRef libraryRef;
        private Integer initialStock;
    }

    @Data
    public static class OverrideResource {
        private EnergyProtocol energyProtocol;
    }

    @Data
    public static class EnergyProtocol {
        private Integer initialEnergyJ;
        private List<Integer> availableEnergyJ;
        private String subsequentPolicy;
    }

    @Data
    public static class ClinicalTrajectory {
        private String mode;
        private List<TrajectoryNode> nodes;
        private RandomSeedPolicy randomSeedPolicy;
    }

    @Data
    public static class TrajectoryNode {
        private String id;
        private LibraryRef stateRef;
        private String entryRuleId;
    }

    @Data
    public static class RandomSeedPolicy {
        private String value;
    }
}

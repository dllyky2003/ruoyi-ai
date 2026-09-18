package com.ruoyi.ems.intent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.ems.intent.model.routing.EvidencePolicy;
import com.ruoyi.ems.intent.model.routing.NegativePolicy;
import com.ruoyi.ems.intent.model.routing.RouteGuard;
import com.ruoyi.ems.intent.model.routing.SlotSpec;
import com.ruoyi.ems.orchestrator.model.ActionChainConfig;
import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 意图定义模型。
 *
 * <p>数据来源：{@code config/acls/**／_intents.json}。
 * 由 {@code CaseIntentDefinitionsLoader} 加载并填充运行时字段
 * （{@code layer} / {@code subDomain} / {@code domainCode} / {@code general}
 * / {@code specific} / {@code caseCode} / {@code execution} / {@code extraction}）。
 *
 * <p>契约构建视角：
 * <ul>
 *   <li>{@link #intentCode} 是主键；</li>
 *   <li>{@link #actType} 决定 writer 类型（EXECUTOR / CLASSIFIER）；</li>
 *   <li>{@link #trg} 的 {@code regex} 作为 surface 的补充来源；</li>
 *   <li>{@link #aliases} 和 {@link #examples} 也是 surface 来源；</li>
 *   <li>{@link #execution} 若含变量写入语义，需要额外解析。</li>
 * </ul>
 */
@Data
public class IntentDefinition {

    // ==================== 现有字段（保持不变） ====================
    private String intentCode;
    private String nameCn;
    private List<String> examples;
    private List<String> h;
    private Obj obj;

    /**
     * 动作类型（JSON 字段名为 {@code act}）。
     *
     * <p>取值范围（中文）：
     * <ul>
     *   <li>发令性指令（COMMAND）</li>
     *   <li>否决（VETO）</li>
     *   <li>闭环确认（CLOSED_LOOP）</li>
     *   <li>宣告（ANNOUNCEMENT）</li>
     *   <li>探查性询问（QUERY）</li>
     *   <li>治疗（TREATMENT）</li>
     * </ul>
     */
    @JsonProperty("act")
    private String actType;

    // ==================== 新增字段（保留原样） ====================
    private Trg trg;
    private Out out;

    @JsonProperty("route_guard")
    private RouteGuard routeGuard;

    @JsonProperty("negative_policy")
    private NegativePolicy negativePolicy;

    @JsonProperty("standard_script")
    private String standardScript;

    private List<String> aliases;

    // ==================== 兼容 CascadeIntentClassifier 需要的字段 ====================
    @JsonProperty("act_type_rules")
    private List<ActTypeRule> actTypeRules;

    @JsonProperty("default_act_type")
    private String defaultActType;

    @JsonProperty("pattern_hints")
    private List<String> patternHints;

    // ==================== 运行时字段（不参与 JSON 序列化） ====================
    @JsonIgnore private String layer;
    @JsonIgnore private String subDomain;
    @JsonIgnore private String domainCode;
    @JsonIgnore private boolean general;
    @JsonIgnore private boolean specific;
    @JsonIgnore private String caseCode;
    @JsonProperty("execution")
    private ActionChainConfig execution;

    @JsonProperty("extraction")
    private ExtractionConfig extraction;

    // ====================================================================
    // 兼容方法
    // ====================================================================

    /**
     * 兼容 {@code CascadeIntentClassifier} 对 {@code getAct()} 的调用。
     * 实际返回 {@link #actType} 的值。
     */
    @JsonIgnore
    public String getAct() {
        return actType;
    }

    /**
     * 兼容方法：返回触发规则的正则列表。
     * 若 {@link #trg} 为空则返回空列表。
     */
    @JsonIgnore
    public List<String> getKeywords() {
        if (trg != null && trg.getRegex() != null && !trg.getRegex().isEmpty()) {
            return trg.getRegex();
        }
        return Collections.emptyList();
    }

    // ====================================================================
    // ★ 新增：契约构建专用便捷方法
    // ====================================================================

    /**
     * 取该意图的所有触发模式（regex + aliases + examples 的并集）。
     *
     * <p>契约构建时用于补充 {@code IntentContract.triggers}，
     * 与 {@code IntentDictLoader.surfaces} 互补。
     *
     * @return 去重后的触发模式集合；无任何来源时返回空集
     */
    @JsonIgnore
    public Set<String> getAllTriggers() {
        Set<String> result = new LinkedHashSet<>();
        if (trg != null && trg.getRegex() != null) {
            for (String r : trg.getRegex()) {
                if (r != null && !r.isBlank()) result.add(r);
            }
        }
        if (aliases != null) {
            for (String a : aliases) {
                if (a != null && !a.isBlank()) result.add(a);
            }
        }
        if (examples != null) {
            for (String e : examples) {
                if (e != null && !e.isBlank()) result.add(e);
            }
        }
        return result;
    }

    /**
     * 取该意图的负向正则列表（{@code trg.negative_regex}）。
     *
     * <p>契约诊断用：若意图配置了负向模式但路由未处理，可能导致误判。
     *
     * @return 负向正则集合；无配置时返回空集
     */
    @JsonIgnore
    public Set<String> getNegativeRegexes() {
        if (trg == null || trg.getNegativeRegex() == null) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String r : trg.getNegativeRegex()) {
            if (r != null && !r.isBlank()) result.add(r);
        }
        return result;
    }

    /**
     * 取该意图的槽位定义（{@code trg.slots}）。
     *
     * <p>契约诊断用：槽位可能映射到变量（如 {@code dose} -> {@code intervention.lastDrugDose}）。
     *
     * @return 槽位名 -> SlotSpec；无配置时返回空 Map
     */
    @JsonIgnore
    public Map<String, SlotSpec> getSlots() {
        if (trg == null || trg.getSlots() == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(trg.getSlots());
    }

    /**
     * 该意图是否声明了执行链（{@code execution}）。
     * 若为 true，契约构建时可以尝试从执行链里提取变量写入语义。
     */
    @JsonIgnore
    public boolean hasExecution() {
        return execution != null;
    }

    /**
     * 该意图是否声明了抽取配置（{@code extraction}）。
     * 若为 true，契约构建时可以尝试从抽取配置里提取变量写入语义。
     */
    @JsonIgnore
    public boolean hasExtraction() {
        return extraction != null;
    }

    // ====================================================================
    // 内部类（保留原样）
    // ====================================================================

    @Data
    public static class Obj {
        private String type;
        private String name;
    }

    @Data
    public static class Trg {
        private List<String> regex;

        @JsonProperty("negative_regex")
        private List<String> negativeRegex;

        private Map<String, SlotSpec> slots;

        @JsonProperty("evidence_policy")
        private EvidencePolicy evidencePolicy;
    }

    @Data
    public static class Out {
        @JsonProperty("artifact_type")
        private String artifactType;
        private String artifact;
        private String downstream;
        @JsonProperty("route_evidence")
        private Boolean routeEvidence;
    }
}

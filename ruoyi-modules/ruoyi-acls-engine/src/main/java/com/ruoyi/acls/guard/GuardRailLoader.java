package com.ruoyi.acls.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 安全护栏配置加载器。
 * 从 YAML 加载 ACLS 安全护栏规则，支持热加载。
 *
 * <p>契约构建视角：
 *   - 护栏是<b>意图级前置拦截</b>，不参与状态机转移，因此不进入 TransitionContract；
 *   - 但护栏引用了意图编码和变量，需要进入 IntentContract 和 VariableContract 的反查链；
 *   - 因此本类额外提供"意图 → 护栏"和"护栏 → 变量"的反查接口。
 */
@Slf4j
@Component
public class GuardRailLoader {

    private static final String CONFIG_PATH = "classpath*:config/routing/guardrails*.yaml";

    /** 通配符：应用到所有案例 */
    private static final String WILDCARD = "*";

    private final AtomicReference<List<GuardRule>> guardRulesRef =
        new AtomicReference<>(new ArrayList<>());

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    // ====================================================================
    // 契约反查索引
    // ====================================================================

    /** guardId -> 该护栏引用的变量集合 */
    private final Map<String, Set<String>> guardIdToVariables = new ConcurrentHashMap<>();

    /** guardId -> 该护栏的意图编码集合（未展开通配符） */
    private final Map<String, Set<String>> guardIdToIntents = new ConcurrentHashMap<>();

    /** 是否已加载过 */
    private volatile boolean loaded = false;

    // ====================================================================
    // 加载入口
    // ====================================================================


    public void init() {
        if (loaded) {
            log.debug("GuardRailLoader 已加载，跳过重复加载");
            return;
        }
        doLoadGuardRules();
        loaded = true;
    }

    /**
     * 全量重新加载。
     * 供 CaseConfigLoader.reloadAll() 调用。
     */
    public synchronized void reloadAll() {
        log.info("🔄 重新加载安全护栏（全量）...");
        guardRulesRef.set(new ArrayList<>());
        guardIdToVariables.clear();
        guardIdToIntents.clear();
        loaded = false;
        doLoadGuardRules();
        loaded = true;
    }

    /**
     * 兼容原有方法名。
     */
    public synchronized void loadGuardRules() {
        reloadAll();
    }

    /**
     * 重载单个案例。
     * 说明：护栏文件通过通配路径统一加载，单案例重载无法精确控制，
     * 这里退化为全量重载。
     */
    public synchronized void reloadCase(String caseId) {
        log.info("🔄 重载安全护栏（案例 {}，退化为全量重载）", caseId);
        reloadAll();
    }

    // ====================================================================
    // 内部加载逻辑
    // ====================================================================

    private void doLoadGuardRules() {
        try {
            PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CONFIG_PATH);

            if (resources.length == 0) {
                log.warn("⚠️ 未找到护栏配置文件，路径: {}", CONFIG_PATH);
                return;
            }

            List<GuardRule> allRules = new ArrayList<>();

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> root = mapper.readValue(is, Map.class);
                    if (root == null) continue;

                    Object guardrailsObj = root.get("guardrails");
                    if (!(guardrailsObj instanceof List)) {
                        log.warn("⚠️ {} 中未找到 guardrails 列表，跳过", resource.getFilename());
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> guardConfigs =
                        (List<Map<String, Object>>) guardrailsObj;

                    int loadedCount = 0;
                    for (Map<String, Object> config : guardConfigs) {
                        try {
                            GuardRule rule = parseGuardRule(config);
                            if (rule != null && rule.getId() != null) {
                                allRules.add(rule);
                                loadedCount++;
                            }
                        } catch (Exception ex) {
                            log.error("❌ 解析规则失败: {}", config.get("id"), ex);
                        }
                    }
                    log.info("✅ 从 {} 加载了 {} 条护栏规则",
                        resource.getFilename(), loadedCount);
                }
            }

            // 排序：priority 降序 + severity BLOCK 优先
            allRules.sort(Comparator
                .comparing((GuardRule r) -> r.getPriority() == null ? 0 : r.getPriority())
                .reversed()
                .thenComparing(r -> r.getSeverity() == null
                    ? GuardRule.Severity.LOG.ordinal()
                    : r.getSeverity().ordinal()));

            guardRulesRef.set(allRules);

            // ★ 构建反查索引
            buildReverseIndexes(allRules);

            long enabledCount = allRules.stream()
                .filter(r -> !Boolean.FALSE.equals(r.getEnabled()))
                .count();
            log.info("🔄 安全护栏配置已加载，共 {} 条规则（启用 {} 条，禁用 {} 条）",
                allRules.size(), enabledCount, allRules.size() - enabledCount);

        } catch (Exception e) {
            log.error("❌ 加载护栏配置失败", e);
        }
    }

    // ====================================================================
    // 构建反查索引
    // ====================================================================

    private void buildReverseIndexes(List<GuardRule> allRules) {
        guardIdToVariables.clear();
        guardIdToIntents.clear();

        for (GuardRule rule : allRules) {
            String guardId = rule.getId();
            if (guardId == null) continue;

            // 1. 提取该护栏引用的变量
            Set<String> vars = new LinkedHashSet<>();
            collectConditionVariables(rule.getCondition(), vars);
            guardIdToVariables.put(guardId, vars);

            // 2. 记录该护栏关联的意图（未展开通配）
            Set<String> intents = new LinkedHashSet<>();
            if (rule.getIntents() != null) {
                intents.addAll(rule.getIntents());
            }
            guardIdToIntents.put(guardId, intents);
        }
    }

    /**
     * 递归提取 Condition 树中引用的变量。
     *
     * <p>Condition 结构（{@link com.ruoyi.acls.guard.Condition}）：
     * <ul>
     *   <li>{@code all}: List&lt;Condition&gt;</li>
     *   <li>{@code any}: List&lt;Condition&gt;</li>
     *   <li>{@code not}: Condition</li>
     *   <li>{@code var} / {@code op} / {@code value} / {@code onMissing}: 叶子节点</li>
     * </ul>
     */
    private void collectConditionVariables(Condition condition, Set<String> out) {
        if (condition == null) return;

        // 叶子：var 字段
        if (condition.getVar() != null && !condition.getVar().isBlank()) {
            out.add(condition.getVar());
        }

        // 递归：all
        if (condition.getAll() != null) {
            for (Condition c : condition.getAll()) {
                collectConditionVariables(c, out);
            }
        }

        // 递归：any
        if (condition.getAny() != null) {
            for (Condition c : condition.getAny()) {
                collectConditionVariables(c, out);
            }
        }

        // 递归：not
        if (condition.getNot() != null) {
            collectConditionVariables(condition.getNot(), out);
        }
    }

    // ====================================================================
    // 解析逻辑（保留原实现）
    // ====================================================================

    @SuppressWarnings("unchecked")
    private GuardRule parseGuardRule(Map<String, Object> config) {
        GuardRule rule = new GuardRule();

        rule.setId((String) config.get("id"));
        rule.setGroup((String) config.get("group"));
        rule.setDescription((String) config.get("description"));
        rule.setTiming((String) config.getOrDefault("timing", "pre_intent"));
        rule.setEnabled(parseBoolean(config.get("enabled"), true));
        rule.setPriority(parseInt(config.get("priority"), 0));

        // 兼容旧字段
        rule.setCaseType((String) config.getOrDefault("caseType", WILDCARD));
        Object statesObj = config.get("states");
        if (statesObj instanceof List) {
            rule.setStates((List<String>) statesObj);
        }

        // trigger.intents
        List<String> intents = new ArrayList<>();
        Object triggerObj = config.get("trigger");
        if (triggerObj instanceof Map) {
            Object intentsObj = ((Map<String, Object>) triggerObj).get("intents");
            if (intentsObj instanceof List) {
                for (Object o : (List<Object>) intentsObj) {
                    if (o != null) intents.add(o.toString());
                }
            }
        }
        // 兼容顶层 intents
        Object legacyIntents = config.get("intents");
        if (legacyIntents instanceof List) {
            for (Object o : (List<Object>) legacyIntents) {
                if (o != null && !intents.contains(o.toString())) {
                    intents.add(o.toString());
                }
            }
        }
        rule.setIntents(intents);

        // condition
        Object conditionObj = config.get("condition");
        if (conditionObj instanceof Map) {
            rule.setCondition(parseCondition((Map<String, Object>) conditionObj));
        } else if (conditionObj instanceof List) {
            Condition all = new Condition();
            List<Condition> list = new ArrayList<>();
            for (Object o : (List<Object>) conditionObj) {
                if (o instanceof Map) {
                    list.add(parseCondition((Map<String, Object>) o));
                }
            }
            all.setAll(list);
            rule.setCondition(all);
        }

        // severity
        String severityStr = String.valueOf(config.getOrDefault("severity", "WARN"));
        try {
            rule.setSeverity(GuardRule.Severity.valueOf(severityStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            rule.setSeverity(GuardRule.Severity.WARN);
        }

        // action
        Object actionObj = config.get("action");
        if (actionObj instanceof Map) {
            Map<String, Object> am = (Map<String, Object>) actionObj;
            GuardRule.Action action = new GuardRule.Action();
            action.setType((String) am.get("type"));
            action.setMessage((String) am.get("message"));
            rule.setAction(action);
        } else {
            rule.setMessage((String) config.get("message"));
            rule.setSuggestion((String) config.get("suggestion"));
        }

        // override
        Object overrideObj = config.get("override");
        if (overrideObj instanceof Map) {
            Map<String, Object> om = (Map<String, Object>) overrideObj;
            GuardRule.OverrideConfig oc = new GuardRule.OverrideConfig();
            oc.setAllowed(parseBoolean(om.get("allowed"), false));
            Object rolesObj = om.get("roles");
            if (rolesObj instanceof List) {
                List<String> roles = new ArrayList<>();
                for (Object o : (List<Object>) rolesObj) {
                    if (o != null) roles.add(o.toString());
                }
                oc.setRoles(roles);
            }
            oc.setRequireReason(parseBoolean(om.get("requireReason"), true));
            rule.setOverride(oc);
        }

        // audit
        Object auditObj = config.get("audit");
        if (auditObj instanceof Map) {
            Map<String, Object> am = (Map<String, Object>) auditObj;
            GuardRule.AuditConfig ac = new GuardRule.AuditConfig();
            ac.setLevel((String) am.get("level"));
            Object fieldsObj = am.get("fields");
            if (fieldsObj instanceof List) {
                List<String> fields = new ArrayList<>();
                for (Object o : (List<Object>) fieldsObj) {
                    if (o != null) fields.add(o.toString());
                }
                ac.setFields(fields);
            }
            rule.setAudit(ac);
        }

        // references
        Object refObj = config.get("references");
        if (refObj instanceof List) {
            List<String> refs = new ArrayList<>();
            for (Object o : (List<Object>) refObj) {
                if (o != null) refs.add(o.toString());
            }
            rule.setReferences(refs);
        }

        return rule;
    }

    @SuppressWarnings("unchecked")
    private Condition parseCondition(Map<String, Object> map) {
        Condition c = new Condition();

        if (map.containsKey("all")) {
            c.setAll(parseConditionList(map.get("all")));
        }
        if (map.containsKey("any")) {
            c.setAny(parseConditionList(map.get("any")));
        }
        if (map.containsKey("not") && map.get("not") instanceof Map) {
            c.setNot(parseCondition((Map<String, Object>) map.get("not")));
        }

        if (map.containsKey("var")) {
            c.setVar(String.valueOf(map.get("var")));
            c.setOp(map.get("op") == null ? null : String.valueOf(map.get("op")));
            c.setValue(map.get("value"));
            c.setOnMissing(map.get("on_missing") == null
                ? null : String.valueOf(map.get("on_missing")));
        }

        return c;
    }

    @SuppressWarnings("unchecked")
    private List<Condition> parseConditionList(Object obj) {
        List<Condition> list = new ArrayList<>();
        if (obj instanceof List) {
            for (Object o : (List<Object>) obj) {
                if (o instanceof Map) {
                    list.add(parseCondition((Map<String, Object>) o));
                }
            }
        }
        return list;
    }

    private Boolean parseBoolean(Object o, boolean defaultValue) {
        if (o == null) return defaultValue;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(o.toString());
    }

    private Integer parseInt(Object o, int defaultValue) {
        if (o == null) return defaultValue;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ====================================================================
    // 原有查询接口（保留）
    // ====================================================================

    public List<GuardRule> getGuardRules() {
        return guardRulesRef.get();
    }

    public List<GuardRule> findMatchingRules(String intentCode) {
        List<GuardRule> matched = new ArrayList<>();
        for (GuardRule rule : getGuardRules()) {
            if (rule.matchesIntent(intentCode)) {
                matched.add(rule);
            }
        }
        return matched;
    }

    public List<GuardRule> findMatchingRules(String intentCode,
                                             Map<String, Object> variables) {
        List<GuardRule> matched = new ArrayList<>();
        for (GuardRule rule : getGuardRules()) {
            if (rule.matches(intentCode, variables)) {
                matched.add(rule);
            }
        }
        matched.sort(Comparator
            .comparing((GuardRule r) -> r.getPriority() == null ? 0 : r.getPriority())
            .reversed()
            .thenComparing(r -> r.getSeverity() == null
                ? GuardRule.Severity.LOG.ordinal()
                : r.getSeverity().ordinal()));
        return matched;
    }

    public List<GuardRule> findMatchingRules(String caseType, String currentState,
                                             String intentCode) {
        List<GuardRule> matched = new ArrayList<>();
        for (GuardRule rule : getGuardRules()) {
            if (rule.getCondition() != null) continue;
            if (rule.matches(caseType, currentState, intentCode)) {
                matched.add(rule);
            }
        }
        matched.sort(Comparator
            .comparing((GuardRule r) -> r.getPriority() == null ? 0 : r.getPriority())
            .reversed()
            .thenComparing(r -> r.getSeverity() == null
                ? GuardRule.Severity.LOG.ordinal()
                : r.getSeverity().ordinal()));
        return matched;
    }

    // ====================================================================
    // 契约构建专用查询接口
    // ====================================================================

    /**
     * 反查：某意图被哪些护栏规则拦截（含通配 caseType）。
     */
    public List<GuardRule> getGuardRulesForIntent(String caseType, String intentCode) {
        if (intentCode == null) return List.of();
        List<GuardRule> matched = new ArrayList<>();
        for (GuardRule rule : getGuardRules()) {
            String ruleCase = rule.getCaseType();
            if (ruleCase != null
                && !WILDCARD.equals(ruleCase)
                && !ruleCase.equals(caseType)) {
                continue;
            }
            if (rule.getIntents() != null && rule.getIntents().contains(intentCode)) {
                matched.add(rule);
            }
        }
        matched.sort(Comparator
            .comparing((GuardRule r) -> r.getPriority() == null ? 0 : r.getPriority())
            .reversed()
            .thenComparing(r -> r.getSeverity() == null
                ? GuardRule.Severity.LOG.ordinal()
                : r.getSeverity().ordinal()));
        return matched;
    }

    /**
     * 契约构建：意图 → 护栏列表映射（按案例）。
     */
    public Map<String, List<GuardRule>> getIntentToGuardsMap(String caseType) {
        Map<String, List<GuardRule>> result = new LinkedHashMap<>();
        for (GuardRule rule : getGuardRules()) {
            String ruleCase = rule.getCaseType();
            if (ruleCase != null
                && !WILDCARD.equals(ruleCase)
                && !ruleCase.equals(caseType)) {
                continue;
            }
            if (rule.getIntents() == null || rule.getIntents().isEmpty()) continue;
            for (String intentCode : rule.getIntents()) {
                if (intentCode == null || WILDCARD.equals(intentCode)) continue;
                result.computeIfAbsent(intentCode, k -> new ArrayList<>()).add(rule);
            }
        }
        result.values().forEach(list -> list.sort(Comparator
            .comparing((GuardRule r) -> r.getPriority() == null ? 0 : r.getPriority())
            .reversed()
            .thenComparing(r -> r.getSeverity() == null
                ? GuardRule.Severity.LOG.ordinal()
                : r.getSeverity().ordinal())));
        return result;
    }

    /**
     * 反查：某护栏引用的变量集合。
     */
    public Set<String> getVariablesForGuard(String guardId) {
        Set<String> vars = guardIdToVariables.get(guardId);
        return vars != null ? Collections.unmodifiableSet(vars) : Collections.emptySet();
    }

    /**
     * 契约构建：护栏 ID → 变量集合映射（全量副本）。
     */
    public Map<String, Set<String>> getGuardToVariablesMap() {
        Map<String, Set<String>> copy = new HashMap<>();
        guardIdToVariables.forEach((k, v) -> copy.put(k, new LinkedHashSet<>(v)));
        return copy;
    }

    /**
     * 契约构建：变量 → 读取它的护栏 ID 集合映射。
     */
    public Map<String, Set<String>> getVariableToGuardsMap() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        guardIdToVariables.forEach((guardId, vars) -> {
            for (String var : vars) {
                result.computeIfAbsent(var, k -> new LinkedHashSet<>()).add(guardId);
            }
        });
        return result;
    }

    /**
     * 列出所有护栏 ID。
     */
    public Set<String> getAllGuardIds() {
        return Collections.unmodifiableSet(guardIdToVariables.keySet());
    }

    /**
     * 反查：某护栏关联的意图（未展开通配）。
     */
    public Set<String> getIntentsForGuard(String guardId) {
        Set<String> intents = guardIdToIntents.get(guardId);
        return intents != null ? Collections.unmodifiableSet(intents) : Collections.emptySet();
    }
}

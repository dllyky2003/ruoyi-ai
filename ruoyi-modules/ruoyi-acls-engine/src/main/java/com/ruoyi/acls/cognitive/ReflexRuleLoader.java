package com.ruoyi.acls.cognitive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.acls.contract.util.GuardExpressionParser;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.npc.Duty;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReflexRuleLoader {

    private static final String COMMON_BASE_PATH = "config/acls/common/";
    private static final String CASES_BASE_PATH = "config/acls/cases/";
    private static final String RULES_FILE = "reflex_rules.json";
    private static final String ADULT_RULES_FILE = "adult_reflex_rules.json";
    private static final String PEDIATRIC_RULES_FILE = "pediatric_reflex_rules.json";
    private static final String DEFAULT_CASE = "common";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** ★ 注入表达式解析器，用于从 condition 提取变量 */
    private final GuardExpressionParser guardExpressionParser;

    /** caseType -> 规则列表 */
    private final Map<String, List<ReflexRule>> ruleCache = new ConcurrentHashMap<>();

    // ====================================================================
    // ★ 新增：契约反查索引
    // ====================================================================

    /** ruleId -> 该规则引用的变量集合 */
    private final Map<String, Set<String>> ruleIdToVariables = new ConcurrentHashMap<>();

    /** caseType -> 该案例下所有规则的 ID 集合（便于诊断） */
    private final Map<String, Set<String>> caseToRuleIds = new ConcurrentHashMap<>();

    /** 是否已加载过 */
    private volatile boolean loaded = false;

    // ====================================================================
    // 加载入口
    // ====================================================================


    public void init() {
        if (loaded) {
            log.debug("ReflexRuleLoader 已加载，跳过重复加载");
            return;
        }
        loadRulesForCase(DEFAULT_CASE);
        loaded = true;
        log.info("✅ 反射规则加载器初始化完成，默认加载成人规则");
    }

    /**
     * 全量重新加载：清空所有缓存，重新加载默认案例。
     *
     * 说明：由于是惰性加载，其他案例会在下次 getRulesForCase 时重新加载。
     * 供 CaseConfigLoader.reloadAll() 调用。
     */
    public synchronized void reloadAll() {
        log.info("🔄 重新加载反射规则（全量）...");
        ruleCache.clear();
        ruleIdToVariables.clear();
        caseToRuleIds.clear();
        loaded = false;
        loadRulesForCase(DEFAULT_CASE);
        loaded = true;
        log.info("✅ 反射规则全量重载完成");
    }

    // ====================================================================
    // 原有查询接口（保留）
    // ====================================================================

    public List<ReflexRule> getRulesForCase(String caseType) {
        if (caseType == null || caseType.trim().isEmpty()) {
            caseType = DEFAULT_CASE;
        }
        if (!ruleCache.containsKey(caseType)) {
            loadRulesForCase(caseType);
        }
        return ruleCache.getOrDefault(caseType, new ArrayList<>());
    }

    // ====================================================================
    // 原有加载逻辑（保留）
    // ====================================================================

    private void loadRulesForCase(String caseType) {
        // 1. 先尝试案例专属
        String casePath = CASES_BASE_PATH + caseType + "/" + RULES_FILE;
        if (loadFromPath(casePath, caseType)) {
            return;
        }

        // 2. 根据年龄段尝试加载对应的通用规则
        String ageGroup = detectAgeGroup(caseType);
        if ("pediatric".equals(ageGroup)) {
            String pediatricPath = COMMON_BASE_PATH + PEDIATRIC_RULES_FILE;
            if (loadFromPath(pediatricPath, caseType)) {
                log.info("📂 案例 {} 使用儿科反射规则", caseType);
                return;
            }
        } else {
            String adultPath = COMMON_BASE_PATH + ADULT_RULES_FILE;
            if (loadFromPath(adultPath, caseType)) {
                log.info("📂 案例 {} 使用成人反射规则", caseType);
                return;
            }
        }

        // 3. 回退到公共 reflex_rules.json
        String commonPath = COMMON_BASE_PATH + RULES_FILE;
        if (loadFromPath(commonPath, DEFAULT_CASE)) {
            List<ReflexRule> commonRules = ruleCache.get(DEFAULT_CASE);
            ruleCache.put(caseType, commonRules != null
                ? new ArrayList<>(commonRules) : new ArrayList<>());
            // ★ 同步反查索引
            syncReverseIndexForAliasCase(caseType, commonRules);
            log.info("📂 案例 {} 使用公共反射规则 (共 {} 条)",
                caseType, ruleCache.get(caseType).size());
            return;
        }

        // 4. 都没有，存空
        ruleCache.putIfAbsent(caseType, new ArrayList<>());
        caseToRuleIds.putIfAbsent(caseType, new HashSet<>());
        log.warn("⚠️ 未找到任何反射规则配置文件，案例 {} 将不具备自主预警能力", caseType);
    }

    private String detectAgeGroup(String caseType) {
        String lower = caseType.toLowerCase();
        if (lower.contains("pediatric") || lower.contains("child")
            || lower.contains("infant") || lower.contains("neonate")
            || lower.contains("baby")) {
            return "pediatric";
        }
        return "adult";
    }

    private boolean loadFromPath(String path, String targetCase) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.debug("配置文件不存在: {}", path);
            return false;
        }

        try (InputStream is = resource.getInputStream()) {
            Map<String, Object> root = objectMapper.readValue(
                is, new TypeReference<Map<String, Object>>() {});
            Object rulesObj = root.get("rules");
            if (!(rulesObj instanceof List)) {
                log.warn("配置文件 {} 中的 rules 字段不是数组", path);
                return false;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) rulesObj;
            if (ruleMaps.isEmpty()) {
                log.warn("配置文件 {} 中的 rules 数组为空", path);
                return false;
            }

            List<ReflexRule> rules = new ArrayList<>();
            Set<String> ruleIds = new HashSet<>();

            for (Map<String, Object> map : ruleMaps) {
                try {
                    String id = (String) map.get("id");
                    String dutyStr = (String) map.get("duty");
                    String condition = (String) map.get("condition");
                    String speech = (String) map.get("speech");
                    String animation = (String) map.get("animation");
                    Integer priority = (Integer) map.get("priority");
                    String type = (String) map.get("type");

                    if (id == null || dutyStr == null || condition == null
                        || speech == null) {
                        log.warn("跳过不完整的反射规则: id={}, duty={}", id, dutyStr);
                        continue;
                    }

                    Duty duty = Duty.valueOf(dutyStr);
                    int pri = priority != null ? priority : 0;

                    ReflexRule rule;
                    if ("action".equals(type) && animation != null
                        && !animation.isEmpty()) {
                        rule = ReflexRule.actionRule(id, duty, condition, speech,
                            animation, pri);
                    } else {
                        rule = ReflexRule.speechRule(id, duty, condition, speech, pri);
                    }
                    rules.add(rule);
                    ruleIds.add(id);

                    // ★ 提取 condition 里的变量，存入反查索引
                    Set<String> vars =
                        guardExpressionParser.extractVariables(condition);
                    ruleIdToVariables.put(id, vars);

                } catch (Exception e) {
                    log.error("解析单条反射规则失败: {}", e.getMessage(), e);
                }
            }

            if (rules.isEmpty()) {
                log.warn("配置文件 {} 解析后无有效规则", path);
                return false;
            }

            ruleCache.put(targetCase, rules);
            caseToRuleIds.put(targetCase, ruleIds);
            log.info("✅ 从 {} 加载了 {} 条反射规则 (案例: {})",
                path, rules.size(), targetCase);
            return true;

        } catch (Exception e) {
            log.error("❌ 加载反射规则文件失败: {}", path, e);
            return false;
        }
    }

    /**
     * ★ 当某案例复用 DEFAULT_CASE 的规则时，把 caseToRuleIds 也同步一份。
     */
    private void syncReverseIndexForAliasCase(String caseType,
                                              List<ReflexRule> commonRules) {
        if (commonRules == null) {
            caseToRuleIds.put(caseType, new HashSet<>());
            return;
        }
        Set<String> ids = new HashSet<>();
        for (ReflexRule r : commonRules) {
            if (r.getId() != null) ids.add(r.getId());
        }
        caseToRuleIds.put(caseType, ids);
    }

    // ====================================================================
    // 原有校验与重载
    // ====================================================================

    public void validateRules(String caseType, ResolvedCaseTypeConfig stateMachineConfig) {
        List<ReflexRule> rules = getRulesForCase(caseType);
        if (rules.isEmpty()) return;
        for (ReflexRule rule : rules) {
            try {
                Duty.valueOf(rule.getDuty().name());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("反射规则 " + rule.getId()
                    + " 使用了无效的 Duty: " + rule.getDuty());
            }
        }
    }

    public void clearCache(String caseType) {
        ruleCache.remove(caseType);
        // ★ 同时清理反查索引
        Set<String> ids = caseToRuleIds.remove(caseType);
        if (ids != null) {
            // 注意：ruleIdToVariables 是全局的，只有当该 ruleId 不再被任何案例引用时才删除
            // 为简化实现，这里不主动删除 ruleIdToVariables，
            // 因为规则 ID 通常是全局唯一的（如 R001、R002），跨案例复用概率低。
        }
    }

    public void reloadCase(String caseType) {
        clearCache(caseType);
        loadRulesForCase(caseType);
        log.info("🔄 案例 {} 的反射规则已重新加载", caseType);
    }

    // ====================================================================
    // ★ 新增：契约构建专用查询接口
    // ====================================================================

    /**
     * 反查：某条反射规则引用的变量集合。
     */
    public Set<String> getVariablesForRule(String ruleId) {
        Set<String> vars = ruleIdToVariables.get(ruleId);
        return vars != null ? Collections.unmodifiableSet(vars) : Collections.emptySet();
    }

    /**
     * 契约构建：变量 → 引用它的反射规则 ID 集合。
     *
     * 用于填充 VariableContract.readByReflexRules。
     */
    public Map<String, Set<String>> getVariableToRulesMap() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        ruleIdToVariables.forEach((ruleId, vars) -> {
            for (String var : vars) {
                result.computeIfAbsent(var, k -> new LinkedHashSet<>()).add(ruleId);
            }
        });
        return result;
    }

    /**
     * 契约构建：案例 → 反射规则列表（返回只读视图）。
     */
    public Map<String, List<ReflexRule>> getCaseToRulesMap() {
        Map<String, List<ReflexRule>> copy = new LinkedHashMap<>();
        ruleCache.forEach((caseType, rules) ->
            copy.put(caseType, Collections.unmodifiableList(rules)));
        return copy;
    }

    /**
     * 列出所有已加载的案例 ID。
     */
    public Set<String> getAllLoadedCaseTypes() {
        return Collections.unmodifiableSet(ruleCache.keySet());
    }

    /**
     * 按 ID 查询某案例下的单条反射规则。
     */
    public ReflexRule getRuleById(String caseType, String ruleId) {
        if (caseType == null || ruleId == null) return null;
        List<ReflexRule> rules = getRulesForCase(caseType);
        for (ReflexRule r : rules) {
            if (ruleId.equals(r.getId())) return r;
        }
        return null;
    }

    /**
     * 列出某案例下所有规则的 ID 集合。
     */
    public Set<String> getRuleIdsForCase(String caseType) {
        Set<String> ids = caseToRuleIds.get(caseType);
        if (ids != null) return Collections.unmodifiableSet(ids);
        // 兜底：惰性触发加载
        List<ReflexRule> rules = getRulesForCase(caseType);
        Set<String> result = new LinkedHashSet<>();
        for (ReflexRule r : rules) {
            if (r.getId() != null) result.add(r.getId());
        }
        return result;
    }

    /**
     * 诊断：找出"引用了未定义变量"的反射规则。
     *
     * @param definedVariables 该案例下所有已定义变量的名称集合
     * @return ruleId -> 未定义变量集合
     */
    public Map<String, Set<String>> findRulesWithUndefinedVariables(
        String caseType, Set<String> definedVariables) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        List<ReflexRule> rules = getRulesForCase(caseType);
        for (ReflexRule rule : rules) {
            Set<String> vars = ruleIdToVariables.get(rule.getId());
            if (vars == null || vars.isEmpty()) continue;
            Set<String> undefined = new LinkedHashSet<>(vars);
            undefined.removeAll(definedVariables);
            if (!undefined.isEmpty()) {
                result.put(rule.getId(), undefined);
            }
        }
        return result;
    }
}

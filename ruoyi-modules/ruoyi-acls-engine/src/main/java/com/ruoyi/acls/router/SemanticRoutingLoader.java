package com.ruoyi.acls.router;

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
 * 语义路由表加载器（支持 import）。
 * <p>
 * 从 classpath:config/routing/semantic-routing.yaml 开始加载，
 * 支持通过 routes 中的 import 字段递归导入其他文件。
 *
 * <p>契约构建视角：
 * <ul>
 *   <li>路由规则通过 {@code (intent, caseType, state)} 三元组定位目标；</li>
 *   <li>{@code targetType} 支持 SKILL / COMPLEX / ATOMIC / CHAIN；</li>
 *   <li>只有 SKILL 和 COMPLEX 类型能进入变量反查链（因为它们有 SkillMeta.writes）；</li>
 *   <li>契约构建时按 (caseType, state) 逐条转移查询路由，而不是一次性建立全局映射。</li>
 * </ul>
 */
@Slf4j
@Component
public class SemanticRoutingLoader {

    private static final String ROOT_CONFIG = "classpath:config/routing/semantic-routing.yaml";

    /** 通配符：应用到所有案例 / 所有状态 */
    private static final String WILDCARD = "*";

    /** 能进入契约变量反查链的 targetType */
    private static final Set<String> CONTRACT_ELIGIBLE_TARGET_TYPES =
        Set.of("SKILL", "COMPLEX");

    /** 当前有效的路由规则列表（线程安全） */
    private final AtomicReference<List<RouteRule>> routeRulesRef =
        new AtomicReference<>(new ArrayList<>());

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private final ThreadLocal<Set<String>> loadingStack =
        ThreadLocal.withInitial(HashSet::new);

    /** 是否已加载过 */
    private volatile boolean loaded = false;

    // ====================================================================
    // 加载入口
    // ====================================================================


    public void init() {
        if (loaded) {
            log.debug("SemanticRoutingLoader 已加载，跳过重复加载");
            return;
        }
        loadRouting();
        loaded = true;
    }

    /**
     * 全量重新加载。
     * 供 CaseConfigLoader.reloadAll() 调用。
     */
    public synchronized void reloadAll() {
        log.info("🔄 重新加载语义路由表（全量）...");
        loaded = false;
        loadRouting();
        loaded = true;
    }

    /**
     * 兼容原有方法名。
     */
    public synchronized void reloadRouting() {
        reloadAll();
    }

    /**
     * 重载单个案例。
     * 说明：路由规则通过 import 链跨案例共享，单案例重载无法精确控制，
     * 这里退化为全量重载。若后续需要真正按案例隔离，需把路由按 caseType
     * 拆分到独立文件，并由本类维护 caseId -> 文件路径的映射。
     */
    public synchronized void reloadCase(String caseId) {
        log.info("🔄 重载语义路由（案例 {}，退化为全量重载）", caseId);
        reloadAll();
    }

    // ====================================================================
    // 内部加载逻辑
    // ====================================================================

    private void loadRouting() {
        try {
            List<RouteRule> allRules = new ArrayList<>();
            loadingStack.get().clear();
            loadFile(ROOT_CONFIG, allRules);
            routeRulesRef.set(allRules);
            log.info("🔄 语义路由表加载完成，共 {} 条规则", allRules.size());
        } catch (Exception e) {
            log.error("❌ 加载语义路由表失败", e);
            // 保留上一次成功加载的规则
        }
    }

    private void loadFile(String path, List<RouteRule> accumulator) throws Exception {
        Set<String> stack = loadingStack.get();
        if (stack.contains(path)) {
            log.warn("⚠️ 检测到循环 import，跳过: {}", path);
            return;
        }
        stack.add(path);

        try {
            PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(path);

            for (Resource resource : resources) {
                if (!resource.exists()) {
                    log.warn("⚠️ 文件不存在: {}", path);
                    continue;
                }

                String filename = resource.getFilename();
                if (filename != null && filename.equals("guardrails.yaml")) {
                    log.debug("⏭️ 跳过 guardrails.yaml");
                    continue;
                }

                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> root = mapper.readValue(is, Map.class);
                    Object routesObj = root.get("routes");
                    if (!(routesObj instanceof List)) {
                        log.warn("⚠️ 文件 {} 中缺少 routes 列表", filename);
                        continue;
                    }

                    List<Map<String, Object>> routes =
                        (List<Map<String, Object>>) routesObj;
                    int loadedCount = 0;

                    for (Map<String, Object> config : routes) {
                        String importPath = (String) config.get("import");
                        if (importPath != null && !importPath.isEmpty()) {
                            String fullPath = resolveImportPath(importPath, resource);
                            loadFile(fullPath, accumulator);
                            continue;
                        }

                        RouteRule rule = parseRouteRule(config);
                        if (rule != null && rule.getIntent() != null) {
                            accumulator.add(rule);
                            loadedCount++;
                        } else {
                            log.warn("⚠️ 规则 {} 缺少 intent 字段或解析失败，已忽略",
                                config.get("id"));
                        }
                    }

                    log.info("✅ 从 {} 加载了 {} 条路由规则", filename, loadedCount);
                } catch (Exception e) {
                    log.error("❌ 加载文件失败: {}", filename, e);
                }
            }
        } finally {
            stack.remove(path);
        }
    }

    private String resolveImportPath(String importPath, Resource currentResource) {
        if (importPath.startsWith("classpath:") || importPath.startsWith("file:")) {
            return importPath;
        }

        try {
            String currentPath = currentResource.getURL().getPath();
            int lastSlash = currentPath.lastIndexOf('/');
            if (lastSlash > 0) {
                String baseDir = currentPath.substring(0, lastSlash + 1);
                if (currentPath.startsWith("jar:")) {
                    int bangIndex = currentPath.indexOf("!/");
                    if (bangIndex > 0) {
                        String jarPart = currentPath.substring(0, bangIndex + 2);
                        String resourcePart = currentPath.substring(bangIndex + 2);
                        int resourceLastSlash = resourcePart.lastIndexOf('/');
                        if (resourceLastSlash > 0) {
                            String baseResourcePath =
                                resourcePart.substring(0, resourceLastSlash + 1);
                            return jarPart + baseResourcePath + importPath;
                        }
                    }
                }
                return "file:" + baseDir + importPath;
            }
        } catch (Exception e) {
            log.warn("⚠️ 无法解析相对路径 {}，回退到 classpath:config/routing/{}",
                importPath, importPath);
        }

        return "classpath:config/routing/" + importPath;
    }

    @SuppressWarnings("unchecked")
    private RouteRule parseRouteRule(Map<String, Object> config) {
        try {
            RouteRule rule = new RouteRule();

            rule.setId((String) config.get("id"));
            rule.setCaseType((String) config.get("caseType"));
            rule.setState((String) config.get("state"));
            rule.setIntent((String) config.get("intent"));

            if (rule.getIntent() == null) {
                return null;
            }

            rule.setTargetType((String) config.getOrDefault("targetType", "SKILL"));
            rule.setTargetId((String) config.get("targetId"));

            Map<String, Object> params = (Map<String, Object>) config.get("params");
            if (params != null) {
                rule.setParams(new ConcurrentHashMap<>(params));
            }

            Map<String, String> paramMapping =
                (Map<String, String>) config.get("paramMapping");
            if (paramMapping != null) {
                rule.setParamMapping(new ConcurrentHashMap<>(paramMapping));
            }

            String condition = (String) config.get("condition");
            if (condition != null) {
                rule.setCondition(condition);
            }

            Integer priority = (Integer) config.get("priority");
            rule.setPriority(priority != null ? priority : 0);

            return rule;
        } catch (Exception e) {
            log.warn("⚠️ 解析路由规则失败: {}", config.get("id"), e);
            return null;
        }
    }

    // ====================================================================
    // 原有查询接口（保留）
    // ====================================================================

    public List<RouteRule> getRouteRules() {
        return routeRulesRef.get();
    }

    public List<RouteRule> findMatchingRules(String intent, String caseType, String state) {
        List<RouteRule> matched = new ArrayList<>();
        for (RouteRule rule : getRouteRules()) {
            if (rule.matches(intent, caseType, state)) {
                matched.add(rule);
            }
        }
        matched.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return matched;
    }

    public boolean hasRouteForIntent(String intentId) {
        if (intentId == null) return false;
        for (RouteRule rule : getRouteRules()) {
            String ruleIntent = rule.getIntent();
            if (ruleIntent != null
                && (ruleIntent.equals(intentId) || WILDCARD.equals(ruleIntent))) {
                return true;
            }
        }
        return false;
    }

    // ====================================================================
    // 契约构建专用查询接口
    // ====================================================================

    /**
     * 建立"意图 → 技能 ID"的映射（按案例维度，忽略 state）。
     *
     * <p>语义：只匹配 state 为 {@code *} 或 null 的路由规则（即不依赖具体状态的通用路由）。
     * 若需要按状态精确查询，请用 {@link #getIntentToSkillMap(String, String)}。
     *
     * @param caseType 案例类型，传 null 表示只取通配规则
     * @return 意图编码 -> 技能 ID
     */
    public Map<String, String> getIntentToSkillMap(String caseType) {
        return getIntentToSkillMap(caseType, null);
    }

    /**
     * 建立"意图 → 技能 ID"的映射（按案例 + 状态维度）。
     *
     * <p>规则：
     * <ol>
     *   <li>只采纳 targetType 为 SKILL 或 COMPLEX 且 targetId 非空的规则；</li>
     *   <li>caseType 匹配："*"（通配）或等于指定 caseType；</li>
     *   <li>state 匹配："*" 或 null（通配）或等于指定 state；</li>
     *   <li>同一意图有多条规则时，按优先级 + 精确度排序，取最高的一条。</li>
     * </ol>
     *
     * @param caseType 案例类型，传 null 表示只取通配规则
     * @param state    状态，传 null 表示只取通配规则
     * @return 意图编码 -> 技能 ID
     */
    public Map<String, String> getIntentToSkillMap(String caseType, String state) {
        Map<String, RouteRule> bestRuleByIntent = new HashMap<>();

        for (RouteRule rule : getRouteRules()) {
            if (!isContractEligible(rule)) continue;

            String intent = rule.getIntent();
            if (intent == null || WILDCARD.equals(intent)) continue;

            if (!matchCaseType(rule.getCaseType(), caseType)) continue;
            if (!matchState(rule.getState(), state)) continue;

            RouteRule existing = bestRuleByIntent.get(intent);
            if (existing == null
                || compareRulePriority(rule, existing, caseType, state) > 0) {
                bestRuleByIntent.put(intent, rule);
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        bestRuleByIntent.forEach((k, v) -> result.put(k, v.getTargetId()));
        return result;
    }

    /**
     * 建立"意图 → 路由规则"的映射（按案例维度）。
     * 契约构建器若需要 params / condition 等细节，可用这个接口。
     */
    public Map<String, RouteRule> getIntentToRouteMap(String caseType) {
        return getIntentToRouteMap(caseType, null);
    }

    /**
     * 建立"意图 → 路由规则"的映射（按案例 + 状态维度）。
     */
    public Map<String, RouteRule> getIntentToRouteMap(String caseType, String state) {
        Map<String, RouteRule> result = new HashMap<>();

        for (RouteRule rule : getRouteRules()) {
            if (!isContractEligible(rule)) continue;

            String intent = rule.getIntent();
            if (intent == null || WILDCARD.equals(intent)) continue;

            if (!matchCaseType(rule.getCaseType(), caseType)) continue;
            if (!matchState(rule.getState(), state)) continue;

            RouteRule existing = result.get(intent);
            if (existing == null
                || compareRulePriority(rule, existing, caseType, state) > 0) {
                result.put(intent, rule);
            }
        }
        return result;
    }

    /**
     * 列出路由表里出现过的所有 caseType（不含通配）。
     * 用于 CaseConfigLoader 反查"哪些案例有专属路由"。
     */
    public Set<String> getAllCaseTypes() {
        Set<String> result = new HashSet<>();
        for (RouteRule rule : getRouteRules()) {
            String ct = rule.getCaseType();
            if (ct != null && !WILDCARD.equals(ct)) {
                result.add(ct);
            }
        }
        return result;
    }

    /**
     * 列出某个 caseType 下所有意图编码。
     */
    public Set<String> getIntentsForCase(String caseType) {
        Set<String> result = new LinkedHashSet<>();
        for (RouteRule rule : getRouteRules()) {
            String intent = rule.getIntent();
            if (intent == null || WILDCARD.equals(intent)) continue;
            if (matchCaseType(rule.getCaseType(), caseType)) {
                result.add(intent);
            }
        }
        return result;
    }

    // ====================================================================
    // 契约诊断专用接口
    // ====================================================================

    /**
     * 反查：某意图在指定案例下路由到哪些目标类型。
     *
     * <p>用于契约诊断：意图被路由到 ATOMIC/CHAIN 时无法进入变量反查链。
     *
     * @return targetType -> targetId（同 targetType 保留优先级最高的一条）
     */
    public Map<String, String> getTargetTypesForIntent(String caseType, String intentCode) {
        Map<String, String> result = new LinkedHashMap<>();
        if (intentCode == null) return result;
        for (RouteRule rule : getRouteRules()) {
            if (!intentCode.equals(rule.getIntent())) continue;
            if (!matchCaseType(rule.getCaseType(), caseType)) continue;
            String tt = rule.getTargetType();
            if (tt == null) continue;
            // 同 targetType 保留第一条（按已排序的规则列表）
            result.putIfAbsent(tt, rule.getTargetId());
        }
        return result;
    }

    /**
     * 反查：所有路由到 ATOMIC 或 CHAIN 的意图。
     *
     * <p>用于契约启动时打印"这些意图不进变量反查链"的诊断信息。
     *
     * @return 意图编码 -> "targetType:targetId"
     */
    public Map<String, String> getAllNonSkillRoutedIntents(String caseType) {
        Map<String, String> result = new LinkedHashMap<>();
        for (RouteRule rule : getRouteRules()) {
            String tt = rule.getTargetType();
            if (tt == null) continue;
            if (CONTRACT_ELIGIBLE_TARGET_TYPES.contains(tt.toUpperCase())) continue;
            if (!matchCaseType(rule.getCaseType(), caseType)) continue;

            String intent = rule.getIntent();
            if (intent == null || WILDCARD.equals(intent)) continue;
            result.putIfAbsent(intent, tt + ":" + rule.getTargetId());
        }
        return result;
    }

    /**
     * 诊断：找出路由表里引用了同一意图但优先级相同、无法确定唯一目标的情况。
     *
     * @return 冲突描述列表（空表示无冲突）
     */
    public List<String> findRoutingConflicts(String caseType, String state) {
        List<String> conflicts = new ArrayList<>();

        // intent -> (caseType+state) -> 候选规则列表
        Map<String, Map<String, List<RouteRule>>> grouped = new LinkedHashMap<>();

        for (RouteRule rule : getRouteRules()) {
            if (!isContractEligible(rule)) continue;
            String intent = rule.getIntent();
            if (intent == null || WILDCARD.equals(intent)) continue;
            if (!matchCaseType(rule.getCaseType(), caseType)) continue;
            if (!matchState(rule.getState(), state)) continue;

            String key = (rule.getCaseType() == null ? WILDCARD : rule.getCaseType())
                + "|" + (rule.getState() == null ? WILDCARD : rule.getState());
            grouped.computeIfAbsent(intent, k -> new LinkedHashMap<>())
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(rule);
        }

        for (Map.Entry<String, Map<String, List<RouteRule>>> e : grouped.entrySet()) {
            for (Map.Entry<String, List<RouteRule>> inner : e.getValue().entrySet()) {
                List<RouteRule> rules = inner.getValue();
                if (rules.size() <= 1) continue;

                int maxPriority = rules.stream()
                    .mapToInt(RouteRule::getPriority).max().orElse(0);
                long maxCount = rules.stream()
                    .filter(r -> r.getPriority() == maxPriority).count();
                if (maxCount > 1) {
                    conflicts.add(String.format(
                        "意图 %s 在 [%s] 下有 %d 条优先级相同的路由规则，无法确定唯一目标",
                        e.getKey(), inner.getKey(), maxCount));
                }
            }
        }
        return conflicts;
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    /**
     * 判断路由规则的目标类型是否可进契约反查链。
     * 只有 SKILL 和 COMPLEX 有 SkillMeta.writes，其余（ATOMIC / CHAIN）不能。
     */
    private boolean isContractEligible(RouteRule rule) {
        String tt = rule.getTargetType();
        if (tt == null || tt.isBlank()) return false;
        if (!CONTRACT_ELIGIBLE_TARGET_TYPES.contains(tt.toUpperCase())) return false;
        return rule.getTargetId() != null && !rule.getTargetId().isBlank();
    }

    /**
     * 判断路由规则的 caseType 是否匹配查询的 caseType。
     *   - ruleCaseType == null：视为通配
     *   - ruleCaseType == "*"：总是匹配
     *   - ruleCaseType == caseType：匹配
     *   - caseType == null：只取通配规则
     */
    private boolean matchCaseType(String ruleCaseType, String caseType) {
        if (ruleCaseType == null || WILDCARD.equals(ruleCaseType)) return true;
        if (caseType == null) return false;
        return caseType.equals(ruleCaseType);
    }

    /**
     * 判断路由规则的 state 是否匹配查询的 state。
     *   - ruleState == null 或 "*"：通配，匹配任何 state
     *   - queryState == null：只匹配通配规则
     *   - 否则要求相等
     */
    private boolean matchState(String ruleState, String queryState) {
        if (ruleState == null || WILDCARD.equals(ruleState)) return true;
        if (queryState == null) return false;
        return ruleState.equals(queryState);
    }

    /**
     * 比较优先级（数值越大越优先）。
     *
     * <p>相同 priority 时的精确度排序：
     * <ol>
     *   <li>state 精确 > state 通配</li>
     *   <li>caseType 精确 > caseType 通配</li>
     * </ol>
     */
    private int compareRulePriority(RouteRule a, RouteRule b,
                                    String caseType, String state) {
        int p = Integer.compare(a.getPriority(), b.getPriority());
        if (p != 0) return p;

        // state 精确性
        boolean aStateExact = state != null && state.equals(a.getState());
        boolean bStateExact = state != null && state.equals(b.getState());
        if (aStateExact != bStateExact) return aStateExact ? 1 : -1;

        // caseType 精确性
        boolean aCaseExact = caseType != null && caseType.equals(a.getCaseType());
        boolean bCaseExact = caseType != null && caseType.equals(b.getCaseType());
        if (aCaseExact != bCaseExact) return aCaseExact ? 1 : -1;

        return 0;
    }
}

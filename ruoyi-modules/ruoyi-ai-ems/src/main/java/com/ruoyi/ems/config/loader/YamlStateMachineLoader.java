package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.StateMachineConfig;
import com.ruoyi.ems.config.StateMachineDefinition;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.domain.PhaseDefinition;
import com.ruoyi.ems.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class YamlStateMachineLoader {

    private final ResourcePatternResolver resourceResolver;
    private final VariableMetaLoader variableMetaManager;

    public YamlStateMachineLoader(ResourcePatternResolver resourceResolver,
                                  VariableMetaLoader variableMetaManager) {
        this.resourceResolver = resourceResolver;
        this.variableMetaManager = variableMetaManager;
    }

    private final Map<String, ResolvedCaseTypeConfig> configCache = new ConcurrentHashMap<>();
    private final Map<String, String> parentMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> inheritanceChainCache = new ConcurrentHashMap<>();

    private final Yaml yaml = new Yaml();

    /** 幂等保护标志 */
    private volatile boolean loaded = false;

    // ====================================================================
    // 启动入口
    // ====================================================================

    public void init() {
        if (loaded) {
            log.debug("YamlStateMachineLoader 已加载，跳过重复加载");
            return;
        }
        try {
            loadAllConfigs();
            loaded = true;
        } catch (IOException e) {
            log.error("❌ 状态机配置加载失败", e);
            throw new RuntimeException("状态机配置加载失败", e);
        }
    }

    /**
     * 全量重载（统一命名）。
     * 供 CaseConfigLoader.reloadAll() 调用。
     */
    public synchronized void reloadAll() {
        log.info("🔄 全量重载状态机配置...");
        configCache.clear();
        parentMap.clear();
        inheritanceChainCache.clear();
        loaded = false;
        try {
            loadAllConfigs();
            loaded = true;
        } catch (IOException e) {
            log.error("❌ 状态机配置全量重载失败", e);
            throw new RuntimeException("状态机配置全量重载失败", e);
        }
    }

    // ====================================================================
    // 加载逻辑
    // ====================================================================

    public void loadAllConfigs() throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("⏳ 开始加载 YAML 状态机配置...");

        Resource[] allResources = resourceResolver.getResources("classpath:config/acls/**/_flow.yaml");
        Set<Resource> resourceSet = new LinkedHashSet<>(Arrays.asList(allResources));

        Resource[] resources = resourceSet.toArray(new Resource[0]);
        log.info("🔍 扫描到 {} 个 _flow.yaml 文件（去重后）", resources.length);
        for (Resource resource : resources) {
            log.info("   - {}", resource.getURL().getPath());
        }

        Map<String, StateMachineConfig> rawConfigs = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.startsWith(".")) {
                continue;
            }
            if (!filename.endsWith("_flow.yaml")) {
                log.debug("⏭️ 跳过非流程文件: {}", filename);
                continue;
            }

            try (InputStream is = resource.getInputStream()) {
                StateMachineConfig config = yaml.loadAs(is, StateMachineConfig.class);
                if (config.getCaseId() == null || config.getCaseId().isEmpty()) {
                    log.warn("⚠️ 文件 {} 缺少 caseId，跳过加载", filename);
                    continue;
                }
                if (rawConfigs.containsKey(config.getCaseId())) {
                    log.warn("⚠️ 重复的 caseId: {}，文件 {} 将被覆盖",
                        config.getCaseId(), filename);
                }
                rawConfigs.put(config.getCaseId(), config);
                log.info("📄 加载 YAML: {} (版本: {})",
                    config.getCaseId(), config.getVersion());
            } catch (Exception e) {
                log.error("❌ 加载 YAML 失败: {}", filename, e);
                throw new ConfigurationException("YAML 加载失败: " + filename, e);
            }
        }

        if (rawConfigs.isEmpty()) {
            log.warn("⚠️ 未找到任何 _flow.yaml 配置文件");
            return;
        }

        for (StateMachineConfig config : rawConfigs.values()) {
            StateMachineDefinition sm = config.getStateMachine();
            String parent = config.getParent();
            if (sm != null && parent != null && !parent.isEmpty()) {
                if (!rawConfigs.containsKey(parent)) {
                    log.warn("⚠️ 案例 {} 的父级 {} 不存在，将视为无继承",
                        config.getCaseId(), parent);
                } else {
                    parentMap.put(config.getCaseId(), parent);
                }
            }
        }

        List<String> sortedCaseIds = topologicalSort(rawConfigs.keySet());

        for (String caseId : sortedCaseIds) {
            ResolvedCaseTypeConfig resolved = resolveConfig(caseId, rawConfigs);
            variableMetaManager.validateStateMachine(caseId, resolved);
            configCache.put(caseId, resolved);
            inheritanceChainCache.put(caseId, buildInheritanceChain(caseId));

            log.info("✅ 合并完成: {} (父级: {}, 状态数: {}, 转移数: {}, 常驻意图数: {}, 顶层阶段数: {}, 叶子阶段数: {}, 深度: {})",
                caseId,
                resolved.getParent() != null ? resolved.getParent() : "无",
                resolved.getStates().size(),
                resolved.getTransitions().size(),
                resolved.getAlwaysAllowedIntents() != null
                    ? resolved.getAlwaysAllowedIntents().size() : 0,
                resolved.getPhases() != null ? resolved.getPhases().size() : 0,
                resolved.getLeafPhases().size(),
                inheritanceChainCache.get(caseId).size() - 1
            );
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("🎯 YAML 配置加载完成，共 {} 个案例，耗时 {}ms",
            configCache.size(), elapsed);
    }

    // ====================================================================
    // 配置解析与合并
    // ====================================================================

    private ResolvedCaseTypeConfig resolveConfig(String caseId,
                                                 Map<String, StateMachineConfig> allConfigs) {
        StateMachineConfig current = allConfigs.get(caseId);
        if (current == null) {
            throw new ConfigurationException("未找到案例配置: " + caseId);
        }

        ResolvedCaseTypeConfig resolved = new ResolvedCaseTypeConfig();
        resolved.setCode(caseId);
        resolved.setCaseName(current.getCaseName());
        resolved.setVersion(current.getVersion());
        resolved.setParent(current.getParent());

        StateMachineDefinition sm = current.getStateMachine();
        if (sm == null) {
            throw new ConfigurationException("案例 " + caseId + " 缺少 stateMachine 节点");
        }

        // --- 如果有父级，先继承父级配置 ---
        if (current.getParent() != null && !current.getParent().isEmpty()
            && allConfigs.containsKey(current.getParent())) {

            ResolvedCaseTypeConfig parentResolved = resolveConfig(current.getParent(), allConfigs);

            resolved.setStates(new ArrayList<>(parentResolved.getStates()));
            resolved.setEndStates(new ArrayList<>(parentResolved.getEndStates()));
            resolved.setTransitions(new ArrayList<>(parentResolved.getTransitions()));
            resolved.setIntentToStateMapping(new LinkedHashMap<>(parentResolved.getIntentToStateMapping()));
            resolved.setGlobalEventIntents(new LinkedHashSet<>(parentResolved.getGlobalEventIntents()));
            resolved.setAlwaysAllowedIntents(new LinkedHashSet<>(parentResolved.getAlwaysAllowedIntents()));
            resolved.setInitialState(parentResolved.getInitialState());
            resolved.setAllowUnlistedTransitions(parentResolved.isAllowUnlistedTransitions());
            resolved.setDefaultTransitionAction(parentResolved.getDefaultTransitionAction());

            if (parentResolved.getStateDescriptions() != null) {
                resolved.setStateDescriptions(new LinkedHashMap<>(parentResolved.getStateDescriptions()));
            }

            if (parentResolved.getResetVarsOnArrival() != null) {
                resolved.setResetVarsOnArrival(new ArrayList<>(parentResolved.getResetVarsOnArrival()));
            } else {
                resolved.setResetVarsOnArrival(new ArrayList<>());
            }

            // 继承父级 onEnterRules
            if (parentResolved.getOnEnterRules() != null) {
                resolved.setOnEnterRules(new LinkedHashMap<>(parentResolved.getOnEnterRules()));
            } else {
                resolved.setOnEnterRules(new LinkedHashMap<>());
            }

            // ★ Phase SM：继承父级 phases 与 initialPhase
            resolved.setPhases(parentResolved.getPhases() != null
                ? new ArrayList<>(parentResolved.getPhases())
                : new ArrayList<>());
            resolved.setInitialPhase(parentResolved.getInitialPhase());

            // ★ 继承父级 overviewExcludeStates
            resolved.setOverviewExcludeStates(parentResolved.getOverviewExcludeStates() != null
                ? new LinkedHashSet<>(parentResolved.getOverviewExcludeStates())
                : new LinkedHashSet<>());

        } else {
            // 无父级，初始化空集合
            resolved.setStates(new ArrayList<>());
            resolved.setEndStates(new ArrayList<>());
            resolved.setTransitions(new ArrayList<>());
            resolved.setIntentToStateMapping(new LinkedHashMap<>());
            resolved.setGlobalEventIntents(new LinkedHashSet<>());
            resolved.setAlwaysAllowedIntents(new LinkedHashSet<>());
            resolved.setAllowUnlistedTransitions(false);
            resolved.setStateDescriptions(new LinkedHashMap<>());
            resolved.setResetVarsOnArrival(new ArrayList<>());
            resolved.setOnEnterRules(new LinkedHashMap<>());
            // ★ Phase SM：空集合
            resolved.setPhases(new ArrayList<>());
            resolved.setInitialPhase(null);
            // ★ overviewExcludeStates 空集合
            resolved.setOverviewExcludeStates(new LinkedHashSet<>());
        }

        // --- 应用当前配置 ---
        if (sm.getStates() != null && !sm.getStates().isEmpty()) {
            Set<String> stateSet = new LinkedHashSet<>(resolved.getStates());
            stateSet.addAll(sm.getStates());
            resolved.setStates(new ArrayList<>(stateSet));
        }

        if (sm.getEndStates() != null && !sm.getEndStates().isEmpty()) {
            Set<String> endSet = new LinkedHashSet<>(resolved.getEndStates());
            endSet.addAll(sm.getEndStates());
            resolved.setEndStates(new ArrayList<>(endSet));
        }

        if (sm.getInitial() != null && !sm.getInitial().isEmpty()) {
            resolved.setInitialState(sm.getInitial());
        }

        if (sm.getTransitions() != null && !sm.getTransitions().isEmpty()) {
            resolved.getTransitions().addAll(sm.getTransitions());
        }

        if (sm.getIntentToStateMapping() != null && !sm.getIntentToStateMapping().isEmpty()) {
            resolved.getIntentToStateMapping().putAll(sm.getIntentToStateMapping());
        }

        if (sm.getGlobalEventIntents() != null && !sm.getGlobalEventIntents().isEmpty()) {
            resolved.getGlobalEventIntents().addAll(sm.getGlobalEventIntents());
        }

        if (sm.getAlwaysAllowedIntents() != null && !sm.getAlwaysAllowedIntents().isEmpty()) {
            resolved.setAlwaysAllowedIntents(new LinkedHashSet<>(sm.getAlwaysAllowedIntents()));
        }

        if (sm.isAllowUnlistedTransitions()) {
            resolved.setAllowUnlistedTransitions(sm.isAllowUnlistedTransitions());
        }
        if (sm.getDefaultTransitionAction() != null && !sm.getDefaultTransitionAction().isEmpty()) {
            resolved.setDefaultTransitionAction(sm.getDefaultTransitionAction());
        }

        if (current.getStateDescriptions() != null && !current.getStateDescriptions().isEmpty()) {
            Map<String, String> currentDescs = resolved.getStateDescriptions();
            if (currentDescs == null) {
                currentDescs = new LinkedHashMap<>();
                resolved.setStateDescriptions(currentDescs);
            }
            currentDescs.putAll(current.getStateDescriptions());
        }

        if (current.getResetVarsOnArrival() != null && !current.getResetVarsOnArrival().isEmpty()) {
            resolved.setResetVarsOnArrival(new ArrayList<>(current.getResetVarsOnArrival()));
        }

        // 合并当前配置的 onEnterRules（子级规则追加到父级之后）
        if (current.getOnEnterRules() != null) {
            Map<String, List<String>> currentOnEnter = current.getOnEnterRules();
            for (Map.Entry<String, List<String>> entry : currentOnEnter.entrySet()) {
                String state = entry.getKey();
                List<String> rules = entry.getValue();
                resolved.getOnEnterRules().compute(state, (k, v) -> {
                    if (v == null) v = new ArrayList<>();
                    v.addAll(rules);
                    return v;
                });
            }
        }

        // ============================================================
        // ★ Phase SM：合并 phases（按 id 覆盖，保留 YAML 声明顺序）
        // ============================================================
        if (sm.getPhases() != null && !sm.getPhases().isEmpty()) {
            Map<String, PhaseDefinition> merged = new LinkedHashMap<>();
            if (resolved.getPhases() != null) {
                for (PhaseDefinition p : resolved.getPhases()) {
                    if (p != null && p.getId() != null) {
                        merged.put(p.getId(), p);
                    }
                }
            }
            for (PhaseDefinition p : sm.getPhases()) {
                if (p != null && p.getId() != null) {
                    merged.put(p.getId(), p);
                }
            }
            resolved.setPhases(new ArrayList<>(merged.values()));
        }

        // ★ Phase SM：initialPhase 子级显式覆盖父级
        if (sm.getInitialPhase() != null && !sm.getInitialPhase().isEmpty()) {
            resolved.setInitialPhase(sm.getInitialPhase());
        }

        // ★ overviewExcludeStates 子级追加到父级
        if (sm.getOverviewExcludeStates() != null && !sm.getOverviewExcludeStates().isEmpty()) {
            Set<String> mergedExclude = resolved.getOverviewExcludeStates() != null
                ? new LinkedHashSet<>(resolved.getOverviewExcludeStates())
                : new LinkedHashSet<>();
            mergedExclude.addAll(sm.getOverviewExcludeStates());
            resolved.setOverviewExcludeStates(mergedExclude);
        }

        // ============================================================
        // ★ Phase SM：构建 stateToPhase 反查表
        // ============================================================
        resolved.rebuildStateToPhaseIndex();

        return resolved;
    }

    private List<String> topologicalSort(Set<String> caseIds) {
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String caseId : caseIds) {
            if (!visited.contains(caseId)) {
                dfsTopological(caseId, caseIds, visited, visiting, sorted);
            }
        }
        return sorted;
    }

    private void dfsTopological(String caseId,
                                Set<String> caseIds,
                                Set<String> visited,
                                Set<String> visiting,
                                List<String> sorted) {
        if (visiting.contains(caseId)) {
            throw new ConfigurationException("检测到循环继承: " + caseId);
        }
        if (visited.contains(caseId)) {
            return;
        }

        visiting.add(caseId);
        String parent = parentMap.get(caseId);
        if (parent != null && caseIds.contains(parent)) {
            dfsTopological(parent, caseIds, visited, visiting, sorted);
        }
        visiting.remove(caseId);

        visited.add(caseId);
        sorted.add(caseId);
    }

    private List<String> buildInheritanceChain(String caseId) {
        List<String> chain = new ArrayList<>();
        String current = caseId;
        while (current != null) {
            chain.add(current);
            current = parentMap.get(current);
        }
        return chain;
    }

    // ====================================================================
    // 原有查询接口
    // ====================================================================

    public ResolvedCaseTypeConfig getConfig(String caseType) {
        return configCache.get(caseType);
    }

    public List<String> getInheritanceChain(String caseType) {
        return inheritanceChainCache.getOrDefault(caseType,
            Collections.singletonList(caseType));
    }

    public Set<String> getLoadedCaseTypes() {
        return configCache.keySet();
    }

    public boolean exists(String caseType) {
        return configCache.containsKey(caseType);
    }

    public Map<String, String> getConfigSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        for (Map.Entry<String, ResolvedCaseTypeConfig> entry : configCache.entrySet()) {
            ResolvedCaseTypeConfig config = entry.getValue();
            summary.put(entry.getKey(),
                String.format("父级: %s, 状态: %d, 转移: %d, 常驻意图: %d",
                    config.getParent() != null ? config.getParent() : "无",
                    config.getStates().size(),
                    config.getTransitions().size(),
                    config.getAlwaysAllowedIntents() != null
                        ? config.getAlwaysAllowedIntents().size() : 0
                ));
        }
        return summary;
    }

    public Map<String, ResolvedCaseTypeConfig> getConfigs() {
        return new HashMap<>(configCache);
    }

    /**
     * 兼容原有方法名，退化为 reloadAll()。
     */
    public synchronized void reload() throws IOException {
        reloadAll();
    }

    /**
     * 保留原有签名；当前实现退化为全量重载。
     */
    public synchronized void reloadCase(String caseType) throws IOException {
        log.info("🔄 重载状态机（案例 {}，退化为全量重载）", caseType);
        reloadAll();
    }

    public List<TransitionRule> getEntryTransitions(String caseType, String state) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null) {
            throw new IllegalArgumentException("案例配置不存在: " + caseType);
        }
        List<TransitionRule> result = new ArrayList<>();
        for (TransitionRule rule : config.getTransitions()) {
            if (state.equals(rule.getTarget())) {
                result.add(rule);
            }
        }
        return result;
    }

    public String generateMermaid(String caseType, boolean isfull) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null) {
            throw new IllegalArgumentException("案例类型不存在: " + caseType);
        }
        return buildMermaid(config, isfull);
    }

    /**
     * ★ Phase SM：生成某个阶段的内部状态机 mermaid。
     *
     * <p>支持两种 phase：</p>
     * <ul>
     *   <li><b>叶子阶段</b>（有 states）：输出内部 state 图（原有逻辑）</li>
     *   <li><b>容器阶段</b>（有 children）：输出子阶段概览图，按 requiresPhases 画边</li>
     * </ul>
     *
     * <p>phaseId 支持递归查找，即前端传子阶段 id 也能命中。</p>
     */
    public String generatePhaseSubgraph(String caseType, String phaseId) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null || !config.hasPhases()) {
            return "";
        }
        PhaseDefinition phase = findPhaseRecursive(config.getPhases(), phaseId);
        if (phase == null) {
            return "";
        }
        if (phase.hasChildren()) {
            return buildContainerSubgraph(phase);
        }
        return buildLeafSubgraph(config, phase);
    }

    /**
     * 清洗 mermaid 边 label：
     *  - 去换行（合并成空格）
     *  - 去 mermaid 保留字符（| / " / <br>）
     *  - 超过 40 字符截断，避免撑爆布局
     */
    private String sanitizeEdgeLabel(String s) {
        if (s == null) return " ";
        String cleaned = s
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("|", "／")
            .replace("\"", "'");
        cleaned = cleaned.trim();
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40) + "…";
        }
        return cleaned.isEmpty() ? " " : cleaned;
    }

    /**
     * 递归查找 phase（含 children）。
     */
    private PhaseDefinition findPhaseRecursive(List<PhaseDefinition> phases, String phaseId) {
        if (phases == null || phaseId == null) {
            return null;
        }
        for (PhaseDefinition p : phases) {
            if (p == null || p.getId() == null) continue;
            if (phaseId.equals(p.getId())) {
                return p;
            }
            if (p.hasChildren()) {
                PhaseDefinition found = findPhaseRecursive(p.getChildren(), phaseId);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 叶子阶段：渲染 states + 内部转移。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>单 state 阶段：只画节点，不画边（自环边无意义，会把布局拉坏）</li>
     *   <li>自环边：跳过（XX --> XX）</li>
     *   <li>边 label：清洗 + 截断（sanitizeEdgeLabel）</li>
     * </ul>
     */
    private String buildLeafSubgraph(ResolvedCaseTypeConfig config, PhaseDefinition phase) {
        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: { 'flowchart': { 'htmlLabels': true } }}%%\n");
        sb.append("flowchart TD\n");
        sb.append("    classDef defaultNode font-weight:bold,font-size:20px,fill:#f9f9f9,stroke:#333,stroke-width:1px,padding:28px 16px,line-height:1.8;\n");
        sb.append("    classDef endState fill:#ffcccc,stroke:#333,stroke-width:2px;\n");

        java.util.function.Function<String, String> toSafeId =
            s -> s.replaceAll("[^a-zA-Z0-9_]", "_");

        Map<String, String> descMap = config.getStateDescriptions();
        Set<String> phaseStates = new LinkedHashSet<>();
        if (phase.getStates() != null) {
            for (String s : phase.getStates()) {
                if (s != null) phaseStates.add(s);
            }
        }

        if (phaseStates.isEmpty()) {
            sb.append("    EMPTY([\"本阶段无子状态\"])\n");
            return sb.toString();
        }

        // 1. 节点
        for (String state : phaseStates) {
            String safeId = toSafeId.apply(state);
            String label = descMap != null ? descMap.getOrDefault(state, state) : state;
            sb.append("    ").append(safeId)
                .append("([\"&nbsp;<b>").append(label).append("</b>&nbsp;\"]):::defaultNode\n");
        }

        // 2. 结束状态样式
        if (config.getEndStates() != null) {
            for (String es : config.getEndStates()) {
                if (phaseStates.contains(es)) {
                    sb.append("    class ").append(toSafeId.apply(es)).append(" endState;\n");
                }
            }
        }

        // ★ 3. 单 state 阶段：自环边无意义，直接返回纯节点图
        if (phaseStates.size() <= 1) {
            return sb.toString();
        }

        // 4. 内部转移（source + target 都在本 phase 内；跳过自环）
        for (TransitionRule rule : config.getTransitions()) {
            if (!phaseStates.contains(rule.getSource())
                || !phaseStates.contains(rule.getTarget())) {
                continue;
            }
            // ★ 跳过自环边
            if (rule.getSource().equals(rule.getTarget())) {
                continue;
            }
            String fromId = toSafeId.apply(rule.getSource());
            String toId = toSafeId.apply(rule.getTarget());
            String event = sanitizeEdgeLabel(rule.getDescription());
            sb.append("    ").append(fromId).append(" -->|")
                .append(event).append("| ").append(toId).append("\n");
        }

        return sb.toString();
    }

    /**
     * 容器阶段：渲染子阶段概览图。
     *
     * <p>节点 = 每个 child（label 附带它自己的状态/子阶段数）；
     * 边 = 按 child.requiresPhases 推导，仅绘制两端都在容器内的依赖。</p>
     */
    private String buildContainerSubgraph(PhaseDefinition container) {
        StringBuilder sb = new StringBuilder();
        sb.append("%%{init: { 'flowchart': { 'htmlLabels': true } }}%%\n");
        sb.append("flowchart TD\n");
        sb.append("    classDef defaultNode font-weight:bold,font-size:20px,fill:#f9f9f9,stroke:#333,stroke-width:1px,padding:28px 16px,line-height:1.8;\n");
        sb.append("    classDef containerNode fill:#e8f0ff,stroke:#333,stroke-width:1px,stroke-dasharray:4 3;\n");

        java.util.function.Function<String, String> toSafeId =
            s -> s.replaceAll("[^a-zA-Z0-9_]", "_");

        List<PhaseDefinition> children = container.getChildren();
        if (children == null || children.isEmpty()) {
            sb.append("    EMPTY([\"本容器无子阶段\"])\n");
            return sb.toString();
        }

        Map<String, String> childIdToSafeId = new LinkedHashMap<>();
        Set<String> childIds = new LinkedHashSet<>();
        for (PhaseDefinition c : children) {
            if (c == null || c.getId() == null) continue;
            childIds.add(c.getId());
            childIdToSafeId.put(c.getId(), toSafeId.apply(c.getId()));
        }

        // 1. 节点
        for (PhaseDefinition c : children) {
            if (c == null || c.getId() == null) continue;
            String safeId = childIdToSafeId.get(c.getId());
            String name = c.getDisplayName() != null ? c.getDisplayName() : c.getId();
            String suffix;
            if (c.hasChildren()) {
                suffix = "\\n(" + c.getChildren().size() + " 个子阶段)";
            } else if (c.getStates() != null && !c.getStates().isEmpty()) {
                suffix = "\\n(含 " + c.getStates().size() + " 个状态)";
            } else {
                suffix = "";
            }
            String nodeClass = c.hasChildren() ? "containerNode" : "defaultNode";
            sb.append("    ").append(safeId)
                .append("([\"&nbsp;<b>").append(escape(name)).append("</b>&nbsp;")
                .append(suffix).append("\"]):::").append(nodeClass).append("\n");
        }

        // 2. 边：按 requiresPhases 画（两端都在 children 内）
        Set<String> emitted = new HashSet<>();
        for (PhaseDefinition c : children) {
            if (c == null || c.getId() == null || c.getRequiresPhases() == null) continue;
            for (String req : c.getRequiresPhases()) {
                if (req == null || !childIds.contains(req)) continue;
                String key = req + "->" + c.getId();
                if (!emitted.add(key)) continue;
                sb.append("    ").append(childIdToSafeId.get(req))
                    .append(" --> ").append(childIdToSafeId.get(c.getId())).append("\n");
            }
        }

        return sb.toString();
    }

    // ============================================================
    // ★ Phase SM：主入口
    //   有 phases → 输出 phase 缩略图（默认）
    //   无 phases → 输出平铺全图（兼容老配置）
    // ============================================================
    private String buildMermaid(ResolvedCaseTypeConfig config, boolean isfull) {
        if (config.hasPhases()) {
            return buildPhaseOverviewMermaid(config);
        }
        return buildFlatMermaid(config);
    }

    private void collectLeafStateIds(PhaseDefinition node, String topId,
                                     Set<String> excludes,
                                     Map<String, String> stateToTop,
                                     int[] counter) {
        if (node.isLeaf()) {
            if (node.getStates() != null) {
                for (String s : node.getStates()) {
                    if (excludes.contains(s)) continue;
                    stateToTop.put(s, topId);
                    counter[0]++;
                }
            }
            return;
        }
        if (node.getChildren() != null) {
            for (PhaseDefinition child : node.getChildren()) {
                collectLeafStateIds(child, topId, excludes, stateToTop, counter);
            }
        }
    }

    private int registerLeafStates(PhaseDefinition leaf, String topId,
                                   Set<String> excludes,
                                   Map<String, String> stateToTop) {
        int n = 0;
        if (leaf.getStates() != null) {
            for (String s : leaf.getStates()) {
                if (excludes.contains(s)) continue;
                stateToTop.put(s, topId);
                n++;
            }
        }
        return n;
    }

    private String resolveTopPhase(String stateOrPhase,
                                   Map<String, String> stateToTop,
                                   Set<String> excludes,
                                   ResolvedCaseTypeConfig config) {
        if (stateOrPhase == null) return null;
        if (excludes.contains(stateOrPhase)) return null;

        // 1. 直接命中 stateToTop（叶子 phase 的 states，或者叶子 phase 的 id 本身）
        String direct = stateToTop.get(stateOrPhase);
        if (direct != null) return direct;

        // 2. 若本身就是顶层 phase id
        for (PhaseDefinition top : config.getPhases()) {
            if (top.getId().equals(stateOrPhase)) return top.getId();
        }

        // 3. 通过 aliases 显式归并（如 F_103_EXIT_TO_PARENT -> CPR）
        for (PhaseDefinition top : config.getPhases()) {
            List<String> aliases = top.getAliases();
            if (aliases != null && aliases.contains(stateOrPhase)) {
                return top.getId();
            }
        }
        return null;
    }

    /**
     * Mermaid 节点 label 转义。
     */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ");
    }

    // ============================================================
    // ★ Phase SM：phase 缩略图
    //   每个顶层 phase 一个节点；跨阶段转移聚合成边
    // ============================================================
    public String buildPhaseOverviewMermaid(ResolvedCaseTypeConfig config) {
        StringBuilder sb = new StringBuilder("graph LR\n");

        Set<String> excludes = config.getOverviewExcludeStates() != null
            ? config.getOverviewExcludeStates() : Collections.emptySet();

        // 1. state -> 顶层 phase ID 映射 + 每个顶层 phase 的叶子 state 计数
        Map<String, String> stateToTop = new LinkedHashMap<>();
        Map<String, Integer> topLeafStateCount = new LinkedHashMap<>();
        Map<String, Integer> topChildCount = new LinkedHashMap<>();

        for (PhaseDefinition top : config.getPhases()) {
            if (top.hasChildren()) {
                int[] counter = {0};
                collectLeafStateIds(top, top.getId(), excludes, stateToTop, counter);
                topLeafStateCount.put(top.getId(), counter[0]);
                topChildCount.put(top.getId(), top.getChildren().size());
            } else {
                int n = registerLeafStates(top, top.getId(), excludes, stateToTop);
                topLeafStateCount.put(top.getId(), n);
                topChildCount.put(top.getId(), 0);
            }
        }

        // 2. 输出节点
        for (PhaseDefinition top : config.getPhases()) {
            String id = top.getId();
            String label = escape(top.getDisplayName());
            int childN = topChildCount.getOrDefault(id, 0);
            int leafN  = topLeafStateCount.getOrDefault(id, 0);
            if (childN > 0) {
                label += "\\n(" + childN + " 个子阶段 / " + leafN + " 个状态)";
            } else {
                label += "\\n(含 " + leafN + " 个状态)";
            }
            sb.append("  ").append(id).append("[\"").append(label).append("\"]\n");
        }

        // 3. 聚合边
        Set<String> emitted = new HashSet<>();
        for (TransitionRule t : config.getTransitions()) {
            String src = resolveTopPhase(t.getSource(), stateToTop, excludes, config);
            String dst = resolveTopPhase(t.getTarget(), stateToTop, excludes, config);
            if (src == null || dst == null) continue;
            if (src.equals(dst)) continue;
            String key = src + "->" + dst;
            if (!emitted.add(key)) continue;

            sb.append("  ").append(src).append(" --> ").append(dst).append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    // 老的平铺全图（无 phases 的案例使用；无 subgraph）
    // ============================================================
    private String buildFlatMermaid(ResolvedCaseTypeConfig config) {
        StringBuilder sb = new StringBuilder();

        sb.append("%%{init: { 'flowchart': { 'htmlLabels': true } }}%%\n");
        sb.append("flowchart TD\n");

        sb.append("    classDef defaultNode font-weight:bold,font-size:22px,fill:#f9f9f9,stroke:#333,stroke-width:1px,padding:28px 16px,line-height:1.8;\n");
        sb.append("    classDef endState fill:#ffcccc,stroke:#333,stroke-width:2px;\n");

        java.util.function.Function<String, String> toSafeId =
            state -> state.replaceAll("[^a-zA-Z0-9_]", "_");

        Map<String, String> descMap = config.getStateDescriptions();

        // 节点
        for (String state : config.getStates()) {
            String safeId = toSafeId.apply(state);
            String label = descMap != null ? descMap.getOrDefault(state, state) : state;
            sb.append("    ").append(safeId)
                .append("([\"&nbsp;<b>").append(label).append("</b>&nbsp;\"]):::defaultNode\n");
        }

        // 结束状态
        if (config.getEndStates() != null) {
            for (String state : config.getEndStates()) {
                String safeId = toSafeId.apply(state);
                sb.append("    class ").append(safeId).append(" endState;\n");
            }
        }

        // 转移边（跳过自环）
        for (TransitionRule rule : config.getTransitions()) {
            String from = rule.getSource();
            String to = rule.getTarget();
            if (from == null || to == null) continue;
            // ★ 自环边跳过
            if (from.equals(to)) continue;
            String event = sanitizeEdgeLabel(rule.getDescription());
            sb.append("    ").append(toSafeId.apply(from)).append(" -->|")
                .append(event).append("| ").append(toSafeId.apply(to)).append("\n");
        }

        return sb.toString();
    }

    public Set<String> getAllStateIds() {
        Set<String> allStates = new HashSet<>();
        for (ResolvedCaseTypeConfig config : configCache.values()) {
            if (config.getStates() != null) {
                allStates.addAll(config.getStates());
            }
        }
        return allStates;
    }

    public Set<String> getStateIdsForCase(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null) {
            return Collections.emptySet();
        }
        return config.getStates() != null
            ? new HashSet<>(config.getStates()) : Collections.emptySet();
    }

    // ====================================================================
    // 契约构建专用查询接口
    // ====================================================================

    public List<TransitionRule> getTransitionsFromState(String caseType, String state) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null) return Collections.emptyList();
        return config.getTransitionsBySource(state);
    }

    public List<String> getOnEnterRules(String caseType, String state) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null) return Collections.emptyList();
        Map<String, List<String>> onEnterRules = config.getOnEnterRules();
        if (onEnterRules == null) return Collections.emptyList();
        List<String> rules = onEnterRules.get(state);
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }

    public Map<String, List<String>> getAllOnEnterRules(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null) return Collections.emptyMap();
        Map<String, List<String>> rules = config.getOnEnterRules();
        return rules != null ? Collections.unmodifiableMap(rules) : Collections.emptyMap();
    }

    public String getInitialState(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        return config != null ? config.getInitialState() : null;
    }

    public Set<String> getEndStates(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null || config.getEndStates() == null) return Collections.emptySet();
        return new LinkedHashSet<>(config.getEndStates());
    }

    public String getParent(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        return config != null ? config.getParent() : null;
    }

    public Set<String> getAlwaysAllowedIntents(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null || config.getAlwaysAllowedIntents() == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(config.getAlwaysAllowedIntents());
    }

    public Set<String> getGlobalEventIntents(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null || config.getGlobalEventIntents() == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(config.getGlobalEventIntents());
    }

    public Map<String, String> getIntentToStateMapping(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null || config.getIntentToStateMapping() == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(config.getIntentToStateMapping());
    }

    public List<String> getResetVarsOnArrival(String caseType) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null || config.getResetVarsOnArrival() == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(config.getResetVarsOnArrival());
    }
}

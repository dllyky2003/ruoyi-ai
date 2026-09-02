package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.StateMachineConfig;
import com.ruoyi.ems.config.StateMachineDefinition;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.exception.ConfigurationException;
import com.ruoyi.ems.context.VariableMetaManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML 状态机配置加载器
 * 核心功能：
 * 1. 扫描 classpath:config/acls/** / *.yaml 加载所有状态机定义
 * 2. 根据 parent 字段构建继承关系，递归合并父级配置
 * 3. 验证所有引用的变量在 VariableMetaManager 中有定义
 * 4. 提供缓存查询和热重载能力
 * 合并规则：
 * - states：父级 + 子级（去重）
 * - endStates：父级 + 子级（去重）
 * - transitions：父级 + 子级（追加，子级可覆盖父级同名规则）
 * - intentToStateMapping：父级 + 子级（子级覆盖父级）
 * - globalEventIntents：父级 + 子级（合并去重）
 * - initialState：子级优先，若无则继承父级
 */
@Component
@Slf4j
public class YamlStateMachineLoader {

    // ==================== 构造器注入（避免字段注入警告） ====================
    private final ResourcePatternResolver resourceResolver;
    private final VariableMetaManager variableMetaManager;

    public YamlStateMachineLoader(ResourcePatternResolver resourceResolver,
                                  VariableMetaManager variableMetaManager) {
        this.resourceResolver = resourceResolver;
        this.variableMetaManager = variableMetaManager;
    }

    // ==================== 缓存 ====================
    private final Map<String, ResolvedCaseTypeConfig> configCache = new ConcurrentHashMap<>();
    private final Map<String, String> parentMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> inheritanceChainCache = new ConcurrentHashMap<>();

    private final Yaml yaml = new Yaml();
    private static final String CONFIG_LOCATION = "classpath:config/acls/**/*.yaml";

    // ==================== 初始化加载 ====================


    public void loadAllConfigs() throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("⏳ 开始加载 YAML 状态机配置...");

        // 1. 扫描并加载原始 YAML
        Resource[] resources = resourceResolver.getResources(CONFIG_LOCATION);
        Map<String, StateMachineConfig> rawConfigs = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.startsWith("_") || filename.startsWith(".")) {
                continue;
            }

            try (InputStream is = resource.getInputStream()) {
                StateMachineConfig config = yaml.loadAs(is, StateMachineConfig.class);
                if (config.getCaseId() == null || config.getCaseId().isEmpty()) {
                    log.warn("⚠️ 文件 {} 缺少 caseId，跳过加载", filename);
                    continue;
                }
                rawConfigs.put(config.getCaseId(), config);
                log.info("📄 加载 YAML: {} (版本: {})", config.getCaseId(), config.getVersion());
            } catch (Exception e) {
                log.error("❌ 加载 YAML 失败: {}", filename, e);
                throw new ConfigurationException("YAML 加载失败: " + filename, e);
            }
        }

        if (rawConfigs.isEmpty()) {
            log.warn("⚠️ 未找到任何 YAML 配置文件，请检查路径: {}", CONFIG_LOCATION);
            return;
        }

        // 2. 构建父级关系映射
        for (StateMachineConfig config : rawConfigs.values()) {
            StateMachineDefinition sm = config.getStateMachine();
            String parent = config.getParent();   // 使用顶层 parent
            if (sm != null) {

                if (parent != null && !parent.isEmpty()) {
                    if (!rawConfigs.containsKey(parent)) {
                        log.warn("⚠️ 案例 {} 的父级 {} 不存在，将视为无继承", config.getCaseId(), parent);
                        continue;
                    }
                    parentMap.put(config.getCaseId(), parent);
                }
            }
        }

        // 3. 拓扑排序（确保父级先于子级处理）
        List<String> sortedCaseIds = topologicalSort(rawConfigs.keySet());

        // 4. 逐层递归合并
        for (String caseId : sortedCaseIds) {
            ResolvedCaseTypeConfig resolved = resolveConfig(caseId, rawConfigs);

            // 5. 变量引用校验
            variableMetaManager.validateStateMachine(caseId, resolved);

            // 6. 缓存
            configCache.put(caseId, resolved);
            inheritanceChainCache.put(caseId, buildInheritanceChain(caseId));

            log.info("✅ 合并完成: {} (父级: {}, 状态数: {}, 转移数: {}, 深度: {})",
                caseId,
                resolved.getParent() != null ? resolved.getParent() : "无",
                resolved.getStates().size(),
                resolved.getTransitions().size(),
                inheritanceChainCache.get(caseId).size() - 1
            );
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("🎯 YAML 配置加载完成，共 {} 个案例，耗时 {}ms", configCache.size(), elapsed);
    }

    // ==================== 核心合并逻辑 ====================

    /**
     * 递归解析配置：合并父级 + 当前层级
     */
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

        // ★ 修改：从 StateMachineConfig 顶层获取 parent
        resolved.setParent(current.getParent());

        StateMachineDefinition sm = current.getStateMachine();
        if (sm == null) {
            throw new ConfigurationException("案例 " + caseId + " 缺少 stateMachine 节点");
        }

        // --- 如果有父级，先继承父级配置 ---
        // ★ 修改：使用 current.getParent() 而非 sm.getParent()
        if (current.getParent() != null && !current.getParent().isEmpty()
            && allConfigs.containsKey(current.getParent())) {

            ResolvedCaseTypeConfig parentResolved = resolveConfig(current.getParent(), allConfigs);

            // 继承状态
            resolved.setStates(new ArrayList<>(parentResolved.getStates()));
            // 继承终止状态
            resolved.setEndStates(new ArrayList<>(parentResolved.getEndStates()));
            // 继承转移规则
            resolved.setTransitions(new ArrayList<>(parentResolved.getTransitions()));
            // 继承意图映射
            resolved.setIntentToStateMapping(new LinkedHashMap<>(parentResolved.getIntentToStateMapping()));
            // 继承全局事件列表
            resolved.setGlobalEventIntents(new LinkedHashSet<>(parentResolved.getGlobalEventIntents()));
            // 继承初始状态（仅作为备选，子类可覆盖）
            resolved.setInitialState(parentResolved.getInitialState());
            // 继承默认转移配置
            resolved.setAllowUnlistedTransitions(parentResolved.isAllowUnlistedTransitions());
            resolved.setDefaultTransitionAction(parentResolved.getDefaultTransitionAction());
            // ★ 新增：继承状态中文描述
            if (parentResolved.getStateDescriptions() != null) {
                resolved.setStateDescriptions(new LinkedHashMap<>(parentResolved.getStateDescriptions()));
            }

        } else {
            // 无父级，初始化空集合
            resolved.setStates(new ArrayList<>());
            resolved.setEndStates(new ArrayList<>());
            resolved.setTransitions(new ArrayList<>());
            resolved.setIntentToStateMapping(new LinkedHashMap<>());
            resolved.setGlobalEventIntents(new LinkedHashSet<>());
            resolved.setAllowUnlistedTransitions(false);
            // ★ 新增：初始化状态描述
            resolved.setStateDescriptions(new LinkedHashMap<>());
        }

        // --- 应用当前配置（子类覆盖父类） ---

        // 1. 状态合并（去重，保留顺序）
        if (sm.getStates() != null && !sm.getStates().isEmpty()) {
            Set<String> stateSet = new LinkedHashSet<>(resolved.getStates());
            stateSet.addAll(sm.getStates());
            resolved.setStates(new ArrayList<>(stateSet));
        }

        // 2. 终止状态合并（去重）
        if (sm.getEndStates() != null && !sm.getEndStates().isEmpty()) {
            Set<String> endSet = new LinkedHashSet<>(resolved.getEndStates());
            endSet.addAll(sm.getEndStates());
            resolved.setEndStates(new ArrayList<>(endSet));
        }

        // 3. 初始状态：子类优先
        if (sm.getInitial() != null && !sm.getInitial().isEmpty()) {
            resolved.setInitialState(sm.getInitial());
        }

        // 4. 转移规则：子类追加
        if (sm.getTransitions() != null && !sm.getTransitions().isEmpty()) {
            resolved.getTransitions().addAll(sm.getTransitions());
        }

        // 5. 意图映射：子类覆盖父类
        if (sm.getIntentToStateMapping() != null && !sm.getIntentToStateMapping().isEmpty()) {
            resolved.getIntentToStateMapping().putAll(sm.getIntentToStateMapping());
        }

        // 6. 全局事件：合并去重
        if (sm.getGlobalEventIntents() != null && !sm.getGlobalEventIntents().isEmpty()) {
            resolved.getGlobalEventIntents().addAll(sm.getGlobalEventIntents());
        }

        // 7. 默认转移配置：子类覆盖
        if (sm.isAllowUnlistedTransitions()) {
            resolved.setAllowUnlistedTransitions(sm.isAllowUnlistedTransitions());
        }
        if (sm.getDefaultTransitionAction() != null && !sm.getDefaultTransitionAction().isEmpty()) {
            resolved.setDefaultTransitionAction(sm.getDefaultTransitionAction());
        }

        // ★ 8. 状态中文描述：子类覆盖父类
        if (current.getStateDescriptions() != null && !current.getStateDescriptions().isEmpty()) {
            // 获取现有的描述（可能来自父级）
            Map<String, String> currentDescs = resolved.getStateDescriptions();
            if (currentDescs == null) {
                currentDescs = new LinkedHashMap<>();
                resolved.setStateDescriptions(currentDescs);
            }
            // 子类覆盖父类
            currentDescs.putAll(current.getStateDescriptions());
        }

        return resolved;
    }
    // ==================== 继承链工具方法 ====================

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

    // ==================== 公开查询接口 ====================

    /**
     * 获取某个案例的完整配置（已合并所有父级）
     */
    public ResolvedCaseTypeConfig getConfig(String caseType) {
        return configCache.get(caseType);
    }

    /**
     * 获取某个案例的继承链（从当前到根）
     */
    public List<String> getInheritanceChain(String caseType) {
        return inheritanceChainCache.getOrDefault(caseType, Collections.singletonList(caseType));
    }

    /**
     * 获取所有已加载的案例类型编码
     */
    @SuppressWarnings("unused")
    public Set<String> getLoadedCaseTypes() {
        return configCache.keySet();
    }

    /**
     * 检查某个案例是否存在
     */
    public boolean exists(String caseType) {
        return configCache.containsKey(caseType);
    }

    /**
     * 获取所有案例的简要摘要信息
     */
    @SuppressWarnings("unused")
    public Map<String, String> getConfigSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        for (Map.Entry<String, ResolvedCaseTypeConfig> entry : configCache.entrySet()) {
            ResolvedCaseTypeConfig config = entry.getValue();
            summary.put(entry.getKey(),
                String.format("父级: %s, 状态: %d, 转移: %d",
                    config.getParent() != null ? config.getParent() : "无",
                    config.getStates().size(),
                    config.getTransitions().size()
                )
            );
        }
        return summary;
    }

    /**
     * 获取所有配置（用于校验）
     */
    @SuppressWarnings("unused")
    public Map<String, ResolvedCaseTypeConfig> getConfigs() {
        return new HashMap<>(configCache);
    }

    // ==================== 热重载 ====================

    /**
     * 热重载所有配置（线程安全）
     */
    public synchronized void reload() throws IOException {
        log.info("🔄 开始热重载 YAML 配置...");
        configCache.clear();
        parentMap.clear();
        inheritanceChainCache.clear();
        loadAllConfigs();
        log.info("✅ 热重载完成");
    }

    /**
     * 热重载单个案例（简单实现：全量重载）
     */
    @SuppressWarnings("unused")
    public synchronized void reloadCase(String caseType) throws IOException {
        // 形参保留供未来优化，当前全量重载
        reload();
    }
    /**
     * 生成 Mermaid 流程图（无高亮）
     */
    public String generateMermaid(String caseType) {
        return generateMermaid(caseType, null);
    }

    /**
     * 生成 Mermaid 流程图，并高亮当前状态
     * @param caseType 案例类型
     * @param currentState 当前状态 ID（可为 null）
     * @return Mermaid 代码
     */
    public String generateMermaid(String caseType, String currentState) {
        ResolvedCaseTypeConfig config = configCache.get(caseType);
        if (config == null) {
            throw new IllegalArgumentException("案例类型不存在: " + caseType);
        }
        return buildMermaid(config, currentState);
    }

    /**
     * 构建 Mermaid 代码（私有方法）
     */
    private String buildMermaid(ResolvedCaseTypeConfig config, String currentState) {
        StringBuilder sb = new StringBuilder();

        sb.append("%%{init: { 'flowchart': { 'rankSpacing': 80, 'nodeSpacing': 20 } }}%%\n");
        sb.append("flowchart TD\n");

        Map<String, String> descMap = config.getStateDescriptions();

        // 1. 生成所有状态节点
        for (String state : config.getStates()) {
            String label = descMap != null && descMap.containsKey(state) ? descMap.get(state) : state;
            sb.append("    ").append(state).append("[\"").append(label).append("\"]");

            // 应用样式类（优先级：currentState > green > endState）
            if (state.equals(currentState)) {
                sb.append(":::currentState");
            }
            else if (config.getEndStates().contains(state)) {
                sb.append(":::endState");
            }
            sb.append("\n");
        }

        // 2. 转移关系
        for (TransitionRule rule : config.getTransitions()) {
            String from = rule.getSource();
            String to = rule.getTarget();
            String event = rule.getDescription();
            if (from != null && to != null) {
                sb.append("    ").append(from).append(" -->|");
                sb.append(event != null ? event : " ");
                sb.append("| ").append(to).append("\n");
            }
        }

        // 3. 样式定义（添加绿色类）
        sb.append("    classDef endState fill:#ffcccc,stroke:#333,stroke-width:2px\n");
        sb.append("    classDef green fill:#90ee90,stroke:#2e7d32,stroke-width:2px\n");  // ← 新增绿色样式
        if (currentState != null) {
            sb.append("    classDef currentState fill:#ff0000,color:#ffffff,stroke:#b71c1c,stroke-width:3px\n");
        }

        return sb.toString();
    }
}

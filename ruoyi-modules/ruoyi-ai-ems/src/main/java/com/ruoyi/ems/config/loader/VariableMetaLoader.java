package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.domain.VariableMeta;
import com.ruoyi.ems.exception.ConfigurationException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VariableMetaLoader {

    // ==================== 核心缓存 ====================
    private final Map<String, VariableMeta> generalMetaMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, VariableMeta>> caseSpecificMetaMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> caseSlotMappings = new ConcurrentHashMap<>();
    private final Map<String, String> generalSlotMappings = new ConcurrentHashMap<>();

    // ★ resetDefaults 缓存
    private final Map<String, Map<String, Object>> caseResetDefaults = new ConcurrentHashMap<>();
    private Map<String, Object> generalResetDefaults = new HashMap<>();

    private final ObjectMapper objectMapper;
    private volatile boolean loaded = false;
    @Autowired
    private ResourcePatternResolver resourceResolver;

    public VariableMetaLoader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // ==================== 初始化加载 ====================

    public void load() throws IOException {

        if (loaded) {
            log.debug("VariableMetaLoader 已加载，跳过重复加载");
            return;
        }

        loadGeneralVariables();
        loadAllSpecificVariables();
        buildIndex();

        log.info("📋 变量系统初始化完成: 通用 {} 个, 专属案例 {} 个, 通用槽位映射 {} 个, 专属槽位映射 {} 个",
            generalMetaMap.size(), caseSpecificMetaMap.size(),
            generalSlotMappings.size(), caseSlotMappings.size());

        loaded = true;
    }

    // ==================== 加载通用变量 ====================

    private void loadGeneralVariables() throws IOException {
        Resource resource = resourceResolver.getResource("classpath:config/acls/GLOBAL/_variables.json");
        log.info("🔍 尝试加载通用变量文件: {}", resource.getURL());
        if (!resource.exists()) {
            log.warn("⚠️ 通用变量配置文件不存在: {}", resource.getURL());
            if (generalResetDefaults == null) {
                generalResetDefaults = new HashMap<>();
            }
            return;
        }
        try (InputStream is = resource.getInputStream()) {
            GeneralVariablesFile file = objectMapper.readValue(is, GeneralVariablesFile.class);
            log.info("✅ 解析成功，variables 数量: {}",
                file.getVariables() != null ? file.getVariables().size() : 0);
            if (file.getVariables() != null) {
                for (VariableMeta meta : file.getVariables()) {
                    meta.setVariableType("CommonVariable");
                    log.debug("  加载变量: {}", meta.getName());
                    generalMetaMap.put(meta.getName(), meta);
                }
            }
            if (file.getSlotMappings() != null && file.getSlotMappings().getMappings() != null) {
                generalSlotMappings.putAll(file.getSlotMappings().getMappings());
                log.debug("  加载通用槽位映射: {}", file.getSlotMappings().getMappings());
            }
            if (file.getResetDefaults() != null && !file.getResetDefaults().isEmpty()) {
                generalResetDefaults = new HashMap<>(file.getResetDefaults());
                log.debug("  加载通用 resetDefaults: {}", generalResetDefaults);
            } else {
                if (generalResetDefaults == null) {
                    generalResetDefaults = new HashMap<>();
                }
            }
        } catch (Exception e) {
            log.error("❌ 解析通用变量文件失败: {}", resource.getURL(), e);
            throw e;
        }
        if (generalResetDefaults == null) {
            generalResetDefaults = new HashMap<>();
        }
    }

    // ==================== 加载专属变量（递归扫描） ====================

    private void loadAllSpecificVariables() throws IOException {
        Resource[] resources = resourceResolver.getResources("classpath:config/acls/cases/**/_variables.json");
        log.info("🔍 找到 {} 个 _variables.json 文件", resources.length);

        for (Resource resource : resources) {
            if (resource.getURL().getPath().contains("/GLOBAL/")) {
                continue;
            }
            loadSpecificVariablesFromFile(resource);
        }
    }

    private void loadSpecificVariablesFromFile(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            SpecificVariablesFile file = objectMapper.readValue(is, SpecificVariablesFile.class);

            String caseType = file.getCaseType();
            if (caseType == null || caseType.isEmpty()) {
                caseType = extractCaseTypeFromPath(resource.getURL().getPath());
            }
            if (caseType == null || caseType.isEmpty()) {
                log.warn("⚠️ 无法确定案例类型，跳过: {}", resource.getURL());
                return;
            }

            log.info("📄 加载文件: {}, caseType={}, resetDefaults 大小: {}, 内容: {}",
                resource.getURL(), caseType,
                file.getResetDefaults() != null ? file.getResetDefaults().size() : 0,
                file.getResetDefaults());

            Map<String, VariableMeta> caseMap = caseSpecificMetaMap.computeIfAbsent(caseType, k -> new ConcurrentHashMap<>());

            if (file.getVariables() != null) {
                for (VariableMeta meta : file.getVariables()) {
                    meta.setVariableType("SpecialVariable");
                    caseMap.put(meta.getName(), meta);
                }
            }

            if (file.getSlotMappings() != null && file.getSlotMappings().getMappings() != null) {
                Map<String, String> caseSlotMap = caseSlotMappings.computeIfAbsent(caseType, k -> new ConcurrentHashMap<>());
                caseSlotMap.putAll(file.getSlotMappings().getMappings());
            }

            Map<String, Object> caseDefaults = caseResetDefaults.computeIfAbsent(caseType, k -> new HashMap<>());

            if (generalResetDefaults == null) {
                generalResetDefaults = new HashMap<>();
            }

            for (Map.Entry<String, Object> entry : generalResetDefaults.entrySet()) {
                caseDefaults.put(entry.getKey(), entry.getValue());
            }

            if (file.getResetDefaults() != null && !file.getResetDefaults().isEmpty()) {
                for (Map.Entry<String, Object> entry : file.getResetDefaults().entrySet()) {
                    if (entry == null || entry.getKey() == null) {
                        log.warn("跳过 resetDefaults 中的无效条目，key 为 null，value: {}",
                            entry != null ? entry.getValue() : "null");
                        continue;
                    } else if (entry.getValue() == null) {
                        log.warn("跳过 resetDefaults 中的无效条目，key 为 {}，value: null",
                            entry.getKey());
                    } else {
                        caseDefaults.put(entry.getKey(), entry.getValue());
                    }
                }
                log.debug("  加载案例 {} 的专属 resetDefaults: {}", caseType, file.getResetDefaults());
            }

            log.info("📌 案例 {} 合并后 resetDefaults 大小: {}", caseType, caseDefaults.size());

        } catch (Exception e) {
            log.error("❌ 解析变量文件失败: {}", resource.getURL(), e);
            throw new IOException("解析变量文件失败: " + resource.getURL(), e);
        }
    }

    private String extractCaseTypeFromPath(String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].equals("_variables.json") && i > 0) {
                return parts[i - 1];
            }
        }
        return null;
    }

    // ==================== 构建索引 ====================

    private void buildIndex() {
        for (String caseType : caseSpecificMetaMap.keySet()) {
            mergeGeneralToCase(caseType);
        }
        validateAllResetDefaults();
    }

    private void mergeGeneralToCase(String caseType) {
        Map<String, VariableMeta> specificMap = caseSpecificMetaMap.computeIfAbsent(caseType, k -> new ConcurrentHashMap<>());
        for (Map.Entry<String, VariableMeta> entry : generalMetaMap.entrySet()) {
            specificMap.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private void validateAllResetDefaults() {
        for (String caseType : caseResetDefaults.keySet()) {
            validateResetDefaults(caseType);
        }
    }

    private void validateResetDefaults(String caseType) {
        Map<String, Object> defaults = caseResetDefaults.get(caseType);
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        Map<String, VariableMeta> allVars = getVariablesForCase(caseType);
        for (String varName : defaults.keySet()) {
            if (!allVars.containsKey(varName)) {
                log.warn("⚠️ resetDefaults 中的变量 '{}' 未在 variables 中定义 (案例: {})",
                    varName, caseType);
            }
        }
    }

    // ==================== 公开查询接口 ====================

    public Map<String, VariableMeta> getVariablesForCase(String caseType) {
        Map<String, VariableMeta> specific = caseSpecificMetaMap.get(caseType);
        return specific != null ? specific : generalMetaMap;
    }

    public VariableMeta getMeta(String caseType, String varName) {
        if (varName == null) {
            return null;
        }
        if (caseType != null) {
            Map<String, VariableMeta> specific = caseSpecificMetaMap.get(caseType);
            if (specific != null && specific.containsKey(varName)) {
                return specific.get(varName);
            }
        }
        return generalMetaMap.get(varName);
    }

    public String resolveSlotToVariable(String caseType, String slotName) {
        Map<String, String> caseSlotMap = caseSlotMappings.get(caseType);
        if (caseSlotMap != null && caseSlotMap.containsKey(slotName)) {
            return caseSlotMap.get(slotName);
        }
        return generalSlotMappings.getOrDefault(slotName, slotName);
    }

    @Deprecated
    public String resolveSlotToVariable(String slotName) {
        return generalSlotMappings.getOrDefault(slotName, slotName);
    }

    public Map<String, String> getGeneralSlotMappings() {
        return new HashMap<>(generalSlotMappings);
    }

    public Map<String, String> getSpecificSlotMappings(String caseType) {
        Map<String, String> caseSlotMap = caseSlotMappings.get(caseType);
        return caseSlotMap != null ? new HashMap<>(caseSlotMap) : Collections.emptyMap();
    }

    // ==================== resetDefaults 查询接口 ====================

    public Map<String, Object> getResetDefaults(String caseType) {
        Map<String, Object> result = new HashMap<>();
        if (generalResetDefaults != null && !generalResetDefaults.isEmpty()) {
            result.putAll(generalResetDefaults);
        }
        Map<String, Object> caseDefaults = caseResetDefaults.get(caseType);
        if (caseDefaults != null && !caseDefaults.isEmpty()) {
            result.putAll(caseDefaults);
        }
        log.info("📦 getResetDefaults({}) 返回大小: {}, 内容: {}", caseType, result.size(), result);
        return result;
    }

    public Object getResetDefault(String caseType, String varName) {
        Map<String, Object> caseDefaults = caseResetDefaults.get(caseType);
        if (caseDefaults != null && caseDefaults.containsKey(varName)) {
            return caseDefaults.get(varName);
        }
        return generalResetDefaults != null ? generalResetDefaults.get(varName) : null;
    }

    public boolean hasResetDefaults(String caseType) {
        Map<String, Object> caseDefaults = caseResetDefaults.get(caseType);
        return (caseDefaults != null && !caseDefaults.isEmpty()) ||
            (generalResetDefaults != null && !generalResetDefaults.isEmpty());
    }

    // ==================== 权限与校验 ====================
    /**
     * ★ 方案 C：嵌套路径前缀回退。
     *
     * <p>从 varName 逐级去尾部 ".xxx"，直到找到已声明的父变量 meta。
     * 例：intervention.vagal.count → intervention.vagal（命中）
     *     intervention.vagal.count.x → intervention.vagal（命中，跨多级）</p>
     *
     * @return 命中的父变量 meta；都没有则返回 null
     */
    private VariableMeta resolveByParentPrefix(String caseType, String varName) {
        if (varName == null || !varName.contains(".")) {
            return null;
        }
        String parent = varName;
        while (parent.contains(".")) {
            int lastDot = parent.lastIndexOf('.');
            parent = parent.substring(0, lastDot);
            VariableMeta meta = getMeta(caseType, parent);
            if (meta != null) {
                return meta;
            }
        }
        return null;
    }
    public boolean checkWritePermission(String caseType, String varName, String writer) {
        if ("SYSTEM".equals(writer) || "STATE_MACHINE".equals(writer)) {
            return true;
        }
        if (varName == null) {
            return true;
        }
        VariableMeta meta = getMeta(caseType, varName);
        if (meta == null) {
            // ★ 方案 C：嵌套路径前缀回退，intervention.vagal.count → intervention.vagal
            meta = resolveByParentPrefix(caseType, varName);
        }
        if (meta == null) {
            log.warn("变量 {} 未定义，允许写入", varName);
            return true;
        }
        String writableBy = meta.getWritableBy();
        if (writableBy == null || "ALL".equals(writableBy)) {
            return true;
        }
        Set<String> allowed = new HashSet<>(Arrays.asList(writableBy.split(",")));
        allowed = allowed.stream().map(String::trim).collect(Collectors.toSet());
        return allowed.contains(writer);
    }

    public boolean validateValue(String caseType, String varName, Object value) {
        if (varName == null) {
            return true;
        }
        if (value instanceof java.util.Map) {
            return true;
        }
        VariableMeta meta = getMeta(caseType, varName);
        if (meta == null) {
            // ★ 方案 C：嵌套路径不做类型校验。
            // 父 meta 的 type=object，但写入的是叶子值（int/string），类型不匹配会误判为非法。
            // 叶子字段的值校验（如 status 枚举白名单）留给父 Map 的 requiredKeys/enumOptions
            // 在业务层做，或后续单独加嵌套校验器。
            return true;
        }
        if (value == null) {
            return true;
        }

        String type = meta.getType();
        if (type == null) {
            return true;
        }

        try {
            switch (type.toLowerCase()) {
                case "integer":
                    if (!(value instanceof Number)) {
                        if (value instanceof String) {
                            try {
                                Integer.parseInt((String) value);
                                return true;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        }
                        return false;
                    }
                    int intVal = ((Number) value).intValue();
                    if (meta.getMin() != null && intVal < meta.getMin()) {
                        return false;
                    }
                    return meta.getMax() == null || intVal <= meta.getMax();

                case "string":
                    if (!(value instanceof String)) {
                        return false;
                    }
                    if (meta.getEnumOptions() != null && !meta.getEnumOptions().isEmpty()) {
                        return meta.getEnumOptions().stream()
                            .anyMatch(opt -> opt.getValue().equals(value));
                    }
                    return true;

                case "boolean":
                    return value instanceof Boolean
                        || "true".equalsIgnoreCase(String.valueOf(value))
                        || "false".equalsIgnoreCase(String.valueOf(value));

                case "object":
                    if (!(value instanceof Map)) {
                        return false;
                    }
                    Map<?, ?> map = (Map<?, ?>) value;

                    // 1. requiredKeys 校验
                    if (meta.getRequiredKeys() != null) {
                        for (String k : meta.getRequiredKeys()) {
                            if (!map.containsKey(k)) {
                                log.debug("变量 {} 缺少必需键: {}", varName, k);
                                return false;
                            }
                        }
                    }

                    // 2. status 枚举白名单（若存在）
                    Object status = map.get("status");
                    if (status != null) {
                        Set<String> allowed = Set.of(
                            "given", "performed", "failed", "cancelled", "initiated", "attempted"
                        );
                        if (!allowed.contains(status.toString())) {
                            log.debug("变量 {} 的 status 值非法: {}", varName, status);
                            return false;
                        }
                    }
                    return true;

                default:
                    return true;
            }
        } catch (Exception e) {
            log.warn("变量 {} 校验异常", varName, e);
            return false;
        }
    }
    public void validateStateMachine(String caseType, ResolvedCaseTypeConfig config) {
        Set<String> referencedVars = new HashSet<>();
        for (TransitionRule rule : config.getTransitions()) {
            if (rule.getCondition() != null) {
                referencedVars.addAll(extractVariables(rule.getCondition()));
            }
            if (rule.getAction() != null) {
                referencedVars.addAll(extractVariables(rule.getAction()));
            }
            // 新增：校验 exitReason 和 effects
            if (rule.getExitReason() != null) {
                // exitReason 是字面量，无需提取变量
            }
            if (rule.getEffects() != null) {
                for (TransitionRule.EffectRule effect : rule.getEffects()) {
                    if (effect != null && effect.getValueExpression() != null) {
                        referencedVars.addAll(extractVariables(effect.getValueExpression()));
                    }
                }
            }
        }

        Map<String, VariableMeta> allVars = getVariablesForCase(caseType);
        Set<String> builtInFields = new HashSet<>(Arrays.asList(
            "utils", "ctx", "elapsedSeconds", "clockStatus"));

        for (String varName : referencedVars) {
            if (builtInFields.contains(varName)) continue;
            if (allVars.containsKey(varName)) continue;

            // ★ 前缀回退：支持访问 Map 内字段
            // 例：intervention.cardioversion.count → intervention.cardioversion
            //     intervention.vagal.effect         → intervention.vagal
            String parentVar = varName;
            boolean matched = false;
            while (parentVar.contains(".")) {
                int lastDot = parentVar.lastIndexOf('.');
                parentVar = parentVar.substring(0, lastDot);
                if (allVars.containsKey(parentVar)) {
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            // 兼容带 caseType 前缀的变量
            String prefixedName = caseType + "_" + varName;
            if (allVars.containsKey(prefixedName)) {
                continue;
            }

            throw new ConfigurationException(
                String.format("变量 '%s' 未定义 (案例: %s)，请检查变量名是否正确或是否已加前缀",
                    varName, caseType)
            );
        }
    }

    /**
     * ★ 从表达式中提取变量引用。
     *
     * 由 private 改为 public，供契约构建器交叉验证使用。
     * 注意：契约构建器的首选解析器是 GuardExpressionParser（能区分变量与 Bean 方法调用），
     * 这里提供的方法主要用于诊断对比。
     */
    public Set<String> extractVariables(String expression) {
        Set<String> vars = new HashSet<>();
        if (expression == null) return vars;
        Pattern pattern = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_.]+)");
        Matcher matcher = pattern.matcher(expression);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!"utils".equals(name) && !"ctx".equals(name)
                && !name.startsWith("utils.") && !name.startsWith("ctx.")) {
                vars.add(name);
            }
        }
        return vars;
    }

    // ==================== 热加载支持 ====================

    public synchronized void reload() throws IOException {
        log.info("🔄 重载所有变量配置...");
        generalMetaMap.clear();
        caseSpecificMetaMap.clear();
        generalSlotMappings.clear();
        caseSlotMappings.clear();
        generalResetDefaults = new HashMap<>();
        caseResetDefaults.clear();
        loaded = false;   // ★ 新增：重置幂等标志
        load();
        log.info("✅ 变量配置重载完成");
    }

    public synchronized void reloadCase(String caseType) throws IOException {
        log.info("🔄 重载案例变量: {}", caseType);
        caseSpecificMetaMap.remove(caseType);
        caseSlotMappings.remove(caseType);
        caseResetDefaults.remove(caseType);

        Resource[] resources = resourceResolver.getResources(
            "classpath:config/acls/cases/**/" + caseType + "/_variables.json");
        boolean loaded = false;
        for (Resource resource : resources) {
            if (resource.exists() && !resource.getURL().getPath().contains("/GLOBAL/")) {
                loadSpecificVariablesFromFile(resource);
                loaded = true;
                break;
            }
        }
        if (!loaded) {
            log.warn("案例 {} 没有专属变量文件，仅使用通用变量", caseType);
        }
        mergeGeneralToCase(caseType);
        if (!caseResetDefaults.containsKey(caseType)
            && generalResetDefaults != null
            && !generalResetDefaults.isEmpty()) {
            Map<String, Object> caseDefaults = new HashMap<>();
            caseDefaults.putAll(generalResetDefaults);
            caseResetDefaults.put(caseType, caseDefaults);
        }
        log.info("✅ 案例 {} 变量重载完成", caseType);
    }

    // ====================================================================
    // ★ 新增：契约构建专用查询接口
    // ====================================================================

    /**
     * 列出所有已加载专属变量的案例 ID（不含 GLOBAL）。
     * 注意：这个集合可能小于状态机里的所有案例，因为某些案例可能没有专属变量文件。
     */
    public Set<String> getAllCaseTypesWithSpecificVariables() {
        return Collections.unmodifiableSet(caseSpecificMetaMap.keySet());
    }

    /**
     * 取通用变量元数据（只读视图）。
     */
    public Map<String, VariableMeta> getGeneralVariables() {
        return Collections.unmodifiableMap(generalMetaMap);
    }

    /**
     * 查询某个变量的可写方集合。
     *
     * writableBy 字段可能是：
     *   - null / "ALL"  -> 所有 writer 都可写
     *   - "STATE_MACHINE"
     *   - "CLASSIFIER"
     *   - "EXECUTOR"
     *   - "SYSTEM"
     *   - 逗号分隔的多值，如 "SYSTEM,STATE_MACHINE"
     *
     * @return 可写方集合；若为 null/ALL 返回空集（表示不限）
     */
    public Set<String> getVariableWriters(String caseType, String varName) {
        VariableMeta meta = getMeta(caseType, varName);
        if (meta == null) return Collections.emptySet();
        String writableBy = meta.getWritableBy();
        if (writableBy == null || "ALL".equalsIgnoreCase(writableBy)) {
            return Collections.emptySet();
        }
        return Arrays.stream(writableBy.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * 反查：某个案例下，某个 writer 类型能写哪些变量。
     * 用于契约诊断：某个意图由 EXECUTOR 执行，但它声明要写一个 writableBy=CLASSIFIER 的变量。
     */
    public Set<String> getVariablesByWriter(String caseType, String writer) {
        Set<String> result = new LinkedHashSet<>();
        Map<String, VariableMeta> vars = getVariablesForCase(caseType);
        for (Map.Entry<String, VariableMeta> e : vars.entrySet()) {
            String writableBy = e.getValue().getWritableBy();
            if (writableBy == null || "ALL".equalsIgnoreCase(writableBy)) {
                result.add(e.getKey());
            } else {
                Set<String> allowed = Arrays.stream(writableBy.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
                if (allowed.contains(writer)) {
                    result.add(e.getKey());
                }
            }
        }
        return result;
    }

    /**
     * 契约侧校验：检查"契约里声明的变量写入方"是否与"变量元数据允许的写入方"一致。
     *
     * @param caseType         案例
     * @param intentToWrites   契约里的映射：意图 -> 它写入的变量集合
     * @param intentToWriterType 契约里的映射：意图 -> writer 类型（如 EXECUTOR / CLASSIFIER / STATE_MACHINE）
     * @return 违规描述列表（空表示无违规）
     */
    public List<String> validateVariableWriters(String caseType,
                                                Map<String, Set<String>> intentToWrites,
                                                Map<String, String> intentToWriterType) {
        List<String> violations = new ArrayList<>();
        if (intentToWrites == null) return violations;

        Map<String, VariableMeta> vars = getVariablesForCase(caseType);
        for (Map.Entry<String, Set<String>> e : intentToWrites.entrySet()) {
            String intentCode = e.getKey();
            Set<String> writtenVars = e.getValue();
            if (writtenVars == null || writtenVars.isEmpty()) continue;

            String writerType = intentToWriterType != null
                ? intentToWriterType.get(intentCode)
                : null;
            if (writerType == null) continue;

            for (String varName : writtenVars) {
                VariableMeta meta = vars.get(varName);
                if (meta == null) continue;

                String writableBy = meta.getWritableBy();
                if (writableBy == null || "ALL".equalsIgnoreCase(writableBy)) continue;

                Set<String> allowed = Arrays.stream(writableBy.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
                if (!allowed.contains(writerType)) {
                    violations.add(String.format(
                        "意图 %s（writer=%s）声明写入变量 %s，但变量元数据 writableBy=%s",
                        intentCode, writerType, varName, writableBy));
                }
            }
        }
        return violations;
    }

    // ==================== 内部数据类 ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeneralVariablesFile {
        private String version;
        private String description;
        private List<VariableMeta> variables;
        @JsonProperty("slot_mappings")
        private SlotMapping slotMappings;
        @JsonProperty("resetDefaults")
        private Map<String, Object> resetDefaults;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SpecificVariablesFile {
        private String caseType;
        private String description;
        private List<VariableMeta> variables;
        @JsonProperty("slot_mappings")
        private SlotMapping slotMappings;
        @JsonProperty("resetDefaults")
        private Map<String, Object> resetDefaults;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SlotMapping {
        private String description;
        private Map<String, String> mappings;
    }
}

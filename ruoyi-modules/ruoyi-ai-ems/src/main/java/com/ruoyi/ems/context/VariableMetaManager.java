package com.ruoyi.ems.context;

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


    @Component
    @Slf4j
    public class VariableMetaManager {

        // ==================== 核心缓存 ====================
        // 通用变量缓存（变量名 → VariableMeta）
        private final Map<String, VariableMeta> generalMetaMap = new ConcurrentHashMap<>();

        // 专属变量缓存（案例ID → (变量名 → VariableMeta)）
        private final Map<String, Map<String, VariableMeta>> caseSpecificMetaMap = new ConcurrentHashMap<>();

        // 槽位映射（槽位名 → 变量名），按案例隔离
        private final Map<String, Map<String, String>> caseSlotMappings = new ConcurrentHashMap<>();
        // 全局通用槽位映射（来自于 GLOBAL/_variables.json）
        private final Map<String, String> generalSlotMappings = new ConcurrentHashMap<>();

        private final ObjectMapper objectMapper;

        @Autowired
        private ResourcePatternResolver resourceResolver;

        public VariableMetaManager() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        // ==================== 初始化加载 ====================

        public void load() throws IOException {
            loadGeneralVariables();
            loadAllSpecificVariables();
            buildIndex();
            log.info("📋 变量系统初始化完成: 通用 {} 个, 专属案例 {} 个, 通用槽位映射 {} 个, 专属槽位映射 {} 个",
                generalMetaMap.size(), caseSpecificMetaMap.size(),
                generalSlotMappings.size(), caseSlotMappings.size());
            for (String caseType : caseSpecificMetaMap.keySet()) {
                Map<String, VariableMeta> vars = caseSpecificMetaMap.get(caseType);
                log.info("  案例 {} 变量数: {}，包含 operation_status: {}",
                    caseType, vars.size(), vars.containsKey("operation_status"));
            }
        }

        // ==================== 加载通用变量 ====================

        private void loadGeneralVariables() throws IOException {
            Resource resource = resourceResolver.getResource("classpath:config/acls/GLOBAL/_variables.json");
            if (!resource.exists()) {
                log.warn("⚠️ 通用变量配置文件不存在: classpath:config/acls/GLOBAL/_variables.json");
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                GeneralVariablesFile file = objectMapper.readValue(is, GeneralVariablesFile.class);
                if (file.getVariables() != null) {
                    for (VariableMeta meta : file.getVariables()) {
                        generalMetaMap.put(meta.getName(), meta);
                    }
                }
                if (file.getSlotMappings() != null && file.getSlotMappings().getMappings() != null) {
                    generalSlotMappings.putAll(file.getSlotMappings().getMappings());
                }
            }
        }

        // ==================== 加载专属变量（递归扫描） ====================

        private void loadAllSpecificVariables() throws IOException {
            Resource[] resources = resourceResolver.getResources("classpath:config/acls/cases/**/_variables.json");
            log.info("🔍 找到 {} 个 _variables.json 文件", resources.length);

            for (Resource resource : resources) {
                // 跳过 GLOBAL 目录（安全起见）
                if (resource.getURL().getPath().contains("/GLOBAL/")) {
                    continue;
                }
                loadSpecificVariablesFromFile(resource);
            }
        }

        /**
         * 从单个文件加载专属变量，并更新 caseSpecificMetaMap 和 caseSlotMappings
         * 变量名自动加上案例前缀，实现不同案例间的隔离
         */
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

                Map<String, VariableMeta> caseMap = caseSpecificMetaMap.computeIfAbsent(caseType, k -> new ConcurrentHashMap<>());

                // 加载变量
                if (file.getVariables() != null) {
                    for (VariableMeta meta : file.getVariables()) {
                        String originalName = meta.getName();
                        // ★ 关键：变量名加前缀
                        String prefixedName = caseType + "_" + originalName;

                        // 检测同一案例内重复定义
                        if (caseMap.containsKey(prefixedName)) {
                            log.warn("案例 {} 中变量 '{}' 重复定义，将被覆盖", caseType, originalName);
                        }

                        meta.setName(prefixedName);
                        caseMap.put(prefixedName, meta);
                    }
                }

                // 加载槽位映射（按案例隔离）
                if (file.getSlotMappings() != null && file.getSlotMappings().getMappings() != null) {
                    Map<String, String> caseSlotMap = caseSlotMappings.computeIfAbsent(caseType, k -> new ConcurrentHashMap<>());
                    Map<String, String> originalMappings = file.getSlotMappings().getMappings();
                    for (Map.Entry<String, String> entry : originalMappings.entrySet()) {
                        String slot = entry.getKey();
                        String varName = entry.getValue();
                        // 如果变量名没有加前缀，则自动加前缀
                        if (!varName.startsWith(caseType + "_")) {
                            varName = caseType + "_" + varName;
                        }
                        caseSlotMap.put(slot, varName);
                    }
                }
            } catch (Exception e) {
                log.error("❌ 解析变量文件失败: {}", resource.getURL(), e);
                throw new IOException("解析变量文件失败: " + resource.getURL(), e);
            }
        }

        /**
         * 从路径中提取案例类型（取 _variables.json 的父目录名）
         * 例如：.../cases/tachycardia/stable_narrow/_variables.json → "stable_narrow"
         */
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
        }

        /**
         * 将通用变量合并到指定案例的专属变量集合中（不覆盖专属已有）
         */
        private void mergeGeneralToCase(String caseType) {
            Map<String, VariableMeta> specificMap = caseSpecificMetaMap.computeIfAbsent(caseType, k -> new ConcurrentHashMap<>());
            for (Map.Entry<String, VariableMeta> entry : generalMetaMap.entrySet()) {
                specificMap.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        // ==================== 公开查询接口 ====================

        /**
         * 获取某案例的完整变量集合（专属 + 通用）
         */
        public Map<String, VariableMeta> getVariablesForCase(String caseType) {
            Map<String, VariableMeta> specific = caseSpecificMetaMap.get(caseType);
            if (specific == null) {
                return generalMetaMap;
            }
            return specific;
        }

        /**
         * 获取某个变量的元数据（优先专属，其次通用）
         */
        public VariableMeta getMeta(String caseType, String varName) {
            Map<String, VariableMeta> specific = caseSpecificMetaMap.get(caseType);
            if (specific != null && specific.containsKey(varName)) {
                return specific.get(varName);
            }
            return generalMetaMap.get(varName);
        }

        /**
         * 解析槽位到变量名（优先案例专属，其次通用）
         */
        public String resolveSlotToVariable(String caseType, String slotName) {
            Map<String, String> caseSlotMap = caseSlotMappings.get(caseType);
            if (caseSlotMap != null && caseSlotMap.containsKey(slotName)) {
                return caseSlotMap.get(slotName);
            }
            return generalSlotMappings.getOrDefault(slotName, slotName);
        }

        /**
         * 兼容旧接口（无 caseType 参数，使用通用映射）
         * @deprecated 请使用 {@link #resolveSlotToVariable(String, String)}
         */
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

        // ==================== 权限与校验 ====================

        public boolean checkWritePermission(String caseType, String varName, String writer) {
            VariableMeta meta = getMeta(caseType, varName);
            if (meta == null) {
                log.warn("变量 {} 未定义，允许写入", varName);
                return true;
            }
            String writableBy = meta.getWritableBy();
            if (writableBy == null || "ALL".equals(writableBy)) {
                return true;
            }
            Set<String> allowed = new HashSet<>(Arrays.asList(writableBy.split(",")));
            allowed = allowed.stream().map(String::trim).collect(java.util.stream.Collectors.toSet());
            return allowed.contains(writer);
        }

        /**
         * 校验变量值是否符合元数据约束
         */
        public boolean validateValue(String caseType, String varName, Object value) {
            VariableMeta meta = getMeta(caseType, varName);
            if (meta == null || value == null) return true;
            String type = meta.getType();
            if (type == null) return true;

            try {
                switch (type.toLowerCase()) {
                    case "integer":
                        if (!(value instanceof Number)) {
                            if (value instanceof String) {
                                try { Integer.parseInt((String) value); return true; }
                                catch (NumberFormatException e) { return false; }
                            }
                            return false;
                        }
                        int intVal = ((Number) value).intValue();
                        if (meta.getMin() != null && intVal < meta.getMin()) return false;
                        return meta.getMax() == null || intVal <= meta.getMax();
                    case "string":
                        if (!(value instanceof String)) return false;
                        if (meta.getEnumOptions() != null && !meta.getEnumOptions().isEmpty()) {
                            return meta.getEnumOptions().stream()
                                .anyMatch(opt -> opt.getValue().equals(value));
                        }
                        return true;
                    case "boolean":
                        return value instanceof Boolean ||
                            "true".equalsIgnoreCase(String.valueOf(value)) ||
                            "false".equalsIgnoreCase(String.valueOf(value));
                    default:
                        return true;
                }
            } catch (Exception e) {
                log.warn("变量 {} 校验异常", varName, e);
                return false;
            }
        }

        /**
         * 校验状态机中引用的变量是否存在
         * 支持短名匹配：YAML 中可写短名（如 #operation_status），系统会自动匹配带前缀的变量（如 stable_narrow_operation_status）
         */
        public void validateStateMachine(String caseType, ResolvedCaseTypeConfig config) {
            Set<String> referencedVars = new HashSet<>();
            for (TransitionRule rule : config.getTransitions()) {
                if (rule.getCondition() != null) {
                    referencedVars.addAll(extractVariables(rule.getCondition()));
                }
                if (rule.getAction() != null) {
                    referencedVars.addAll(extractVariables(rule.getAction()));
                }
            }

            Map<String, VariableMeta> allVars = getVariablesForCase(caseType);
            Set<String> builtInFields = new HashSet<>(Arrays.asList("utils", "ctx", "elapsedSeconds", "clockStatus"));

            for (String varName : referencedVars) {
                if (builtInFields.contains(varName)) continue;

                // ★ 关键：先检查原名，再检查带前缀的名称（实现短名自动匹配）
                if (!allVars.containsKey(varName)) {
                    String prefixedName = caseType + "_" + varName;
                    if (allVars.containsKey(prefixedName)) {
                        // YAML 中写的是短名，但实际存储有前缀 → 通过校验（允许）
                        continue;
                    }
                    throw new ConfigurationException(
                        String.format("变量 '%s' 未定义 (案例: %s)，请检查变量名是否正确或是否已加前缀",
                            varName, caseType)
                    );
                }
            }
        }

        private Set<String> extractVariables(String expression) {
            Set<String> vars = new HashSet<>();
            if (expression == null) return vars;
            Pattern pattern = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*)");
            Matcher matcher = pattern.matcher(expression);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (!"utils".equals(name) && !"ctx".equals(name)) {
                    vars.add(name);
                }
            }
            return vars;
        }

        // ==================== 热加载支持 ====================

        /**
         * 全量重载变量配置（清空并重新加载所有变量）
         */
        public synchronized void reload() throws IOException {
            log.info("🔄 重载所有变量配置...");
            generalMetaMap.clear();
            caseSpecificMetaMap.clear();
            generalSlotMappings.clear();
            caseSlotMappings.clear();
            load();
            log.info("✅ 变量配置重载完成");
        }

        /**
         * 重载指定案例的变量配置（保留通用变量）
         */
        public synchronized void reloadCase(String caseType) throws IOException {
            log.info("🔄 重载案例变量: {}", caseType);
            // 移除该案例的专属变量和槽位映射
            caseSpecificMetaMap.remove(caseType);
            caseSlotMappings.remove(caseType);

            // 重新加载该案例的 _variables.json
            Resource[] resources = resourceResolver.getResources("classpath:config/acls/cases/**/" + caseType + "/_variables.json");
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
            // 合并通用变量到该案例（确保通用变量始终存在）
            mergeGeneralToCase(caseType);
            log.info("✅ 案例 {} 变量重载完成", caseType);
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
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class SpecificVariablesFile {
            private String caseType;
            private String description;
            private List<VariableMeta> variables;
            @JsonProperty("slot_mappings")
            private SlotMapping slotMappings;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class SlotMapping {
            private String description;
            private Map<String, String> mappings;
        }
    }

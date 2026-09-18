package com.ruoyi.acls.skill.registery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.impl.GenericActionSkill;
import com.ruoyi.acls.skill.impl.GenericAssertSkill;
import com.ruoyi.acls.skill.impl.QuerySkill;
import com.ruoyi.acls.skill.impl.StandardActionSkill;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class YamlSkillLoader {

    private final SkillRegistry registry;
    private final SpELExpressionEvaluator spelEvaluator;

    // ====================================================================
    // ★ 新增：技能 IO 元数据索引（供契约构建使用）
    // ====================================================================

    /**
     * 技能的 IO 元数据：从 YAML 原始配置里提取的写入/读取变量。
     * 契约构建器通过 getSkillMeta(skillId) 反查。
     */
    @Getter
    public static class SkillMeta {
        /** 技能 ID */
        private final String skillId;
        /** 技能类型：query / standard / generic / generic_assert / complex */
        private final String type;
        /** 职责枚举字符串 */
        private final String duty;
        /** 来源文件名（用于诊断） */
        private final String sourceFile;
        /** 该技能写入的变量 */
        private final Set<String> writes;
        /** 该技能读取的变量（前置条件 + sourceVar/sourceVars + query targetVar） */
        private final Set<String> reads;

        public SkillMeta(String skillId, String type, String duty, String sourceFile,
                         Set<String> writes, Set<String> reads) {
            this.skillId = skillId;
            this.type = type;
            this.duty = duty;
            this.sourceFile = sourceFile;
            this.writes = writes != null ? Collections.unmodifiableSet(writes) : Set.of();
            this.reads = reads != null ? Collections.unmodifiableSet(reads) : Set.of();
        }
    }

    /** skillId -> SkillMeta */
    private final Map<String, SkillMeta> skillMetaMap = new ConcurrentHashMap<>();

    /** 是否已加载过 */
    private volatile boolean loaded = false;

    // ====================================================================
    // 加载入口
    // ====================================================================


    public void loadSkills() {
        if (loaded) {
            log.debug("YamlSkillLoader 已加载，跳过重复加载");
            return;
        }
        doLoadSkills();
        loaded = true;
    }

    /**
     * 全量重新加载：清空已有索引，重新扫描。
     * 供 CaseConfigLoader.reloadAll() 调用。
     *
     * 注意：技能不按案例区分，所以没有 reloadCase(caseId)。
     */
    public synchronized void reloadAll() {
        log.info("🔄 重新加载技能配置（全量）...");
        skillMetaMap.clear();
        loaded = false;
        doLoadSkills();
        loaded = true;
    }

    // ====================================================================
    // 内部加载逻辑
    // ====================================================================

    private void doLoadSkills() {
        try {
            PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();
            Resource[] resources =
                resolver.getResources("classpath:config/skills/**/*.yaml");

            if (resources.length == 0) {
                log.warn("⚠️ 未找到任何技能配置文件，请检查 classpath:config/skills/**/*.yaml");
                return;
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            int totalLoaded = 0;

            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> root = mapper.readValue(is, Map.class);
                    List<Map<String, Object>> skills =
                        (List<Map<String, Object>>) root.get("skills");

                    if (skills == null || skills.isEmpty()) {
                        continue;
                    }

                    for (Map<String, Object> config : skills) {
                        // ★ 1. 先构建 SkillMeta（不管 createSkillFromConfig 是否成功）
                        String id = (String) config.get("id");
                        if (id == null || id.isBlank()) {
                            log.warn("跳过 id 为空的技能定义，来源: {}", fileName);
                            continue;
                        }
                        SkillMeta meta = buildSkillMeta(config, fileName);
                        if (meta != null) {
                            skillMetaMap.put(id, meta);
                        }

                        // ★ 2. 再创建 EmsSkill 并注册
                        EmsSkill skill = createSkillFromConfig(config);
                        if (skill != null) {
                            if (registry.exists(id)) {
                                log.warn("⚠️ 技能 ID '{}' 重复（来自 {}），将覆盖已有定义",
                                    id, fileName);
                            }
                            registry.register(skill);
                            log.info("✅ 注册技能: {} (类型: {}, 职责: {})",
                                id, config.get("type"), config.get("duty"));
                            totalLoaded++;
                        }
                    }
                    log.info("✅ 从 {} 加载技能配置 ({} 个)", fileName, skills.size());

                } catch (Exception e) {
                    log.error("❌ 加载技能文件失败: {}", fileName, e);
                }
            }

            log.info("📋 总计加载 {} 个技能（含 IO 元数据索引 {} 条）",
                totalLoaded, skillMetaMap.size());

        } catch (Exception e) {
            log.error("❌ 加载 YAML 技能配置失败", e);
        }
    }

    // ====================================================================
    // ★ 新增：从原始 YAML 配置提取 SkillMeta
    // ====================================================================

    @SuppressWarnings("unchecked")
    private SkillMeta buildSkillMeta(Map<String, Object> config, String sourceFile) {
        String id = (String) config.get("id");
        String type = (String) config.get("type");
        String duty = (String) config.get("duty");

        Map<String, Object> params = (Map<String, Object>) config.get("params");
        if (params == null) {
            params = Map.of();
        }

        Set<String> writes = new LinkedHashSet<>();
        Set<String> reads = new LinkedHashSet<>();

        if (type == null) {
            return new SkillMeta(id, "unknown", duty, sourceFile, writes, reads);
        }

        switch (type) {
            case "query": {
                // QuerySkill 读 targetVar
                String targetVar = (String) params.get("targetVar");
                addIfNotBlank(reads, targetVar);
                break;
            }
            case "standard": {
                // StandardActionSkill 写 successVar
                String successVar = (String) params.get("successVar");
                addIfNotBlank(writes, successVar);
                // 读 prerequisites（如果有）
                collectPrerequisiteVars(params, reads);
                break;
            }
            case "generic": {
                // 读 sourceVar / sourceVars
                addIfNotBlank(reads, (String) params.get("sourceVar"));
                collectStringList(params.get("sourceVars"), reads);
                // 读 prerequisites
                collectPrerequisiteVars(params, reads);
                // 写 outputs
                Object outputConfigs = params.get("outputs");
                if (outputConfigs instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Object var = m.get("var");
                            if (var != null && !String.valueOf(var).isBlank()) {
                                writes.add(String.valueOf(var));
                            }
                        }
                    }
                }
                // 写 targetVar（若有）
                addIfNotBlank(writes, (String) params.get("targetVar"));
                break;
            }
            case "generic_assert": {
                // 读 sourceVar / sourceVars
                addIfNotBlank(reads, (String) params.get("sourceVar"));
                collectStringList(params.get("sourceVars"), reads);
                // 读 prerequisites
                collectPrerequisiteVars(params, reads);
                // 写 targetVar
                addIfNotBlank(writes, (String) params.get("targetVar"));
                // assertExpression 里的变量由契约构建器用 GuardExpressionParser 解析
                break;
            }
            case "complex": {
                // complex 技能由 Spring Bean 单独注册；
                // 若 YAML 里显式声明了 writes / reads，也一并采集
                collectStringList(params.get("writes"), writes);
                collectStringList(params.get("reads"), reads);
                collectPrerequisiteVars(params, reads);
                break;
            }
            default:
                // 未知类型，尽量宽容地采集
                collectStringList(params.get("writes"), writes);
                collectStringList(params.get("reads"), reads);
        }

        return new SkillMeta(id, type, duty, sourceFile, writes, reads);
    }

    @SuppressWarnings("unchecked")
    private void collectPrerequisiteVars(Map<String, Object> params, Set<String> reads) {
        Object prereqConfigs = params.get("prerequisites");
        if (!(prereqConfigs instanceof List<?> list)) return;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object var = m.get("variable");
                if (var != null && !String.valueOf(var).isBlank()) {
                    reads.add(String.valueOf(var));
                }
            }
        }
    }

    private void collectStringList(Object source, Set<String> target) {
        if (source == null) return;
        if (source instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    target.add(String.valueOf(item));
                }
            }
        } else if (source instanceof String s && !s.isBlank()) {
            target.add(s);
        }
    }

    private void addIfNotBlank(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value);
        }
    }

    // ====================================================================
    // 对外查询（供契约构建）
    // ====================================================================

    /**
     * 取单个技能的 IO 元数据。
     */
    public SkillMeta getSkillMeta(String skillId) {
        return skillMetaMap.get(skillId);
    }

    /**
     * 取所有技能的 IO 元数据（只读视图）。
     */
    public Map<String, SkillMeta> getAllSkillMeta() {
        return Collections.unmodifiableMap(skillMetaMap);
    }

    /**
     * 列出所有已加载的技能 ID。
     */
    public Set<String> getAllSkillIds() {
        return Collections.unmodifiableSet(skillMetaMap.keySet());
    }

    /**
     * 便捷方法：取某技能的写入变量集合（不存在时返回空集）。
     */
    public Set<String> getSkillWrites(String skillId) {
        SkillMeta meta = skillMetaMap.get(skillId);
        return meta != null ? meta.getWrites() : Set.of();
    }

    /**
     * 便捷方法：取某技能的读取变量集合（不存在时返回空集）。
     */
    public Set<String> getSkillReads(String skillId) {
        SkillMeta meta = skillMetaMap.get(skillId);
        return meta != null ? meta.getReads() : Set.of();
    }

    // ====================================================================
    // 以下为原有逻辑，保持不变
    // ====================================================================

    @SuppressWarnings("unchecked")
    private EmsSkill createSkillFromConfig(Map<String, Object> config) {
        String id = (String) config.get("id");
        String dutyStr = (String) config.get("duty");
        String type = (String) config.get("type");
        Map<String, Object> params = (Map<String, Object>) config.get("params");

        if (dutyStr == null) {
            log.error("❌ 技能 {} 缺少 duty 字段", id);
            return null;
        }

        Duty duty;
        try {
            duty = Duty.valueOf(dutyStr);
        } catch (IllegalArgumentException e) {
            log.error("❌ 无效的职责枚举值: {}, 技能 ID: {}", dutyStr, id);
            return null;
        }

        switch (type) {
            case "query":
                return createQuerySkill(id, duty, params);

            case "standard":
                return createStandardSkill(id, duty, params);

            case "generic":
                return createGenericActionSkill(id, duty, params, spelEvaluator);

            case "generic_assert":
                return createGenericAssertSkill(id, duty, params, spelEvaluator);

            case "complex":
                log.info("复杂技能 {} 将由 Spring Bean 单独注册", id);
                return null;

            default:
                log.warn("未知技能类型: {}, 技能 ID: {}", type, id);
                return null;
        }
    }

    // ==================== 各类型技能创建方法 ====================

    private EmsSkill createQuerySkill(String id, Duty duty, Map<String, Object> params) {
        String targetVar = (String) params.get("targetVar");
        String template = (String) params.get("responseTemplate");

        Map<String, String> valueMap = null;
        Object valueMapObj = params.get("valueMap");
        if (valueMapObj instanceof Map) {
            valueMap = (Map<String, String>) valueMapObj;
        }

        return new QuerySkill(id, duty, targetVar, template, valueMap);
    }

    /**
     * StandardActionSkill 已精简为 6 参数构造器。
     * YAML 里的 waitForCompletion / timeoutSeconds 会被静默忽略。
     * 完成信号由 TeamManager 的 TaskCompletedEvent 驱动；
     * 超时兜底由 TeamMember.isTaskTimeout() 负责。
     */
    private EmsSkill createStandardSkill(String id, Duty duty, Map<String, Object> params) {
        String vrAction = (String) params.get("vrActionCode");
        String successVar = (String) params.get("successVar");
        String speech = (String) params.get("successSpeech");

        if (successVar == null || successVar.isBlank()) {
            log.error("❌ standard 技能 {} 缺少 successVar 字段，跳过注册", id);
            return null;
        }

        Object durationObj = params.get("durationSeconds");
        if (durationObj == null) {
            log.error("❌ standard 技能 {} 缺少 durationSeconds 字段，跳过注册", id);
            return null;
        }
        int duration = ((Number) durationObj).intValue();

        return new StandardActionSkill(id, duty, vrAction, duration, successVar, speech);
    }

    @SuppressWarnings("unchecked")
    private EmsSkill createGenericActionSkill(String id, Duty duty,
                                              Map<String, Object> params,
                                              SpELExpressionEvaluator spelEvaluator) {
        String vrAction = (String) params.get("vrActionCode");
        int duration = ((Number) params.get("durationSeconds")).intValue();
        String speech = (String) params.get("successSpeech");

        // 解析 outputs
        List<Map<String, Object>> outputConfigs =
            (List<Map<String, Object>>) params.get("outputs");
        Map<String, Object> outputs = new LinkedHashMap<>();
        if (outputConfigs != null) {
            for (Map<String, Object> out : outputConfigs) {
                String var = (String) out.get("var");
                Object value = out.get("value");
                if (var != null) {
                    outputs.put(var, value);
                }
            }
        }

        // 解析 prerequisites
        List<GenericActionSkill.Prerequisite> prerequisites = new ArrayList<>();
        List<Map<String, Object>> prereqConfigs =
            (List<Map<String, Object>>) params.get("prerequisites");
        if (prereqConfigs != null) {
            for (Map<String, Object> prereq : prereqConfigs) {
                String variable = (String) prereq.get("variable");
                Object expected = prereq.get("expected");
                String message = (String) prereq.get("message");
                prerequisites.add(
                    new GenericActionSkill.Prerequisite(variable, expected, message));
            }
        }

        // 解析 varMapping
        Map<String, String> varMapping = null;
        Object varMappingObj = params.get("varMapping");
        if (varMappingObj instanceof Map) {
            varMapping = (Map<String, String>) varMappingObj;
        }

        boolean waitForCompletion = true;
        if (params.containsKey("waitForCompletion")) {
            waitForCompletion = (Boolean) params.get("waitForCompletion");
        }
        int timeoutSeconds = params.containsKey("timeoutSeconds")
            ? ((Number) params.get("timeoutSeconds")).intValue()
            : duration + 3;

        String sourceVar = (String) params.get("sourceVar");

        List<String> sourceVars = null;
        Object sourceVarsObj = params.get("sourceVars");
        if (sourceVarsObj instanceof List) {
            sourceVars = new ArrayList<>();
            for (Object item : (List<?>) sourceVarsObj) {
                if (item != null) {
                    sourceVars.add(String.valueOf(item));
                }
            }
        }

        String targetVar = (String) params.get("targetVar");
        String unavailableValue = (String) params.get("unavailableValue");

        Map<String, String> valueMap = null;
        Object valueMapObj = params.get("valueMap");
        if (valueMapObj instanceof Map) {
            valueMap = (Map<String, String>) valueMapObj;
        }

        return new GenericActionSkill(
            id, duty,
            vrAction, duration, speech,
            outputs, prerequisites, varMapping,
            waitForCompletion, timeoutSeconds,
            targetVar,
            unavailableValue,
            valueMap,
            sourceVar,
            sourceVars
        );
    }

    @SuppressWarnings("unchecked")
    private EmsSkill createGenericAssertSkill(String id, Duty duty,
                                              Map<String, Object> params,
                                              SpELExpressionEvaluator spelEvaluator) {
        String assertExpression = (String) params.get("assertExpression");
        String targetVar = (String) params.get("targetVar");
        String successSpeech = (String) params.get("successSpeech");

        // 解析 valueMap
        Map<String, String> valueMap = null;
        Object valueMapObj = params.get("valueMap");
        if (valueMapObj instanceof Map) {
            valueMap = (Map<String, String>) valueMapObj;
        }

        // 解析 prerequisites
        List<GenericAssertSkill.Prerequisite> prerequisites = new ArrayList<>();
        List<Map<String, Object>> prereqConfigs =
            (List<Map<String, Object>>) params.get("prerequisites");
        if (prereqConfigs != null) {
            for (Map<String, Object> prereq : prereqConfigs) {
                String variable = (String) prereq.get("variable");
                Object expected = prereq.get("expected");
                String message = (String) prereq.get("message");
                prerequisites.add(
                    new GenericAssertSkill.Prerequisite(variable, expected, message));
            }
        }

        String sourceVar = (String) params.get("sourceVar");

        List<String> sourceVars = null;
        Object sourceVarsObj = params.get("sourceVars");
        if (sourceVarsObj instanceof List) {
            sourceVars = new ArrayList<>();
            for (Object item : (List<?>) sourceVarsObj) {
                if (item != null) {
                    sourceVars.add(String.valueOf(item));
                }
            }
        }

        String unavailableValue = (String) params.get("unavailableValue");

        return new GenericAssertSkill(
            id, duty,
            assertExpression,
            targetVar,
            successSpeech,
            valueMap,
            prerequisites,
            sourceVar,
            sourceVars,
            unavailableValue,
            spelEvaluator
        );
    }
}

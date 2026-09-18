package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.domain.RuleDefinition;
import com.ruoyi.ems.domain.dto.RuleConfig;
import com.ruoyi.ems.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RuleConfigLoader {

    @Value("${acls.rule.base-path:classpath:config/acls/}")
    private String basePath;

    private final Map<String, List<RuleDefinition>> caseRulesMap = new ConcurrentHashMap<>();
    private List<RuleDefinition> globalRules = new ArrayList<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @PostConstruct
    public void loadAllRules() {
        loadGlobalRules();
        loadCaseRules();
        log.info("✅ 规则加载完成: 全局规则 {} 条，案例规则 {} 个案例",
            globalRules.size(), caseRulesMap.size());
    }

    public synchronized void reloadAll() {
        log.info("🔄 规则全量热重载开始...");
        caseRulesMap.clear();
        globalRules.clear();
        loadGlobalRules();
        loadCaseRules();
        log.info("✅ 规则全量热重载完成");
    }

    public synchronized void reloadCase(String caseId) {
        log.info("🔄 重载案例规则: {}", caseId);
        caseRulesMap.remove(caseId);
        String casePattern = basePath + "cases/" + caseId + "/_rules.yaml";
        try {
            Resource resource = new PathMatchingResourcePatternResolver().getResource(casePattern);
            if (resource.exists()) {
                try (var is = resource.getInputStream()) {
                    RuleConfig config = yamlMapper.readValue(is, RuleConfig.class);
                    if (config != null && config.getRules() != null && caseId.equals(config.getCaseId())) {
                        caseRulesMap.put(caseId, config.getRules());
                        log.info("案例规则重载成功: caseId={}, 规则数={}", caseId, config.getRules().size());
                    }
                }
            } else {
                log.warn("案例规则文件不存在: {}", casePattern);
            }
        } catch (Exception e) {
            log.error("重载案例规则失败: {}", caseId, e);
            throw new RuntimeException("案例规则重载失败: " + caseId, e);
        }
    }

    private void loadGlobalRules() {
        String globalPath = basePath + "GLOBAL/_general-rules.yaml";
        try {
            Resource resource = new PathMatchingResourcePatternResolver().getResource(globalPath);
            if (resource.exists()) {
                RuleConfig config = yamlMapper.readValue(resource.getInputStream(), RuleConfig.class);
                if (config != null && config.getRules() != null) {
                    globalRules = config.getRules();
                    log.info("加载全局规则 {} 条", globalRules.size());
                }
            } else {
                log.warn("全局规则文件不存在: {}", globalPath);
            }
        } catch (IOException e) {
            log.error("加载全局规则失败", e);
        }
    }

    private void loadCaseRules() {
        String casePattern = basePath + "cases/*/_rules.yaml";
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(casePattern);
            for (Resource resource : resources) {
                try (var is = resource.getInputStream()) {
                    RuleConfig config = yamlMapper.readValue(is, RuleConfig.class);
                    if (config != null && config.getRules() != null && config.getCaseId() != null) {
                        caseRulesMap.put(config.getCaseId(), config.getRules());
                        log.info("加载案例规则: caseId={}, 规则数={}",
                            config.getCaseId(), config.getRules().size());
                    }
                }
            }
        } catch (IOException e) {
            log.error("加载案例规则失败", e);
        }
    }

    // ==================== 查询接口 ====================

    public List<RuleDefinition> getMergedRulesForCase(String caseId) {
        List<RuleDefinition> merged = new ArrayList<>();
        if (globalRules != null) {
            merged.addAll(globalRules);
        }
        List<RuleDefinition> caseRules = caseRulesMap.get(caseId);
        if (caseRules != null) {
            Map<String, RuleDefinition> ruleMap = new HashMap<>();
            for (RuleDefinition rule : merged) {
                if (rule.getId() != null) {
                    ruleMap.put(rule.getId(), rule);
                }
            }
            for (RuleDefinition caseRule : caseRules) {
                if (caseRule.getId() != null) {
                    ruleMap.put(caseRule.getId(), caseRule);
                } else {
                    merged.add(caseRule);
                }
            }
            merged = new ArrayList<>(ruleMap.values());
        }
        merged.sort(Comparator.comparingInt(RuleDefinition::getPriority));
        return merged;
    }

    public List<RuleDefinition> getGlobalRules() {
        return Collections.unmodifiableList(globalRules);
    }

    public Map<String, List<RuleDefinition>> getCaseRulesMap() {
        return Collections.unmodifiableMap(caseRulesMap);
    }

    // ==================== 校验方法（供 SPI 调用） ====================

    public void validateAllRules(YamlStateMachineLoader stateMachineLoader,
                                 CaseIntentDefinitionsLoader intentLoader) {
        validateRules(globalRules, "GLOBAL", null, stateMachineLoader, intentLoader);
        for (Map.Entry<String, List<RuleDefinition>> entry : caseRulesMap.entrySet()) {
            validateRules(entry.getValue(), "案例 " + entry.getKey(), entry.getKey(), stateMachineLoader, intentLoader);
        }
    }

    public void validateCaseRules(String caseId,
                                  YamlStateMachineLoader stateMachineLoader,
                                  CaseIntentDefinitionsLoader intentLoader) {
        List<RuleDefinition> rules = caseRulesMap.get(caseId);
        if (rules == null || rules.isEmpty()) return;
        validateRules(rules, "案例 " + caseId, caseId, stateMachineLoader, intentLoader);
    }

    /**
     * 核心校验方法
     * @param rules 规则列表
     * @param scope 作用域（GLOBAL / 案例 xxx）
     * @param caseId 案例ID（全局规则时为 null）
     */
    private void validateRules(List<RuleDefinition> rules, String scope,
                               String caseId,
                               YamlStateMachineLoader stateMachineLoader,
                               CaseIntentDefinitionsLoader intentLoader) {
        if (rules == null || rules.isEmpty()) return;

        Set<String> ids = new HashSet<>();
        SpelExpressionParser parser = new SpelExpressionParser();

        for (RuleDefinition rule : rules) {
            // 1. ID 唯一性
            if (rule.getId() != null && !ids.add(rule.getId())) {
                throw new ConfigurationException(
                    String.format("规则ID重复: %s (scope: %s)", rule.getId(), scope));
            }

            // 2. intent 存在性
            String intentCode = rule.getIntent();
            if (intentCode == null) {
                throw new ConfigurationException(
                    String.format("规则缺少 intent 字段 (scope: %s, ruleId: %s)", scope, rule.getId()));
            }
            if (!intentLoader.isValidIntentCode(intentCode)) {
                throw new ConfigurationException(
                    String.format("规则引用的意图 '%s' 不存在 (scope: %s, ruleId: %s)",
                        intentCode, scope, rule.getId()));
            }

            // 3. 状态存在性校验
            List<String> allowedStates = rule.getAllowedStates();
            if (allowedStates != null && !allowedStates.isEmpty() && !allowedStates.contains("ANY")) {
                Set<String> allStateIds;
                if ("GLOBAL".equals(scope)) {
                    // 全局规则：检查状态是否在任意案例中存在
                    allStateIds = stateMachineLoader.getAllStateIds();
                } else {
                    // 案例规则：检查状态是否在该案例中存在
                    allStateIds = stateMachineLoader.getStateIdsForCase(caseId);
                    if (allStateIds.isEmpty()) {
                        log.warn("案例 {} 无状态定义，回退到全局状态集合", caseId);
                        allStateIds = stateMachineLoader.getAllStateIds();
                    }
                }
                for (String state : allowedStates) {
                    if (!allStateIds.contains(state)) {
                        throw new ConfigurationException(
                            String.format("规则引用的状态 '%s' 不存在 (scope: %s, caseId: %s, ruleId: %s)",
                                state, scope, caseId, rule.getId()));
                    }
                }
            }

            // 4. SpEL 条件语法校验
            List<String> conditions = rule.getConditions();
            if (conditions != null) {
                for (String expr : conditions) {
                    try {
                        parser.parseExpression(expr);
                    } catch (Exception e) {
                        throw new ConfigurationException(
                            String.format("规则条件表达式语法错误: '%s' (scope: %s, ruleId: %s) -> %s",
                                expr, scope, rule.getId(), e.getMessage()), e);
                    }
                }
            }
        }
    }
}

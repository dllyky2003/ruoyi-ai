package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.exception.ConfigurationException;
import jakarta.annotation.PostConstruct;
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

@Component
@Slf4j
public class EventLogDerivationLoader {

    private static final String CONFIG_PATH =
        "classpath:config/acls/GLOBAL/_event_log_derivations.json";

    /** 全部规则（保持配置顺序） */
    private volatile List<Rule> allRules = Collections.emptyList();
    /** 全局规则（无 caseTypes 限定） */
    private volatile List<Rule> globalRules = Collections.emptyList();
    /** 案例专属规则：caseType → rules */
    private final Map<String, List<Rule>> caseRules = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean loaded = false;

    @Autowired
    private ResourcePatternResolver resourceResolver;

    @PostConstruct
    public void init() {
        try {
            load();
        } catch (IOException e) {
            log.error("EventLogDerivationLoader 初始化失败", e);
        }
    }

    public synchronized void load() throws IOException {
        if (loaded) return;

        Resource resource = resourceResolver.getResource(CONFIG_PATH);
        if (!resource.exists()) {
            log.warn("⚠️ 事件日志派生配置不存在: {}，跳过加载", CONFIG_PATH);
            loaded = true;
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            ConfigFile file = objectMapper.readValue(is, ConfigFile.class);
            if (file.getDerivations() == null || file.getDerivations().isEmpty()) {
                log.info("事件日志派生配置为空");
                loaded = true;
                return;
            }

            List<Rule> all = new ArrayList<>();
            List<Rule> global = new ArrayList<>();
            Map<String, List<Rule>> byCase = new HashMap<>();
            Set<String> seenTargets = new HashSet<>();

            for (Rule r : file.getDerivations()) {
                if (!validate(r)) continue;

                // mode 解析
                String mode = r.getMode();
                if (mode == null || mode.isEmpty()) {
                    mode = inferMode(r.getTarget());
                    r.setMode(mode);
                }

                if (!seenTargets.add(r.getTarget())) {
                    throw new ConfigurationException(
                        "事件日志派生配置存在重复 target: " + r.getTarget());
                }

                all.add(r);

                // 分类归档
                if (r.getCaseTypes() == null || r.getCaseTypes().isEmpty()) {
                    global.add(r);
                } else {
                    for (String ct : r.getCaseTypes()) {
                        byCase.computeIfAbsent(ct, k -> new ArrayList<>()).add(r);
                    }
                }
            }

            this.allRules = Collections.unmodifiableList(all);
            this.globalRules = Collections.unmodifiableList(global);
            this.caseRules.clear();
            byCase.forEach((k, v) ->
                this.caseRules.put(k, Collections.unmodifiableList(v)));

            log.info("📋 事件日志派生规则加载完成: 全局 {} 条, 案例专属 {} 个案例",
                global.size(), caseRules.size());
            loaded = true;

        } catch (IOException e) {
            log.error("❌ 加载事件日志派生配置失败", e);
            throw e;
        }
    }

    public synchronized void reload() throws IOException {
        loaded = false;
        allRules = Collections.emptyList();
        globalRules = Collections.emptyList();
        caseRules.clear();
        load();
    }

    /**
     * 取适用于指定 caseType 的全部规则。
     * 结果包含：全局规则 + 该 caseType 的专属规则。
     */
    public List<Rule> getRulesForCase(String caseType) {
        if (caseType == null || caseType.isEmpty()) {
            return globalRules;
        }
        List<Rule> specific = caseRules.get(caseType);
        if (specific == null || specific.isEmpty()) {
            return globalRules;
        }
        List<Rule> merged = new ArrayList<>(globalRules.size() + specific.size());
        merged.addAll(globalRules);
        merged.addAll(specific);
        return merged;
    }

    public List<Rule> getAllRules() {
        return allRules;
    }

    // ==================== 内部逻辑 ====================

    private boolean validate(Rule r) {
        if (r.getCode() == null || r.getCode().isEmpty()) {
            log.warn("跳过无效规则（code 为空）: {}", r);
            return false;
        }
        if (r.getTarget() == null || r.getTarget().isEmpty()) {
            log.warn("跳过无效规则（target 为空）: {}", r);
            return false;
        }
        if (r.getMode() != null && !r.getMode().isEmpty()
            && !"first".equals(r.getMode()) && !"last".equals(r.getMode())) {
            log.warn("跳过无效规则（mode 非法）: {}", r);
            return false;
        }
        return true;
    }

    private String inferMode(String target) {
        if (target == null || target.isEmpty()) return "first";
        int idx = target.lastIndexOf('.');
        String local = idx >= 0 ? target.substring(idx + 1) : target;
        return local.startsWith("last") ? "last" : "first";
    }

    // ==================== 数据类 ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfigFile {
        private String version;
        private String description;
        private List<Rule> derivations;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {
        private String code;
        private String target;
        private String mode;              // 可选，未填时从 target 前缀推导
        private List<String> caseTypes;   // 可选，未填表示全局
    }
}

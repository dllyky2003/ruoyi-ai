package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.domain.DerivationRule;
import com.ruoyi.ems.domain.DerivationsConfig;
import jakarta.annotation.PostConstruct;   // ★ 改为 jakarta
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DerivationRuleLoader {

    /** 每个案例的合并后规则（全局 + 案例专属） */
    private final Map<String, List<DerivationRule>> caseRules = new ConcurrentHashMap<>();

    /** 全局规则 */
    private volatile List<DerivationRule> globalRules = new ArrayList<>();

    /** 是否已加载过，供幂等保护使用 */
    private volatile boolean loaded = false;

    // ====================================================================
    // 加载入口
    // ====================================================================


    public void loadAll() {
        if (loaded) {
            log.debug("DerivationRuleLoader 已加载，跳过重复加载");
            return;
        }
        doLoadAll();
        loaded = true;
    }

    /**
     * 全量重新加载：清空已有规则，重新扫描。
     * 供 CaseConfigLoader.reloadAll() 调用。
     */
    public synchronized void reloadAll() {
        log.info("🔄 重新加载派生规则（全量）...");
        caseRules.clear();
        globalRules = new ArrayList<>();
        loaded = false;
        doLoadAll();
        loaded = true;
    }

    /**
     * 重新加载单个案例。
     * 供 CaseConfigLoader.reloadCase(caseId) 调用。
     */
    public synchronized void reloadCase(String caseId) {
        if (caseId == null || caseId.isEmpty()) {
            log.warn("reloadCase: caseId 为空，跳过");
            return;
        }
        log.info("🔄 重新加载案例 [{}] 的派生规则...", caseId);
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = "classpath*:config/acls/cases/" + caseId + "/_derivations.yaml";
            Resource[] resources = resolver.getResources(pattern);

            if (resources.length == 0) {
                log.warn("案例 [{}] 未找到 _derivations.yaml", caseId);
                return;
            }

            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    DerivationsConfig config = yaml.loadAs(is, DerivationsConfig.class);
                    if (config != null && config.getDerivations() != null) {
                        // 合并：全局规则 + 案例专属（案例覆盖全局同名 target）
                        List<DerivationRule> merged = mergeWithGlobal(config.getDerivations());
                        caseRules.put(caseId, merged);
                        log.info("📄 重载案例 [{}] 派生规则: 原始 {} 条, 合并后 {} 条",
                            caseId, config.getDerivations().size(), merged.size());
                    }
                }
            }
        } catch (Exception e) {
            log.error("重新加载案例 [{}] 派生规则失败", caseId, e);
        }
    }

    // ====================================================================
    // 内部加载逻辑
    // ====================================================================

    private void doLoadAll() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:config/acls/**/_derivations.yaml");

            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                String path = resource.getURL().toString();
                String caseId = extractCaseId(path);
                try (InputStream is = resource.getInputStream()) {
                    DerivationsConfig config = yaml.loadAs(is, DerivationsConfig.class);
                    if (config != null && config.getDerivations() != null) {
                        if ("GLOBAL".equals(caseId)) {
                            globalRules = config.getDerivations();
                            log.info("📄 加载全局派生规则: {} 条", globalRules.size());
                        } else {
                            caseRules.put(caseId, config.getDerivations());
                            log.info("📄 加载案例 [{}] 派生规则: {} 条",
                                caseId, config.getDerivations().size());
                        }
                    }
                }
            }

            // 合并：每个案例 = 全局规则 + 案例专属规则（案例覆盖全局同名 target）
            for (Map.Entry<String, List<DerivationRule>> entry : caseRules.entrySet()) {
                String caseId = entry.getKey();
                List<DerivationRule> merged = mergeWithGlobal(entry.getValue());
                entry.setValue(merged);
                log.info("✅ 案例 [{}] 合并后派生规则: {} 条", caseId, merged.size());
            }

            // 全局规则单独作为 "GLOBAL" 案例存储
            caseRules.put("GLOBAL", globalRules);

        } catch (Exception e) {
            log.error("加载派生规则失败", e);
        }
    }

    /**
     * 合并：全局规则 + 案例专属规则。
     * 同名 target 时，案例规则覆盖全局规则。
     */
    private List<DerivationRule> mergeWithGlobal(List<DerivationRule> caseOnly) {
        Map<String, DerivationRule> targetMap = new LinkedHashMap<>();
        for (DerivationRule rule : globalRules) {
            if (rule.getTarget() != null) {
                targetMap.put(rule.getTarget(), rule);
            }
        }
        if (caseOnly != null) {
            for (DerivationRule rule : caseOnly) {
                if (rule.getTarget() != null) {
                    targetMap.put(rule.getTarget(), rule);
                }
            }
        }
        return new ArrayList<>(targetMap.values());
    }

    private String extractCaseId(String path) {
        if (path.contains("/GLOBAL/") || path.contains("\\GLOBAL\\")) {
            return "GLOBAL";
        }
        Pattern p = Pattern.compile(".*/cases/([^/]+)/.*");
        Matcher m = p.matcher(path);
        if (m.find()) {
            return m.group(1);
        }
        return "GLOBAL";
    }

    // ====================================================================
    // 对外查询
    // ====================================================================

    /**
     * 取某案例的合并后派生规则。
     * 若案例不存在，回退到 GLOBAL。
     */
    public List<DerivationRule> getRulesForCase(String caseType) {
        List<DerivationRule> rules = caseRules.get(caseType);
        if (rules == null) {
            rules = caseRules.get("GLOBAL");
        }
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }

    /**
     * 取全局派生规则。
     */
    public List<DerivationRule> getGlobalRules() {
        return Collections.unmodifiableList(globalRules);
    }

    /**
     * 列出所有已加载的案例 ID（含 GLOBAL）。
     */
    public Set<String> getAllCaseIds() {
        return Collections.unmodifiableSet(caseRules.keySet());
    }
}

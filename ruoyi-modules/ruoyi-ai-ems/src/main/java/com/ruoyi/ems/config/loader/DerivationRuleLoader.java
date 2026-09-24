package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.domain.DerivationRule;
import com.ruoyi.ems.domain.DerivationsConfig;
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

    /** 每个案例的合并后规则（GLOBAL + _shared/<group> + 案例专属） */
    private final Map<String, List<DerivationRule>> caseRules = new ConcurrentHashMap<>();

    /** 共享组规则：key = 组名（tachycardia / bradycardia / ...） */
    private final Map<String, List<DerivationRule>> sharedRules = new ConcurrentHashMap<>();

    /** 案例 → 声明的共享组列表（来自 case.yaml 的 sharedGroups 字段） */
    private final Map<String, List<String>> caseSharedGroups = new ConcurrentHashMap<>();

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
        sharedRules.clear();
        caseSharedGroups.clear();
        globalRules = new ArrayList<>();
        loaded = false;
        doLoadAll();
        loaded = true;
    }

    /**
     * 重新加载单个案例。
     * 注意：共享组规则不重载；如需刷新共享组，请调用 reloadAll()。
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

            // 重新读该案例声明的共享组
            List<String> groups = loadSharedGroups(resolver, caseId);
            caseSharedGroups.put(caseId, groups);

            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    DerivationsConfig config = yaml.loadAs(is, DerivationsConfig.class);
                    if (config != null && config.getDerivations() != null) {
                        List<DerivationRule> merged = mergeWithShared(config.getDerivations(), groups);
                        caseRules.put(caseId, merged);
                        log.info("📄 重载案例 [{}] 派生规则: 原始 {} 条, 合并后 {} 条 (sharedGroups={})",
                            caseId, config.getDerivations().size(), merged.size(), groups);
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

            // ★ 1. 先加载 GLOBAL
            loadGlobalRules(resolver);

            // ★ 2. 加载共享层：cases/_shared/<group>/_derivations.yaml
            loadSharedRules(resolver);

            // ★ 3. 加载病例层：cases/<caseId>/_derivations.yaml
            Yaml yaml = new Yaml();
            Resource[] resources = resolver.getResources(
                "classpath*:config/acls/cases/*/_derivations.yaml");
            for (Resource resource : resources) {
                String path = resource.getURL().toString();
                String caseId = extractCaseId(path);
                if (caseId == null) {
                    // _shared 下的已被 loadSharedRules 处理，跳过
                    continue;
                }
                try (InputStream is = resource.getInputStream()) {
                    DerivationsConfig config = yaml.loadAs(is, DerivationsConfig.class);
                    if (config != null && config.getDerivations() != null) {
                        caseRules.put(caseId, config.getDerivations());
                        log.info("📄 加载案例 [{}] 派生规则: {} 条",
                            caseId, config.getDerivations().size());
                    }
                }
            }

            // ★ 4. 读每个病例的 case.yaml 的 sharedGroups
            loadAllCaseSharedGroups(resolver);

            // ★ 5. 合并：GLOBAL → _shared/<group> → 病例
            for (Map.Entry<String, List<DerivationRule>> entry : caseRules.entrySet()) {
                String caseId = entry.getKey();
                List<String> groups = caseSharedGroups.getOrDefault(caseId, Collections.emptyList());
                List<DerivationRule> merged = mergeWithShared(entry.getValue(), groups);
                entry.setValue(merged);
                log.info("✅ 案例 [{}] 合并后派生规则: {} 条 (sharedGroups={})",
                    caseId, merged.size(), groups);
            }

            // GLOBAL 自身也存一份（供 fallback）
            caseRules.put("GLOBAL", globalRules);

        } catch (Exception e) {
            log.error("加载派生规则失败", e);
        }
    }

    private void loadGlobalRules(PathMatchingResourcePatternResolver resolver) throws Exception {
        Resource[] resources = resolver.getResources(
            "classpath*:config/acls/GLOBAL/_derivations.yaml");
        Yaml yaml = new Yaml();
        for (Resource resource : resources) {
            try (InputStream is = resource.getInputStream()) {
                DerivationsConfig config = yaml.loadAs(is, DerivationsConfig.class);
                if (config != null && config.getDerivations() != null) {
                    globalRules = config.getDerivations();
                    log.info("📄 加载全局派生规则: {} 条", globalRules.size());
                }
            }
        }
    }

    private void loadSharedRules(PathMatchingResourcePatternResolver resolver) throws Exception {
        Resource[] resources = resolver.getResources(
            "classpath*:config/acls/cases/_shared/*/_derivations.yaml");
        Yaml yaml = new Yaml();
        for (Resource resource : resources) {
            String path = resource.getURL().toString();
            String group = extractSharedGroup(path);
            if (group == null) continue;
            try (InputStream is = resource.getInputStream()) {
                DerivationsConfig config = yaml.loadAs(is, DerivationsConfig.class);
                if (config != null && config.getDerivations() != null) {
                    sharedRules.put(group, config.getDerivations());
                    log.info("📄 加载共享组 [{}] 派生规则: {} 条",
                        group, config.getDerivations().size());
                }
            }
        }
    }

    private void loadAllCaseSharedGroups(PathMatchingResourcePatternResolver resolver) {
        try {
            Resource[] caseYamls = resolver.getResources(
                "classpath*:config/acls/cases/*/case.yaml");
            for (Resource res : caseYamls) {
                String path = res.getURL().toString();
                String caseId = extractCaseIdFromCaseYaml(path);
                if (caseId == null) continue;
                List<String> groups = readSharedGroups(res);
                caseSharedGroups.put(caseId, groups);
                if (!groups.isEmpty()) {
                    log.info("📎 案例 [{}] 声明共享组: {}", caseId, groups);
                }
            }
        } catch (Exception e) {
            log.warn("读取 case.yaml 的 sharedGroups 失败", e);
        }
    }

    private List<String> loadSharedGroups(PathMatchingResourcePatternResolver resolver,
                                          String caseId) {
        try {
            Resource[] resources = resolver.getResources(
                "classpath*:config/acls/cases/" + caseId + "/case.yaml");
            if (resources.length > 0) {
                return readSharedGroups(resources[0]);
            }
        } catch (Exception e) {
            log.warn("读取案例 [{}] 的 sharedGroups 失败", caseId, e);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> readSharedGroups(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(is);
            if (map == null) return Collections.emptyList();
            Object raw = map.get("sharedGroups");
            if (raw instanceof List) {
                List<String> result = new ArrayList<>();
                for (Object o : (List<Object>) raw) {
                    if (o != null) result.add(o.toString());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("解析 case.yaml 的 sharedGroups 失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 合并顺序：GLOBAL → _shared/<group>（按声明顺序）→ 病例层。
     * 同名 target 后者覆盖前者。
     */
    private List<DerivationRule> mergeWithShared(List<DerivationRule> caseOnly,
                                                 List<String> groups) {
        Map<String, DerivationRule> targetMap = new LinkedHashMap<>();
        // 1. GLOBAL（最低优先级）
        for (DerivationRule r : globalRules) {
            if (r.getTarget() != null) targetMap.put(r.getTarget(), r);
        }
        // 2. _shared/<group>（按声明顺序覆盖）
        if (groups != null) {
            for (String g : groups) {
                List<DerivationRule> gr = sharedRules.get(g);
                if (gr != null) {
                    for (DerivationRule r : gr) {
                        if (r.getTarget() != null) targetMap.put(r.getTarget(), r);
                    }
                }
            }
        }
        // 3. 病例层（最高优先级）
        if (caseOnly != null) {
            for (DerivationRule r : caseOnly) {
                if (r.getTarget() != null) targetMap.put(r.getTarget(), r);
            }
        }
        return new ArrayList<>(targetMap.values());
    }

    private String extractCaseId(String path) {
        if (path.contains("/_shared/")) return null;
        if (path.contains("/GLOBAL/")) return "GLOBAL";
        Pattern p = Pattern.compile(".*/cases/([^/]+)/.*");
        Matcher m = p.matcher(path);
        if (m.find()) {
            String id = m.group(1);
            if (id.startsWith("_")) return null;   // 保险：_ 开头目录一律跳过
            return id;
        }
        return "GLOBAL";
    }

    private String extractCaseIdFromCaseYaml(String path) {
        Pattern p = Pattern.compile(".*/cases/([^/]+)/case\\.yaml");
        Matcher m = p.matcher(path);
        if (m.find()) {
            String id = m.group(1);
            if (id.startsWith("_")) return null;
            return id;
        }
        return null;
    }

    private String extractSharedGroup(String path) {
        Pattern p = Pattern.compile(".*/cases/_shared/([^/]+)/_derivations\\.yaml");
        Matcher m = p.matcher(path);
        if (m.find()) return m.group(1);
        return null;
    }

    // ====================================================================
    // 对外查询
    // ====================================================================

    public List<DerivationRule> getRulesForCase(String caseType) {
        List<DerivationRule> rules = caseRules.get(caseType);
        if (rules == null) {
            rules = caseRules.get("GLOBAL");
        }
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }

    public List<DerivationRule> getGlobalRules() {
        return Collections.unmodifiableList(globalRules);
    }

    public Set<String> getAllCaseIds() {
        return Collections.unmodifiableSet(caseRules.keySet());
    }
}

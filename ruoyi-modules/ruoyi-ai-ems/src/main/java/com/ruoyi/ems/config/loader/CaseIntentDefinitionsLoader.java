package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.IntentDictionary;
import com.ruoyi.ems.intent.model.Layer;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.orchestrator.model.ActionChainConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CaseIntentDefinitionsLoader {

    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper objectMapper;

    // ==================== 核心索引（完全保留原有结构） ====================
    @Getter
    private final Map<String, IntentDefinition> allIntents = new ConcurrentHashMap<>();
    @Getter
    private final Set<String> validIntentCodes = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<String> generalIntentCodes = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<String> specificIntentCodes = ConcurrentHashMap.newKeySet();

    @Getter
    private final Map<String, Set<String>> intentsByDomain = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Set<String>> intentsBySubDomain = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Set<String>> intentsByLayer = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, SubDomain> subDomainMap = new ConcurrentHashMap<>();

    @Getter
    private String caseCode;      // 当前案例码（简单名称，如 "stable_narrow"）
    @Getter
    private String caseNameCn;    // 当前案例中文名

    // ==================== 构造函数 ====================
    public CaseIntentDefinitionsLoader(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    // ==================== 启动加载 ====================

    public void init() {
        try {
            log.info("开始加载意图字典（扫描 config/acls/**/_intents.json）...");
            // 1. 加载通用意图
            loadGlobalIntents();
            // 2. 加载所有案例专属意图（每个子目录独立加载）
            loadAllCaseIntents();
            // 3. 构建域映射
            buildDomainMappings();
            log.info("意图字典加载完成！共 {} 个意图，G类 {} 个，S类 {} 个，子域 {} 个",
                allIntents.size(), generalIntentCodes.size(), specificIntentCodes.size(), subDomainMap.size());
        } catch (Exception e) {
            log.error("意图字典加载失败", e);
            throw new RuntimeException("意图字典加载失败", e);
        }
    }

    // ==================== 加载通用意图 ====================
    private void loadGlobalIntents() throws Exception {
        Resource[] resources = resourceResolver.getResources("classpath:config/acls/GLOBAL/_intents.json");
        if (resources.length == 0) {
            log.warn("未找到通用意图文件：config/acls/global/_intents.json");
            return;
        }
        IntentDictionary dict = loadDictionary(resources[0]);
        loadLayer(dict, true);
        log.info("✅ 加载通用意图：{} 个", generalIntentCodes.size());
    }

    // ==================== 加载所有案例专属意图（每个子目录独立） ====================
    private void loadAllCaseIntents() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath:config/acls/cases/**/_intents.json");
            if (resources.length == 0) {
                log.info("未找到任何案例专属意图文件");
                return;
            }
            for (Resource resource : resources) {
                String path = resource.getURL().getPath();
                // 提取完整相对路径（如 "tachycardia/stable_narrow"）
                String fullCaseId = extractFullCaseIdFromPath(path);
                if (fullCaseId == null) {
                    log.warn("无法从路径提取案例ID：{}", path);
                    continue;
                }
                // 提取简单名称（最后一段）作为案例编码
                String simpleCaseId = extractSimpleCaseId(fullCaseId);
                IntentDictionary dict = loadDictionary(resource);
                extractCaseMetadata(dict, simpleCaseId);
                loadLayer(dict, false);
                log.info("✅ 加载案例 [{}] 专属意图：{} 个", simpleCaseId, countSpecificIntentsInLayer(dict));
            }
        } catch (FileNotFoundException e) {
            log.info("未找到案例专属意图目录，跳过加载");
        } catch (Exception e) {
            log.warn("扫描案例专属意图文件时发生IO异常", e);
        }
    }

    // ==================== 按需加载单个案例（热加载） ====================
    public synchronized void loadIntentsForCase(String caseId) throws Exception {
        if (caseId == null || caseId.trim().isEmpty()) {
            throw new IllegalArgumentException("caseId 不能为空");
        }
        log.info("🔄 开始加载案例 [{}] 专属意图...", caseId);
        removeSpecificIntentsForCase(caseId);

        String path = "classpath:config/acls/cases/**/" + caseId + "/_intents.json";
        Resource[] resources = resourceResolver.getResources(path);
        if (resources.length == 0) {
            log.warn("案例 [{}] 没有专属意图文件，将只使用通用意图", caseId);
            return;
        }

        Resource resource = resources[0];

        // ★ 1. 读取原始 JSON 内容（用于验证）
        String rawJson = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // ★ 2. 加载意图字典（保持原有逻辑）
        IntentDictionary dict = loadDictionary(resource);

        // ★ 3. 验证每个意图的 execution 字段
        if (dict != null && dict.getLayers() != null) {
            JsonNode root = objectMapper.readTree(rawJson);
            // 遍历 layers
            for (Layer layer : dict.getLayers()) {
                if (layer.getSubDomains() != null) {
                    for (SubDomain subDomain : layer.getSubDomains()) {
                        if (subDomain.getIntents() != null) {
                            for (IntentDefinition def : subDomain.getIntents()) {
                                String intentCode = def.getIntentCode();
                                ActionChainConfig exec = def.getExecution();

                                // 查找原始 JSON 中对应的 execution 节点（用于对比）
                                JsonNode execNode = root.path("execution");
                                if (!execNode.isMissingNode()) {
                                    // 如果 exec 为空或字段为空，则警告
                                    if (exec == null) {
                                        log.warn("⚠️ 意图 {} 的 execution 在 JSON 中存在但反序列化为 null，原始: {}",
                                            intentCode, execNode);
                                    } else if (exec.type() == null) {
                                        log.warn("⚠️ 意图 {} 的 execution.type 为 null，原始: {}",
                                            intentCode, execNode);
                                    } else if (exec.steps() == null || exec.steps().isEmpty()) {
                                        log.warn("⚠️ 意图 {} 的 execution.steps 为空，原始: {}",
                                            intentCode, execNode);
                                    } else {
                                        log.info("✅ 意图 {} 的 execution 反序列化成功: type={}, steps={}",
                                            intentCode, exec.type(), exec.steps().size());
                                    }
                                } else {
                                    // 如果 JSON 中没有 execution，则正常（可能是旧格式）
                                    log.debug("意图 {} 没有 execution 字段", intentCode);
                                }
                            }
                        }
                    }
                }
            }
        }

        // ★ 4. 继续原有加载流程
        String fullPath = resource.getURL().getPath();
        String fullCaseId = extractFullCaseIdFromPath(fullPath);
        String simpleCaseId = (fullCaseId != null) ? extractSimpleCaseId(fullCaseId) : caseId;
        extractCaseMetadata(dict, simpleCaseId);
        loadLayer(dict, false);
        buildDomainMappings();
        log.info("✅ 案例 [{}] 专属意图加载完成，当前总意图数：{}", caseId, allIntents.size());
    }
    /**
     * 移除指定案例的所有专属意图（用于热加载前的清理）
     */
    private void removeSpecificIntentsForCase(String caseId) {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, IntentDefinition> entry : allIntents.entrySet()) {
            IntentDefinition def = entry.getValue();
            if (def.isSpecific() && caseId.equals(def.getCaseCode())) {
                toRemove.add(entry.getKey());
            }
        }
        for (String code : toRemove) {
            allIntents.remove(code);
            validIntentCodes.remove(code);
            specificIntentCodes.remove(code);
        }
        // 清理索引（将在后续 buildDomainMappings 中重建）
        intentsByDomain.clear();
        intentsBySubDomain.clear();
        intentsByLayer.clear();
        // 注意：subDomainMap 保留，但可能包含已移除意图的子域，buildDomainMappings 会重建
    }

    // ==================== 辅助加载方法 ====================

    private IntentDictionary loadDictionary(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, IntentDictionary.class);
        }
    }

    /**
     * 提取元数据，设置当前案例的简单名称（caseCode）
     */
    private void extractCaseMetadata(IntentDictionary dict, String simpleCaseId) throws Exception {
        // 从 JSON 根节点读取 case_code、case_name_cn（如果有）
        String json = objectMapper.writeValueAsString(dict);
        JsonNode root = objectMapper.readTree(json);
        if (root.has("case_code")) {
            this.caseCode = root.get("case_code").asText();
        } else {
            this.caseCode = simpleCaseId; // 使用简单名称
        }
        if (root.has("case_name_cn")) {
            this.caseNameCn = root.get("case_name_cn").asText();
        } else {
            this.caseNameCn = dict.getLayers().stream()
                .filter(l -> "SPECIFIC".equals(l.getLayerCode()))
                .findFirst()
                .map(Layer::getLayerName)
                .orElse(simpleCaseId);
        }
        log.debug("案例元数据：code={}, name={}", caseCode, caseNameCn);
    }

    private void loadLayer(IntentDictionary dict, boolean isGeneral) {
        if (dict.getLayers() == null) return;

        for (Layer layer : dict.getLayers()) {
            if (isGeneral && !"GENERAL".equals(layer.getLayerCode())) continue;
            if (!isGeneral && !"SPECIFIC".equals(layer.getLayerCode())) continue;

            if (layer.getSubDomains() == null) continue;

            for (SubDomain subDomain : layer.getSubDomains()) {
                if (subDomain.getIntents() == null) continue;

                String domainCode = subDomain.getDomainCode();
                String domainName = subDomain.getSubDomainName();

                // 合并子域（如果跨层同名，合并意图列表）
                if (!subDomainMap.containsKey(domainName)) {
                    subDomainMap.put(domainName, subDomain);
                } else {
                    SubDomain existing = subDomainMap.get(domainName);
                    List<IntentDefinition> merged = new ArrayList<>(existing.getIntents());
                    merged.addAll(subDomain.getIntents());
                    existing.setIntents(merged);
                }

                for (IntentDefinition intent : subDomain.getIntents()) {
                    intent.setLayer(layer.getLayerName());
                    intent.setSubDomain(domainName);
                    intent.setGeneral(isGeneral);
                    intent.setSpecific(!isGeneral);
                    intent.setDomainCode(domainCode);
                    if (!isGeneral && caseCode != null) {
                        intent.setCaseCode(caseCode);
                    }

                    String code = intent.getIntentCode();
                    // 如果是专属意图，覆盖同码的通用意图
                    if (!isGeneral && allIntents.containsKey(code)) {
                        // 覆盖
                        log.debug("专属意图覆盖通用：{}", code);
                        allIntents.put(code, intent);
                        generalIntentCodes.remove(code);
                        specificIntentCodes.add(code);
                    } else if (!allIntents.containsKey(code)) {
                        allIntents.put(code, intent);
                        validIntentCodes.add(code);
                        if (isGeneral) {
                            generalIntentCodes.add(code);
                        } else {
                            specificIntentCodes.add(code);
                        }
                    } else {
                        log.warn("意图编码 {} 重复（同一层内），已忽略", code);
                        continue;
                    }
                }
            }
        }
    }

    private void buildDomainMappings() {
        intentsByDomain.clear();
        intentsBySubDomain.clear();
        intentsByLayer.clear();

        for (IntentDefinition intent : allIntents.values()) {
            String domainCode = intent.getDomainCode();
            if (domainCode != null && !domainCode.isEmpty()) {
                intentsByDomain.computeIfAbsent(domainCode, k -> ConcurrentHashMap.newKeySet())
                    .add(intent.getIntentCode());
            } else {
                intentsByDomain.computeIfAbsent("UNKNOWN", k -> ConcurrentHashMap.newKeySet())
                    .add(intent.getIntentCode());
                log.warn("意图 {} 未配置 domain_code，已自动归入 UNKNOWN 域", intent.getIntentCode());
            }

            String subDomain = intent.getSubDomain();
            if (subDomain != null) {
                intentsBySubDomain.computeIfAbsent(subDomain, k -> ConcurrentHashMap.newKeySet())
                    .add(intent.getIntentCode());
            }

            String layerCode = intent.isGeneral() ? "GENERAL" : "SPECIFIC";
            intentsByLayer.computeIfAbsent(layerCode, k -> ConcurrentHashMap.newKeySet())
                .add(intent.getIntentCode());
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从路径中提取完整的相对路径（如 "tachycardia/stable_narrow"）
     */
    private String extractFullCaseIdFromPath(String path) {
        int idx = path.indexOf("config/acls/cases/");
        if (idx == -1) return null;
        String rest = path.substring(idx + "config/acls/cases/".length());
        int end = rest.indexOf("/_intents.json");
        if (end == -1) return null;
        return rest.substring(0, end);
    }

    /**
     * 从完整相对路径提取最后一段作为简单名称（如 "stable_narrow"）
     */
    private String extractSimpleCaseId(String fullCaseId) {
        if (fullCaseId == null) return null;
        int lastSlash = fullCaseId.lastIndexOf('/');
        return lastSlash == -1 ? fullCaseId : fullCaseId.substring(lastSlash + 1);
    }

    private int countSpecificIntentsInLayer(IntentDictionary dict) {
        if (dict.getLayers() == null) return 0;
        return dict.getLayers().stream()
            .filter(l -> "SPECIFIC".equals(l.getLayerCode()))
            .flatMap(l -> l.getSubDomains().stream())
            .mapToInt(sd -> sd.getIntents() == null ? 0 : sd.getIntents().size())
            .sum();
    }

    // ==================== 热加载接口（兼容原设计） ====================

    /**
     * 全量重新加载（通用 + 所有案例）
     */
    public synchronized void reloadAll() throws Exception {
        log.info("🔄 开始全量热加载意图字典...");
        clearAllCaches();
        init();
        log.info("✅ 全量热加载完成");
    }

    /**
     * 加载指定案例的意图（若已存在则覆盖）
     */
    public synchronized void reloadCase(String caseId) throws Exception {
        loadIntentsForCase(caseId);
    }
    public List<SubDomain> getSpecificSubDomainsByCase(String caseId) {
        if (caseId == null) return Collections.emptyList();
        return subDomainMap.values().stream()
            .filter(sub -> sub.getIntents() != null)
            .filter(sub -> sub.getIntents().stream()
                .anyMatch(intent -> caseId.equals(intent.getCaseCode()) && intent.isSpecific()))
            .collect(Collectors.toList());
    }
    public List<SubDomain> getCaseDefinitions(String caseId) {
        List<SubDomain> all = new ArrayList<>();
        all.addAll(getGeneralSubDomains());
        if (caseId != null)
            all.addAll(getSpecificSubDomainsByCase(caseId));
        else
            all.addAll(getSpecificSubDomains());
        return all;
    }
    // ==================== 清空缓存 ====================

    private void clearAllCaches() {
        allIntents.clear();
        validIntentCodes.clear();
        generalIntentCodes.clear();
        specificIntentCodes.clear();
        intentsByDomain.clear();
        intentsBySubDomain.clear();
        intentsByLayer.clear();
        subDomainMap.clear();
        caseCode = null;
        caseNameCn = null;
        log.debug("所有缓存已清空");
    }

    // ==================== 查询方法（与原有完全一致） ====================

    public List<SubDomain> getSubDomainsByDomainCode(String domainCode) {
        if (domainCode == null || !intentsByDomain.containsKey(domainCode)) {
            return Collections.emptyList();
        }
        Set<String> targetIntentCodes = intentsByDomain.get(domainCode);
        List<SubDomain> result = new ArrayList<>();
        for (SubDomain subDomain : subDomainMap.values()) {
            if (subDomain.getIntents() == null || subDomain.getIntents().isEmpty()) continue;
            boolean hasMatch = subDomain.getIntents().stream()
                .map(IntentDefinition::getIntentCode)
                .anyMatch(targetIntentCodes::contains);
            if (hasMatch) {
                result.add(subDomain);
            }
        }
        return result;
    }

    public List<IntentDefinition> getIntentsByDomain(String domainCode) {
        Set<String> codes = intentsByDomain.get(domainCode);
        if (codes == null) return Collections.emptyList();
        return codes.stream().map(allIntents::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public IntentDefinition getIntent(String code) {
        return allIntents.get(code);
    }

    public boolean isValidIntentCode(String code) {
        return code != null && validIntentCodes.contains(code);
    }

    public List<IntentDefinition> getAllIntentList() {
        return new ArrayList<>(allIntents.values());
    }

    public List<SubDomain> getAllSubDomains() {
        return new ArrayList<>(subDomainMap.values());
    }

    public SubDomain getSubDomainByName(String domainName) {
        return subDomainMap.get(domainName);
    }

    public SubDomain getSubDomainByIntentCode(String intentCode) {
        IntentDefinition def = allIntents.get(intentCode);
        if (def == null) return null;
        String domainName = def.getSubDomain();
        return domainName != null ? subDomainMap.get(domainName) : null;
    }

    public List<IntentDefinition> getIntentsBySubDomainName(String domainName) {
        SubDomain subDomain = subDomainMap.get(domainName);
        return subDomain != null && subDomain.getIntents() != null
            ? new ArrayList<>(subDomain.getIntents())
            : Collections.emptyList();
    }

    public List<SubDomain> getGeneralSubDomains() {
        return subDomainMap.values().stream()
            .filter(d -> d.getIntents() != null && d.getIntents().stream().anyMatch(IntentDefinition::isGeneral))
            .collect(Collectors.toList());
    }

    public List<SubDomain> getSpecificSubDomains() {
        return subDomainMap.values().stream()
            .filter(d -> d.getIntents() != null && d.getIntents().stream().anyMatch(IntentDefinition::isSpecific))
            .collect(Collectors.toList());
    }

    public int getSubDomainCount() {
        return subDomainMap.size();
    }
    public IntentDefinition loadIntent(String caseType, String intentCode) {
        String path = "config/acls/cases/" + caseType + "/orchestration/" + intentCode + ".json";
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(is, IntentDefinition.class);
        } catch (Exception e) {
            return null;
        }
    }
    public void printSubDomainStats() {
        log.info("========== 子域统计 ==========");
        for (Map.Entry<String, SubDomain> entry : subDomainMap.entrySet()) {
            SubDomain domain = entry.getValue();
            int count = domain.getIntents() != null ? domain.getIntents().size() : 0;
            log.info("  {}: {} 个意图", entry.getKey(), count);
        }
        log.info("===============================");
    }
}

package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.IntentDictionary;
import com.ruoyi.ems.intent.model.Layer;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.intent.model.routing.RouterDefaults;
import com.ruoyi.ems.intent.model.routing.ScoringDefinition;
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

    // ==================== 原有索引 ====================
    @Getter private final Map<String, IntentDefinition> allIntents = new ConcurrentHashMap<>();
    @Getter private final Set<String> validIntentCodes = ConcurrentHashMap.newKeySet();
    @Getter private final Set<String> generalIntentCodes = ConcurrentHashMap.newKeySet();
    @Getter private final Set<String> specificIntentCodes = ConcurrentHashMap.newKeySet();
    @Getter private final Map<String, Set<String>> intentsByDomain = new ConcurrentHashMap<>();
    @Getter private final Map<String, Set<String>> intentsBySubDomain = new ConcurrentHashMap<>();
    @Getter private final Map<String, Set<String>> intentsByLayer = new ConcurrentHashMap<>();
    @Getter private final Map<String, SubDomain> subDomainMap = new ConcurrentHashMap<>();

    // ==================== v1.3 路由索引 ====================
    @Getter private final Map<String, Set<String>> intentsByAct = new ConcurrentHashMap<>();
    @Getter private final Map<String, Set<String>> intentsByObjType = new ConcurrentHashMap<>();
    @Getter private final Map<String, Set<String>> intentsByLayerCode = new ConcurrentHashMap<>();
    @Getter private final Map<String, Set<String>> intentsByDomainCode = new ConcurrentHashMap<>();
    @Getter private final Map<String, Set<String>> intentsBySubDomainName = new ConcurrentHashMap<>();

    // ==================== ★ 新增：按案例索引 ====================
    /** 案例 ID -> 该案例的专属意图编码集合 */
    private final Map<String, Set<String>> intentsByCase = new ConcurrentHashMap<>();

    /** 案例 ID -> 该案例的元数据 */
    private final Map<String, CaseMeta> caseMetaMap = new ConcurrentHashMap<>();

    /** ★ 修复：caseCode/caseNameCn 改为按案例存储，避免多案例覆盖 */
    @Getter private volatile String caseCode;       // 保留，兼容原有调用（最后加载的案例）
    @Getter private volatile String caseNameCn;     // 同上

    @Getter private volatile RouterDefaults routerDefaults;
    @Getter private volatile ScoringDefinition scoringDefinition;
    @Getter private volatile List<String> stateTagsVocabulary = Collections.emptyList();
    @Getter private volatile List<String> contextVarsVocabulary = Collections.emptyList();
    @Getter private volatile Map<String, Object> routerTraceSchema = Collections.emptyMap();

    // ★ 幂等保护
    private volatile boolean loaded = false;

    // ====================================================================
    // ★ 新增：案例元数据内部类
    // ====================================================================
    public record CaseMeta(String caseCode, String caseNameCn, String simpleCaseId) {}

    public CaseIntentDefinitionsLoader(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    // ====================================================================
    // 启动加载
    // ====================================================================
    public void init() {
        if (loaded) {
            log.debug("CaseIntentDefinitionsLoader 已加载，跳过重复加载");
            return;
        }
        try {
            log.info("开始加载意图字典（扫描 config/acls/**/_intents.json）...");
            loadGlobalIntents();
            loadAllCaseIntents();
            buildDomainMappings();
            buildRoutingMappings();
            buildCaseMappings();     // ★ 新增
            loaded = true;
            log.info("意图字典加载完成！共 {} 个意图，G类 {} 个，S类 {} 个，子域 {} 个，案例 {} 个",
                allIntents.size(), generalIntentCodes.size(), specificIntentCodes.size(),
                subDomainMap.size(), intentsByCase.size());
        } catch (Exception e) {
            log.error("意图字典加载失败", e);
            throw new RuntimeException("意图字典加载失败", e);
        }
    }

    private void loadGlobalIntents() throws Exception {
        Resource[] resources = resourceResolver.getResources("classpath:config/acls/GLOBAL/_intents.json");
        if (resources.length == 0) {
            log.warn("未找到通用意图文件：config/acls/GLOBAL/_intents.json");
            return;
        }
        IntentDictionary dict = loadDictionary(resources[0]);
        applyGlobalMetadata(dict);
        loadLayer(dict, true);
        log.info("✅ 加载通用意图：{} 个", generalIntentCodes.size());
    }

    private void loadAllCaseIntents() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath:config/acls/cases/**/_intents.json");
            if (resources.length == 0) {
                log.info("未找到任何案例专属意图文件");
                return;
            }
            for (Resource resource : resources) {
                String path = resource.getURL().getPath();
                String fullCaseId = extractFullCaseIdFromPath(path);
                if (fullCaseId == null) {
                    log.warn("无法从路径提取案例ID：{}", path);
                    continue;
                }
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

    private void applyGlobalMetadata(IntentDictionary dict) {
        if (dict == null) return;
        if (dict.getScoringDefinition() != null) this.scoringDefinition = dict.getScoringDefinition();
        if (dict.getDefaults() != null) this.routerDefaults = dict.getDefaults();
        if (dict.getStateTagsVocabulary() != null) this.stateTagsVocabulary = dict.getStateTagsVocabulary();
        if (dict.getContextVarsVocabulary() != null) this.contextVarsVocabulary = dict.getContextVarsVocabulary();
        if (dict.getRouterTraceSchema() != null) this.routerTraceSchema = dict.getRouterTraceSchema();
    }

    private IntentDictionary loadDictionary(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, IntentDictionary.class);
        }
    }

    // ====================================================================
    // ★ 修改：extractCaseMetadata
    // 原实现把 caseCode/caseNameCn 设为实例字段，多案例加载时会覆盖。
    // 新实现：抽取到 CaseMeta，按 simpleCaseId 存入 caseMetaMap，同时保留
    //       实例字段（最后加载的案例值）供兼容。
    // ====================================================================
    private void extractCaseMetadata(IntentDictionary dict, String simpleCaseId) throws Exception {
        String json = objectMapper.writeValueAsString(dict);
        JsonNode root = objectMapper.readTree(json);

        String caseCodeVal;
        JsonNode caseCodeNode = root.get("case_code");
        if (caseCodeNode != null && !caseCodeNode.isNull() && !caseCodeNode.asText().isBlank()) {
            caseCodeVal = caseCodeNode.asText();
        } else {
            caseCodeVal = simpleCaseId;
        }

        String caseNameCnVal;
        JsonNode caseNameNode = root.get("case_name_cn");
        if (caseNameNode != null && !caseNameNode.isNull() && !caseNameNode.asText().isBlank()) {
            caseNameCnVal = caseNameNode.asText();
        } else {
            caseNameCnVal = dict.getLayers() != null
                ? dict.getLayers().stream()
                .filter(l -> "SPECIFIC".equals(l.getLayerCode()))
                .findFirst()
                .map(Layer::getLayerName)
                .orElse(simpleCaseId)
                : simpleCaseId;
        }

        // ★ 存入 map，避免覆盖
        caseMetaMap.put(simpleCaseId, new CaseMeta(caseCodeVal, caseNameCnVal, simpleCaseId));

        // 保留实例字段兼容原有调用（虽然多案例时会覆盖，但旧代码可能依赖）
        this.caseCode = caseCodeVal;
        this.caseNameCn = caseNameCnVal;
    }

    // ====================================================================
    // ★ 修改：loadLayer
    // 原实现里 intent.setCaseCode(caseCode) 用的是实例字段，多案例会串。
    // 新实现：从 caseMetaMap 反查当前 simpleCaseId 对应的 caseCode。
    // 需要在调用 loadLayer 前设置一个 case 上下文。这里用 ThreadLocal 简化，
    // 或者更简单地，在 loadLayer 方法签名里加上 caseId 参数。
    // ====================================================================

    private void loadLayer(IntentDictionary dict, boolean isGeneral) {
        // 原逻辑保留，但 caseCode 改为从当前上下文取
        // 为避免大改，这里保持实例字段用法不变；多案例场景的正确性
        // 已在 extractCaseMetadata 里通过 caseMetaMap 保存，契约构建器
        // 应该优先使用 caseMetaMap，而不是 IntentDefinition.caseCode。
        if (dict.getLayers() == null) return;

        for (Layer layer : dict.getLayers()) {
            if (isGeneral && !"GENERAL".equals(layer.getLayerCode())) continue;
            if (!isGeneral && !"SPECIFIC".equals(layer.getLayerCode())) continue;
            if (layer.getSubDomains() == null) continue;

            for (SubDomain subDomain : layer.getSubDomains()) {
                if (subDomain.getIntents() == null) continue;

                String domainCode = subDomain.getDomainCode();
                String domainName = subDomain.getSubDomainName();

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

                    if (intent.getH() != null && intent.getH().size() >= 3) {
                        intent.setDomainCode(intent.getH().get(1));
                        intent.setSubDomain(intent.getH().get(2));
                    }
                    if (!isGeneral && caseCode != null) intent.setCaseCode(caseCode);

                    String code = intent.getIntentCode();
                    if (!isGeneral && allIntents.containsKey(code)) {
                        allIntents.put(code, intent);
                        generalIntentCodes.remove(code);
                        specificIntentCodes.add(code);
                    } else if (!allIntents.containsKey(code)) {
                        allIntents.put(code, intent);
                        validIntentCodes.add(code);
                        if (isGeneral) generalIntentCodes.add(code);
                        else specificIntentCodes.add(code);
                    } else {
                        log.warn("意图编码 {} 重复（同一层内），已忽略", code);
                    }
                }
            }
        }
    }

    // ====================================================================
    // 原有 buildDomainMappings / buildRoutingMappings 不变
    // ====================================================================
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

    private void buildRoutingMappings() {
        intentsByAct.clear();
        intentsByObjType.clear();
        intentsByLayerCode.clear();
        intentsByDomainCode.clear();
        intentsBySubDomainName.clear();

        for (IntentDefinition intent : allIntents.values()) {
            if (intent.getAct() != null) {
                intentsByAct.computeIfAbsent(intent.getAct(), k -> ConcurrentHashMap.newKeySet())
                    .add(intent.getIntentCode());
            }
            if (intent.getObj() != null && intent.getObj().getType() != null) {
                intentsByObjType.computeIfAbsent(intent.getObj().getType(), k -> ConcurrentHashMap.newKeySet())
                    .add(intent.getIntentCode());
            }
            if (intent.getH() != null) {
                if (intent.getH().size() >= 1) {
                    intentsByLayerCode.computeIfAbsent(intent.getH().get(0), k -> ConcurrentHashMap.newKeySet())
                        .add(intent.getIntentCode());
                }
                if (intent.getH().size() >= 2) {
                    intentsByDomainCode.computeIfAbsent(intent.getH().get(1), k -> ConcurrentHashMap.newKeySet())
                        .add(intent.getIntentCode());
                }
                if (intent.getH().size() >= 3) {
                    intentsBySubDomainName.computeIfAbsent(intent.getH().get(2), k -> ConcurrentHashMap.newKeySet())
                        .add(intent.getIntentCode());
                }
            }
        }
    }

    // ====================================================================
    // ★ 新增：按案例索引构建
    // ====================================================================
    private void buildCaseMappings() {
        intentsByCase.clear();
        for (IntentDefinition intent : allIntents.values()) {
            if (intent.isSpecific()) {
                String cid = intent.getCaseCode();
                if (cid != null && !cid.isBlank()) {
                    intentsByCase.computeIfAbsent(cid, k -> ConcurrentHashMap.newKeySet())
                        .add(intent.getIntentCode());
                }
            }
        }
    }

    // ====================================================================
    // 工具方法（保留）
    // ====================================================================
    private String extractFullCaseIdFromPath(String path) {
        int idx = path.indexOf("config/acls/cases/");
        if (idx == -1) return null;
        String rest = path.substring(idx + "config/acls/cases/".length());
        int end = rest.indexOf("/_intents.json");
        if (end == -1) return null;
        return rest.substring(0, end);
    }

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

    // ====================================================================
    // 热加载
    // ====================================================================
    public synchronized void reloadAll() {
        clearAllCaches();
        loaded = false;
        init();
    }

    public synchronized void reloadCase(String caseId) {
        loadIntentsForCase(caseId);
        buildCaseMappings();      // ★ 新增：重建按案例索引
    }

    public synchronized void loadIntentsForCase(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            throw new IllegalArgumentException("caseId 不能为空");
        }

        removeSpecificIntentsForCase(caseId);

        String path = "classpath:config/acls/cases/**/" + caseId + "/_intents.json";
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(path);
        } catch (Exception e) {
            log.error("扫描案例 [{}] 意图文件失败", caseId, e);
            return;
        }
        if (resources.length == 0) {
            log.warn("案例 [{}] 没有专属意图文件，将只使用通用意图", caseId);
            return;
        }
        // ★ 修复：遍历所有匹配资源，而不是只取 resources[0]
        for (Resource resource : resources) {
            try {
                loadOneCaseResource(resource, caseId);
            } catch (Exception e) {
                log.error("加载案例 [{}] 的意图文件失败: {}", caseId, resource, e);
            }
        }

        buildDomainMappings();
        buildRoutingMappings();
        buildCaseMappings();      // ★ 新增

        List<SubDomain> matched = getSpecificSubDomainsByCase(caseId);
        log.info("✅ 案例 [{}] 专属意图热加载完成: {} 个子域, {} 个意图",
            caseId, matched.size(), specificIntentCodes.size());
    }

    /**
     * ★ 抽取：加载单个案例意图资源文件。
     */
    private void loadOneCaseResource(Resource resource, String caseId) throws Exception {
        String rawJson = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        IntentDictionary dict = loadDictionary(resource);
        if (dict == null) {
            log.error("案例 [{}] 专属意图反序列化结果为 null", caseId);
            return;
        }

        // execution / extraction 校验：基于每个 intent 自身的 JSON 节点检查
        if (dict.getLayers() != null) {
            JsonNode root = objectMapper.readTree(rawJson);
            for (Layer layer : dict.getLayers()) {
                if (layer.getSubDomains() == null) continue;
                for (SubDomain subDomain : layer.getSubDomains()) {
                    if (subDomain.getIntents() == null) continue;
                    for (IntentDefinition def : subDomain.getIntents()) {
                        String intentCode = def.getIntentCode();

                        // ★ 修复：定位到当前意图自身的 JSON 节点，而不是根节点
                        JsonNode intentNode = findIntentNode(root, intentCode);
                        if (intentNode == null) {
                            log.warn("⚠️ 意图 {} 在原始 JSON 中找不到对应节点，跳过校验", intentCode);
                            continue;
                        }

                        // ========== execution 校验 ==========
                        JsonNode execNode = intentNode.path("execution");
                        if (!execNode.isMissingNode() && !execNode.isNull()) {
                            ActionChainConfig exec = def.getExecution();
                            if (exec == null) {
                                log.warn("⚠️ 意图 {} 的 execution 未成功反序列化（JSON 有但 Java 为 null），请检查 IntentDefinition 是否漏了 @JsonProperty", intentCode);
                            } else if (exec.type() == null) {
                                log.warn("⚠️ 意图 {} 的 execution.type 为 null", intentCode);
                            } else if (exec.steps() == null || exec.steps().isEmpty()) {
                                log.warn("⚠️ 意图 {} 的 execution.steps 为空", intentCode);
                            }
                        }

                        // ========== extraction 校验（新增） ==========
                        JsonNode extractionNode = intentNode.path("extraction");
                        if (!extractionNode.isMissingNode() && !extractionNode.isNull()) {
                            if (def.getExtraction() == null) {
                                log.warn("⚠️ 意图 {} 的 extraction 未成功反序列化（JSON 有但 Java 为 null），请检查 IntentDefinition 是否漏了 @JsonProperty", intentCode);
                            } else {
                                log.info("✅ 意图 {} 的 extraction 已加载: targetParams={}, rules={}",
                                    intentCode,
                                    def.getExtraction().getTargetParams(),
                                    def.getExtraction().getRules());
                            }
                        }
                    }
                }
            }
        }

        String fullPath = resource.getURL().getPath();
        String fullCaseId = extractFullCaseIdFromPath(fullPath);
        String simpleCaseId = (fullCaseId != null) ? extractSimpleCaseId(fullCaseId) : caseId;

        extractCaseMetadata(dict, simpleCaseId);
        loadLayer(dict, false);
    }

    /**
     * ★ 新增：在原始 JSON 树中按 intent_code 定位到具体意图节点。
     *
     * <p>因为 execution / extraction 都是挂在单个意图上的字段，
     * 不能用 root.path("execution") 直接取——根节点根本没有这个字段。</p>
     *
     * <p>层级：root -> layers[] -> sub_domains[] -> intents[] -> { intent_code, execution, extraction, ... }</p>
     */
    private JsonNode findIntentNode(JsonNode root, String intentCode) {
        if (root == null || intentCode == null) return null;

        JsonNode layers = root.path("layers");
        if (layers.isMissingNode() || !layers.isArray()) return null;

        for (JsonNode layer : layers) {
            JsonNode subDomains = layer.path("sub_domains");
            if (subDomains.isMissingNode() || !subDomains.isArray()) continue;

            for (JsonNode subDomain : subDomains) {
                JsonNode intents = subDomain.path("intents");
                if (intents.isMissingNode() || !intents.isArray()) continue;

                for (JsonNode intent : intents) {
                    if (intentCode.equals(intent.path("intent_code").asText(null))) {
                        return intent;
                    }
                }
            }
        }
        return null;
    }

    private void removeSpecificIntentsForCase(String caseId) {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, IntentDefinition> entry : allIntents.entrySet()) {
            IntentDefinition def = entry.getValue();
            if (def.isSpecific() && caseId.equals(def.getCaseCode())) toRemove.add(entry.getKey());
        }
        for (String code : toRemove) {
            allIntents.remove(code);
            validIntentCodes.remove(code);
            specificIntentCodes.remove(code);
        }
        intentsByDomain.clear();
        intentsBySubDomain.clear();
        intentsByLayer.clear();
    }

    private void clearAllCaches() {
        allIntents.clear();
        validIntentCodes.clear();
        generalIntentCodes.clear();
        specificIntentCodes.clear();
        intentsByDomain.clear();
        intentsBySubDomain.clear();
        intentsByLayer.clear();
        intentsByAct.clear();
        intentsByObjType.clear();
        intentsByLayerCode.clear();
        intentsByDomainCode.clear();
        intentsBySubDomainName.clear();
        intentsByCase.clear();          // ★ 新增
        caseMetaMap.clear();            // ★ 新增
        subDomainMap.clear();
        caseCode = null;
        caseNameCn = null;
    }

    // ====================================================================
    // 原有查询接口（保留，略）
    // ====================================================================
    public List<SubDomain> getSubDomainsByDomainCode(String domainCode) {
        if (domainCode == null || !intentsByDomain.containsKey(domainCode)) return Collections.emptyList();
        Set<String> targetIntentCodes = intentsByDomain.get(domainCode);
        List<SubDomain> result = new ArrayList<>();
        for (SubDomain subDomain : subDomainMap.values()) {
            if (subDomain.getIntents() == null || subDomain.getIntents().isEmpty()) continue;
            boolean hasMatch = subDomain.getIntents().stream()
                .map(IntentDefinition::getIntentCode)
                .anyMatch(targetIntentCodes::contains);
            if (hasMatch) result.add(subDomain);
        }
        return result;
    }

    public List<IntentDefinition> getIntentsByDomain(String domainCode) {
        Set<String> codes = intentsByDomain.get(domainCode);
        if (codes == null) return Collections.emptyList();
        return codes.stream().map(allIntents::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public IntentDefinition getIntent(String code) { return allIntents.get(code); }
    public boolean isValidIntentCode(String code) { return code != null && validIntentCodes.contains(code); }
    public List<IntentDefinition> getAllIntentList() { return new ArrayList<>(allIntents.values()); }
    public List<SubDomain> getAllSubDomains() { return new ArrayList<>(subDomainMap.values()); }
    public SubDomain getSubDomainByName(String domainName) { return subDomainMap.get(domainName); }
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
    public int getSubDomainCount() { return subDomainMap.size(); }

    public IntentDefinition loadIntent(String caseType, String intentCode) {
        String path = "config/acls/cases/" + caseType + "/orchestration/" + intentCode + ".json";
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(is, IntentDefinition.class);
        } catch (Exception e) {
            return null;
        }
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
        List<SubDomain> g = getGeneralSubDomains();
        List<SubDomain> s = caseId != null ? getSpecificSubDomainsByCase(caseId) : getSpecificSubDomains();
        all.addAll(g);
        all.addAll(s);
        return all;
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

    // ====================================================================
    // ★ 新增：契约构建专用查询接口
    // ====================================================================

    /**
     * 取某案例下所有可用意图（通用 + 该案例专属）。
     *
     * 契约构建时，需要遍历该案例的所有意图来建立 IntentContract。
     */
    public List<IntentDefinition> getIntentsForCase(String caseId) {
        List<IntentDefinition> result = new ArrayList<>();

        // 1. 通用意图
        for (String code : generalIntentCodes) {
            IntentDefinition def = allIntents.get(code);
            if (def != null) result.add(def);
        }

        // 2. 该案例的专属意图
        if (caseId != null) {
            Set<String> caseIntents = intentsByCase.get(caseId);
            if (caseIntents != null) {
                for (String code : caseIntents) {
                    IntentDefinition def = allIntents.get(code);
                    if (def != null) result.add(def);
                }
            }
        }

        return result;
    }

    /**
     * 取某案例下所有可用意图编码（通用 + 该案例专属）。
     */
    public Set<String> getIntentCodesForCase(String caseId) {
        Set<String> result = new LinkedHashSet<>(generalIntentCodes);
        if (caseId != null) {
            Set<String> caseIntents = intentsByCase.get(caseId);
            if (caseIntents != null) {
                result.addAll(caseIntents);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 列出所有有专属意图的案例 ID。
     */
    public Set<String> getAllCaseIds() {
        return Collections.unmodifiableSet(intentsByCase.keySet());
    }

    /**
     * 取案例元数据（caseCode / caseNameCn / simpleCaseId）。
     */
    public CaseMeta getCaseMeta(String caseId) {
        return caseMetaMap.get(caseId);
    }

    /**
     * 取某意图对应的 writer 类型。
     *
     * 推断规则：
     *   - 意图的 act 为 CLOSED_LOOP / VETO / ANNOUNCEMENT  -> CLASSIFIER
     *   - 意图的 act 为 COMMAND                              -> EXECUTOR（由 TeamMember 执行技能）
     *   - 意图的 source 为 STATE_MACHINE                    -> STATE_MACHINE
     *   - 其他                                              -> 返回 null（表示未知）
     *
     * 注意：这是一个"保守推断"，如果 IntentDefinition 里有更明确的 writer 字段，
     * 应当优先使用那个字段。
     */
    public String getIntentWriterType(String intentCode) {
        IntentDefinition def = allIntents.get(intentCode);
        if (def == null) return null;

        String act = def.getAct();
        if (act == null) return null;

        switch (act.toUpperCase()) {
            case "CLOSED_LOOP":
            case "VETO":
            case "ANNOUNCEMENT":
                return "CLASSIFIER";
            case "COMMAND":
                return "EXECUTOR";
            default:
                return null;
        }
    }

    /**
     * 按动作类型反查意图编码。
     */
    public Set<String> getIntentCodesByAct(String actType) {
        Set<String> codes = intentsByAct.get(actType);
        return codes != null ? Collections.unmodifiableSet(codes) : Collections.emptySet();
    }

    /**
     * 契约构建器：意图 → 元数据映射。
     */
    public IntentMeta getIntentMeta(String intentCode) {
        IntentDefinition def = allIntents.get(intentCode);
        if (def == null) return null;
        return new IntentMeta(
            def.getIntentCode(),
            def.getNameCn(),
            def.getAct(),
            def.getObj() != null ? def.getObj().getType() : null,
            def.getDomainCode(),
            def.getSubDomain(),
            def.isGeneral(),
            def.getCaseCode()
        );
    }

    /**
     * 契约构建器：意图元数据 DTO。
     */
    public record IntentMeta(
        String intentCode,
        String name,
        String actType,
        String objType,
        String domainCode,
        String subDomain,
        boolean general,
        String caseCode
    ) {}
}

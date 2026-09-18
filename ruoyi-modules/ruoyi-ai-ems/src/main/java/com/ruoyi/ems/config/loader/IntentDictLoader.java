package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.intent.dict.InvertedHit;
import com.ruoyi.ems.intent.dict.PinyinUtil;
import com.ruoyi.ems.intent.dict.TextNormalizer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * 加载 inverted_intents.json。
 *
 * <p>格式：
 * <pre>
 * {
 *   "entries": {
 *     "surface": [["IA_xxx", "同义词", 0.95], ["IA_xxx", "标准", 1.0, "note"]]
 *   },
 *   "intents": {
 *     "IA_xxx": {"name": "...", "normalized": "..."}
 *   }
 * }
 * </pre>
 *
 * <p>拼音索引说明：
 *   对每个 surface 枚举 2~8 字子串，子串转拼音后入 pinyinMap。
 */
@Component
public class IntentDictLoader {

    private static final Logger log = LoggerFactory.getLogger(IntentDictLoader.class);

    private static final int SUB_MIN_LEN = 2;
    private static final int SUB_MAX_LEN = 8;

    @Value("${acls.dict.path:classpath:/config/acls/dict/inverted_intents.json}")
    private String dictPath;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    // ==================== 正向索引 ====================
    private final Map<String, List<InvertedHit>> exactMap = new HashMap<>();
    private final Map<String, Set<String>> pinyinMap = new HashMap<>();
    private final Set<String> allSurfaces = new HashSet<>();
    private final Map<Integer, List<String>> surfacesByLen = new HashMap<>();

    // ==================== ★ 新增：反向索引 ====================
    /** intentCode -> 命中该意图的所有 surface */
    private final Map<String, Set<String>> intentToSurfaces = new HashMap<>();

    /** intentCode -> 意图元数据（name / normalized） */
    private final Map<String, IntentMetaInfo> intentMetaMap = new HashMap<>();

    /** 是否已加载过，供幂等保护使用 */
    private volatile boolean loaded = false;

    public IntentDictLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public void setDictPath(String dictPath) {
        this.dictPath = dictPath;
    }

    // ====================================================================
    // 加载入口
    // ====================================================================


    public void load() {
        if (loaded) {
            log.debug("IntentDictLoader 已加载，跳过重复加载");
            return;
        }
        doLoad();
        loaded = true;
    }

    /**
     * 全量重新加载：清空已有索引，重新读取词典文件。
     * 供 CaseConfigLoader.reloadAll() 调用。
     *
     * 注意：词典是全局单文件，没有 case 维度，因此没有 reloadCase。
     */
    public synchronized void reloadAll() {
        log.info("🔄 重新加载意图词典（全量）...");
        clearAll();
        loaded = false;
        doLoad();
        loaded = true;
    }

    // ====================================================================
    // 内部加载逻辑
    // ====================================================================

    private synchronized void doLoad() {
        Resource res = resourceLoader.getResource(dictPath);
        if (!res.exists()) {
            log.error("词典文件不存在: {}", dictPath);
            return;
        }

        try (InputStream is = res.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);

            // 1. 读 intent 元数据表
            Map<String, IntentMeta> metaMap = readIntentMeta(root);
            log.info("intents 元数据: {} 条", metaMap.size());

            // ★ 同步到 intentMetaMap，供反向查询用
            metaMap.forEach((code, meta) ->
                intentMetaMap.put(code, new IntentMetaInfo(meta.name(), meta.normalized())));

            // 2. 读 entries
            JsonNode entriesNode = root.get("entries");
            if (entriesNode == null || !entriesNode.isObject()) {
                log.warn("entries 为空或不存在");
                return;
            }

            Iterator<Map.Entry<String, JsonNode>> it = entriesNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String rawSurface = e.getKey();
                if (rawSurface == null) continue;

                String surface = TextNormalizer.normalize(rawSurface);
                if (surface.isEmpty()) continue;

                JsonNode arr = e.getValue();
                if (!arr.isArray() || arr.isEmpty()) continue;

                List<InvertedHit> hits = new ArrayList<>();
                for (JsonNode item : arr) {
                    InvertedHit h = parseHit(item, metaMap);
                    if (h != null) hits.add(h);
                }
                if (hits.isEmpty()) continue;

                hits.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
                exactMap.computeIfAbsent(surface, k -> new ArrayList<>()).addAll(hits);
                allSurfaces.add(surface);

                // ★ 构建反向索引：intentCode -> surfaces
                for (InvertedHit h : hits) {
                    intentToSurfaces
                        .computeIfAbsent(h.intentCode(), k -> new LinkedHashSet<>())
                        .add(surface);
                }

                // ---- 拼音索引：枚举 surface 的所有 2~8 字子串 ----
                addPinyinSubstrings(surface);
            }

            // 3. 按长度分桶
            for (String s : allSurfaces) {
                surfacesByLen.computeIfAbsent(s.length(), k -> new ArrayList<>()).add(s);
            }

            log.info("词典加载完成: {} surfaces, {} pinyin keys, {} intents",
                allSurfaces.size(), pinyinMap.size(), intentToSurfaces.size());

        } catch (Exception ex) {
            // ★ 加载失败时保留已有索引（reloadAll 场景），并记录日志
            log.error("加载词典失败: {}", dictPath, ex);
        }
    }

    /**
     * 清空所有索引，供 reloadAll 使用。
     */
    private void clearAll() {
        exactMap.clear();
        pinyinMap.clear();
        allSurfaces.clear();
        surfacesByLen.clear();
        intentToSurfaces.clear();
        intentMetaMap.clear();
    }

    /**
     * 对 surface 的所有 2~8 字子串建拼音索引。
     */
    private void addPinyinSubstrings(String surface) {
        int len = surface.length();
        if (len < SUB_MIN_LEN) return;

        for (int start = 0; start < len; start++) {
            int maxN = Math.min(SUB_MAX_LEN, len - start);
            for (int n = SUB_MIN_LEN; n <= maxN; n++) {
                String sub = surface.substring(start, start + n);
                String py = PinyinUtil.toPinyin(sub);
                if (py.isEmpty()) continue;
                pinyinMap.computeIfAbsent(py, k -> new HashSet<>()).add(surface);
            }
        }
    }

    private record IntentMeta(String name, String normalized) {}

    /** ★ 供外部读取意图元数据（契约定性诊断可用） */
    public record IntentMetaInfo(String name, String normalized) {}

    private Map<String, IntentMeta> readIntentMeta(JsonNode root) {
        Map<String, IntentMeta> meta = new HashMap<>();
        JsonNode intentsNode = root.get("intents");
        if (intentsNode == null || !intentsNode.isObject()) return meta;

        Iterator<Map.Entry<String, JsonNode>> it = intentsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v == null || !v.isObject()) continue;
            String name = v.path("name").asText("");
            String norm = v.path("normalized").asText("");
            meta.put(e.getKey(), new IntentMeta(name, norm));
        }
        return meta;
    }

    private InvertedHit parseHit(JsonNode item, Map<String, IntentMeta> metaMap) {
        if (!item.isArray() || item.size() < 3) return null;

        String code = item.get(0).asText("");
        String type = item.get(1).asText("");
        double conf = item.get(2).asDouble(0.0);
        String note = item.size() > 3 ? item.get(3).asText("") : "";

        if (code.isEmpty()) return null;

        IntentMeta meta = metaMap.get(code);
        String name = meta != null ? meta.name() : "";
        String normalized = meta != null ? meta.normalized() : "";

        return new InvertedHit(code, name, normalized, type, conf, note);
    }

    // ====================================================================
    // 原有查询接口（保留）
    // ====================================================================

    public Map<String, List<InvertedHit>> getExactMap() { return exactMap; }
    public Map<String, Set<String>> getPinyinMap() { return pinyinMap; }
    public Set<String> getAllSurfaces() { return allSurfaces; }
    public Map<Integer, List<String>> getSurfacesByLen() { return surfacesByLen; }

    // ====================================================================
    // ★ 新增：契约构建专用反向查询
    // ====================================================================

    /**
     * 反查：某个意图被哪些 surface 命中。
     * 契约构建时用于填充 IntentContract.surfaces。
     */
    public Set<String> getSurfacesForIntent(String intentCode) {
        if (intentCode == null) return Collections.emptySet();
        Set<String> surfaces = intentToSurfaces.get(intentCode);
        return surfaces != null
            ? Collections.unmodifiableSet(surfaces)
            : Collections.emptySet();
    }

    /**
     * 列出词典里出现过的所有意图编码。
     * 用于契约诊断：哪些意图在词典里没有 surface。
     */
    public Set<String> getIntentCodes() {
        return Collections.unmodifiableSet(intentToSurfaces.keySet());
    }

    /**
     * 取意图的元数据（name / normalized）。
     */
    public IntentMetaInfo getIntentMeta(String intentCode) {
        return intentMetaMap.get(intentCode);
    }

    /**
     * 契约构建器的便捷入口：意图 → surface 映射（全量副本）。
     */
    public Map<String, Set<String>> getIntentToSurfacesMap() {
        Map<String, Set<String>> copy = new HashMap<>();
        intentToSurfaces.forEach((k, v) -> copy.put(k, new HashSet<>(v)));
        return copy;
    }

    /**
     * 诊断：找出"有意图元数据但没有任何 surface"的意图编码。
     */
    public Set<String> getIntentsWithoutSurface() {
        Set<String> orphan = new HashSet<>(intentMetaMap.keySet());
        orphan.removeAll(intentToSurfaces.keySet());
        return orphan;
    }
}

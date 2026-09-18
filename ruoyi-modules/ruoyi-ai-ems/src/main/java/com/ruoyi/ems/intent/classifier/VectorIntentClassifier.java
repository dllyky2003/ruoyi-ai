package com.ruoyi.ems.intent.classifier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.IntentCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 向量意图分类器（第二层）
 * 默认从预计算的 centroids.json 加载质心，避免启动时调用 Embedding 模型
 * 如果质心文件不存在，回退到从 fast_path_routing.json 的 corpus 实时计算
 */
@Slf4j
@Component
public class VectorIntentClassifier implements IntentClassifier {

    /**
     * 质心文件加载路径（支持 classpath: 或 file: 前缀）
     * 默认加载 classpath*:config/acls/../_training/centroids.json
     */
    @Value("${acls.vector.centroid-patterns:classpath*:config/acls/**/_training/centroids.json}")
    private String centroidPatterns;

    /**
     * 回退配置路径（当质心文件不存在时使用）
     */
    @Value("${acls.vector.config-patterns:classpath*:config/acls/**/fast_path_routing.json}")
    private String configPatterns;

    /**
     * 相似度阈值，低于此值视为不匹配
     */
    @Value("${acls.vector.threshold:0.75}")
    private double threshold;

    /**
     * Embedding 模型（仅回退时使用）
     */
    private final EmbeddingModelWrapper embeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 意图质心映射：intentCode -> 向量质心 (float[])
     */
    private Map<String, float[]> centroidMap = new ConcurrentHashMap<>();

    /**
     * 意图名称缓存：intentCode -> nameCn
     */
    private Map<String, String> intentNameMap = new ConcurrentHashMap<>();

    /**
     * 加载来源：centroid_file / corpus_computed / none
     */
    private String loadSource = "none";

    public VectorIntentClassifier(EmbeddingModelWrapper embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    @Override
    public void warmUp()
    {

    }
    @PostConstruct
    public void init() {
        // 1. 优先从质心文件加载
        boolean loaded = loadCentroidsFromJson();

        // 2. 如果质心文件不存在，回退到从 corpus 计算
        if (!loaded) {
            log.warn("质心文件加载失败，回退到从 fast_path_routing.json 计算质心");
            if (embeddingModel != null && embeddingModel.isAvailable()) {
                loadConfigs();
                log.info("向量分类器初始化完成（回退模式），共 {} 个意图质心", centroidMap.size());
            } else {
                log.warn("Embedding 模型不可用，向量分类器将返回 noMatch");
            }
        } else {
            log.info("向量分类器初始化完成（质心文件模式），共 {} 个意图质心", centroidMap.size());
        }
    }

    @PreDestroy
    public void destroy() {
        centroidMap.clear();
        intentNameMap.clear();
    }

    // ================================================================
    //  1. 核心加载方法：从 centroids.json 加载
    // ================================================================

    /**
     * 从预计算的 centroids.json 文件加载质心
     * 支持多个质心文件合并加载
     */
    private boolean loadCentroidsFromJson() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(centroidPatterns);

            if (resources.length == 0) {
                log.info("未找到任何 centroids.json 文件，路径模式: {}", centroidPatterns);
                return false;
            }

            Map<String, float[]> mergedCentroids = new HashMap<>();
            Map<String, String> mergedNames = new HashMap<>();
            int totalDimension = 0;
            int fileCount = 0;

            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                try (InputStream is = resource.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);

                    int dimension = root.has("dimension") ? root.get("dimension").asInt() : 0;
                    JsonNode intentsNode = root.get("intents");
                    if (intentsNode == null || !intentsNode.isObject()) {
                        log.warn("跳过 {}：缺少 intents 字段", resource.getFilename());
                        continue;
                    }

                    JsonNode namesNode = root.get("intent_names");

                    AtomicInteger loadedCount = new AtomicInteger(0);

                    intentsNode.fieldNames().forEachRemaining(intentCode -> {
                        JsonNode vectorNode = intentsNode.get(intentCode);
                        if (vectorNode.isArray() && vectorNode.size() > 0) {
                            int vecSize = vectorNode.size();
                            float[] vector = new float[vecSize];
                            for (int i = 0; i < vecSize; i++) {
                                vector[i] = (float) vectorNode.get(i).asDouble();
                            }
                            mergedCentroids.put(intentCode, vector);
                            loadedCount.incrementAndGet();

                            if (namesNode != null && namesNode.has(intentCode)) {
                                mergedNames.put(intentCode, namesNode.get(intentCode).asText());
                            }
                        }
                    });

                    totalDimension = Math.max(totalDimension, dimension);
                    fileCount++;
                    log.debug("从 {} 加载了 {} 个质心", resource.getFilename(), loadedCount.get());
                } catch (Exception e) {
                    log.warn("解析质心文件失败: {}", resource.getFilename(), e);
                }
            }

            if (mergedCentroids.isEmpty()) {
                log.warn("未从任何质心文件加载到有效质心");
                return false;
            }

            this.centroidMap = mergedCentroids;
            this.intentNameMap = mergedNames;
            this.loadSource = String.format("centroid_file (%d 个文件)", fileCount);

            log.info("✅ 从质心文件加载成功: {} 个意图, {} 维, 来源文件数: {}",
                centroidMap.size(), totalDimension, fileCount);

            return true;

        } catch (IOException e) {
            log.warn("加载质心文件失败: {}", e.getMessage());
            return false;
        }
    }

    // ================================================================
    //  2. 回退方法：从 fast_path_routing.json 的 corpus 计算质心
    // ================================================================

    /**
     * 加载所有 fast_path_routing.json 配置文件，计算意图质心
     * （仅当质心文件不存在时使用）
     */
    private void loadConfigs() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(configPatterns);

            if (resources.length == 0) {
                log.warn("未找到任何 fast_path_routing.json 配置文件");
                return;
            }

            Map<String, float[]> mergedCentroids = new HashMap<>();
            Map<String, String> mergedNames = new HashMap<>();

            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> root = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                    Map<String, Object> intentsMap = extractIntentsMap(root);
                    if (intentsMap == null) {
                        continue;
                    }

                    for (Map.Entry<String, Object> entry : intentsMap.entrySet()) {
                        String intentCode = entry.getKey();
                        Map<String, Object> intentConfig = (Map<String, Object>) entry.getValue();

                        String nameCn = (String) intentConfig.get("name_cn");
                        if (nameCn != null) {
                            mergedNames.put(intentCode, nameCn);
                        }

                        Object vectorObj = intentConfig.get("vector");
                        if (vectorObj instanceof Map) {
                            Map<String, Object> vector = (Map<String, Object>) vectorObj;
                            // 优先尝试读取 centroid_cache（如果有）
                            Object cacheObj = vector.get("centroid_cache");
                            if (cacheObj instanceof List) {
                                float[] centroid = parseCentroidFromList((List<?>) cacheObj);
                                if (centroid != null) {
                                    mergedCentroids.put(intentCode, centroid);
                                    continue;
                                }
                            }

                            // 否则从 corpus 计算
                            List<String> corpus = (List<String>) vector.get("corpus");
                            if (corpus != null && !corpus.isEmpty()) {
                                float[] centroid = computeCentroid(corpus);
                                if (centroid != null) {
                                    mergedCentroids.put(intentCode, centroid);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("解析配置文件失败: {}", resource.getFilename(), e);
                }
            }

            this.centroidMap = mergedCentroids;
            this.intentNameMap = mergedNames;
            this.loadSource = "corpus_computed";

            log.info("✅ 从 fast_path_routing.json 计算质心完成，共 {} 个意图质心", centroidMap.size());

        } catch (IOException e) {
            log.error("加载向量配置文件失败", e);
        }
    }

    /**
     * 从 List 解析质心
     */
    private float[] parseCentroidFromList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Number) {
                result[i] = ((Number) item).floatValue();
            } else {
                return null;
            }
        }
        return result;
    }

    /**
     * 从根节点提取 intents Map
     */
    private Map<String, Object> extractIntentsMap(Map<String, Object> root) {
        Object routingObj = root.get("fast_path_routing");
        if (routingObj instanceof Map) {
            Map<String, Object> routing = (Map<String, Object>) routingObj;
            Object intentsObj = routing.get("intents");
            if (intentsObj instanceof Map) {
                return (Map<String, Object>) intentsObj;
            }
        }

        Object directIntents = root.get("intents");
        if (directIntents instanceof Map) {
            return (Map<String, Object>) directIntents;
        }

        if (!root.isEmpty()) {
            String firstKey = root.keySet().iterator().next();
            if (firstKey.matches("^[GS]\\d.*")) {
                return root;
            }
        }

        return null;
    }

    /**
     * 计算语料库的质心向量（回退方案）
     */
    private float[] computeCentroid(List<String> corpus) {
        if (corpus == null || corpus.isEmpty() || embeddingModel == null) {
            return null;
        }

        try {
            List<float[]> embeddings = new ArrayList<>();
            for (String text : corpus) {
                float[] emb = embeddingModel.embed(text);
                if (emb != null) {
                    embeddings.add(emb);
                }
            }

            if (embeddings.isEmpty()) {
                return null;
            }

            int dim = embeddings.get(0).length;
            float[] centroid = new float[dim];
            for (float[] emb : embeddings) {
                for (int i = 0; i < dim; i++) {
                    centroid[i] += emb[i];
                }
            }
            for (int i = 0; i < dim; i++) {
                centroid[i] /= embeddings.size();
            }

            return normalize(centroid);
        } catch (Exception e) {
            log.error("计算质心失败", e);
            return null;
        }
    }

    /**
     * L2 归一化
     */
    private float[] normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vector;
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return Math.max(0.0, Math.min(1.0, dot));
    }

    // ================================================================
    //  3. 分类方法
    // ================================================================

    @Override
    public String getMode() {
        return "vector";
    }

    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.noMatch(getMode(), "文本为空");
        }

        if (centroidMap.isEmpty()) {
            return IntentCommand.noMatch(getMode(), "未加载任何意图质心");
        }

        if (embeddingModel == null || !embeddingModel.isAvailable()) {
            return IntentCommand.noMatch(getMode(), "Embedding 模型不可用");
        }

        String text = asrText.trim();

        try {
            float[] textEmbedding = embeddingModel.embed(text);
            if (textEmbedding == null) {
                return IntentCommand.noMatch(getMode(), "文本向量化失败");
            }
            textEmbedding = normalize(textEmbedding);

            List<IntentCandidate> candidates = new ArrayList<>();
            for (Map.Entry<String, float[]> entry : centroidMap.entrySet()) {
                String intentCode = entry.getKey();
                float[] centroid = entry.getValue();
                double score = cosineSimilarity(textEmbedding, centroid);
                candidates.add(new IntentCandidate(intentCode, score));
            }

            candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

            if (candidates.isEmpty() || candidates.get(0).getScore() < threshold) {
                return IntentCommand.noMatch(getMode(),
                    String.format("最高相似度 %.3f < 阈值 %.2f",
                        candidates.isEmpty() ? 0 : candidates.get(0).getScore(), threshold));
            }

            IntentCandidate best = candidates.get(0);
            String intentCode = best.getIntentCode();
            String nameCn = intentNameMap.getOrDefault(intentCode, intentCode);

            IntentCommand result = IntentCommand.vectorHit(intentCode, best.getScore(), getMode());
            result.setCandidates(candidates);
            result.setRawText(asrText);
            result.setActType("vector");
            result.setReason(String.format("向量匹配成功 (score: %.3f, 来源: %s)", best.getScore(), loadSource));
            result.setMatchDetail(String.format("向量相似度: %.3f, 意图: %s, 来源: %s",
                best.getScore(), nameCn, loadSource));

            return result;

        } catch (Exception e) {
            log.error("向量分类失败", e);
            return IntentCommand.noMatch(getMode(), "向量分类异常: " + e.getMessage());
        }
    }

    // ================================================================
    //  4. 辅助方法
    // ================================================================

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
        log.info("向量阈值已更新为: {}", threshold);
    }

    public Map<String, float[]> getCentroidMap() {
        return Collections.unmodifiableMap(centroidMap);
    }

    public String getLoadSource() {
        return loadSource;
    }

    /**
     * 刷新配置：优先重新加载质心文件，若失败则回退到计算
     */
    public void refreshConfigs() {
        centroidMap.clear();
        intentNameMap.clear();

        boolean loaded = loadCentroidsFromJson();
        if (!loaded) {
            log.warn("质心文件刷新失败，回退到从 fast_path_routing.json 计算");
            if (embeddingModel != null && embeddingModel.isAvailable()) {
                loadConfigs();
            }
        }

        log.info("向量配置已刷新，共 {} 个意图质心，来源: {}", centroidMap.size(), loadSource);
    }

    /**
     * Embedding 模型包装器接口
     */
    public interface EmbeddingModelWrapper {
        float[] embed(String text);
        boolean isAvailable();
    }
}

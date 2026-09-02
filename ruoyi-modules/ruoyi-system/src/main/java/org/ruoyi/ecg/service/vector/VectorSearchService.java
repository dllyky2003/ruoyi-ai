package org.ruoyi.ecg.service.vector;

import org.ruoyi.ecg.domain.intent.CandidateIntent;
import org.ruoyi.ecg.domain.intent.EcgIntentDefinition;
import org.ruoyi.ecg.domain.intent.EcgUtterancePattern;
import org.ruoyi.ecg.service.BgeM3EmbeddingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 向量检索服务（使用 BGE-M3）
 */
@Service
public class VectorSearchService {

    @Resource
    private BgeM3EmbeddingService embeddingService;

    /** 语料向量缓存（启动时加载） */
    private final Map<String, float[]> corpusCache = new ConcurrentHashMap<>();

    /** 意图语料映射（intentId -> List<语料文本>） */
    private final Map<String, List<String>> intentUtteranceMap = new ConcurrentHashMap<>();

    /**
     * 初始化语料库（由 EcgRuntimeBuilder 调用）
     */
    public void initCorpus(Map<String, List<EcgUtterancePattern>> utteranceMap) {
        corpusCache.clear();
        intentUtteranceMap.clear();

        System.out.println("📝 VectorSearchService.initCorpus 被调用，传入的 utteranceMap 大小: " +
            (utteranceMap != null ? utteranceMap.size() : "null"));

        if (utteranceMap == null || utteranceMap.isEmpty()) {
            System.out.println("⚠️ 语料为空，跳过索引");
            return;
        }

        int total = 0;
        for (Map.Entry<String, List<EcgUtterancePattern>> entry : utteranceMap.entrySet()) {
            String intentId = entry.getKey();
            List<String> texts = new ArrayList<>();

            for (EcgUtterancePattern pattern : entry.getValue()) {
                if (pattern.getIsActive() == null || pattern.getIsActive() == 1) {
                    String text = pattern.getUtterance();
                    if (text != null && !text.trim().isEmpty()) {
                        texts.add(text);
                        // 向量化并缓存
                        float[] vector = embeddingService.embed(text);
                        // key 格式: intentId + "_" + text
                        String key = intentId + "_" + text;
                        corpusCache.put(key, vector);
                        total++;
                    }
                }
            }
            intentUtteranceMap.put(intentId, texts);
        }

        System.out.println("✅ 向量检索服务初始化完成，共索引 " + total + " 条语料");
    }

    /**
     * 从缓存 key 中提取 intentId
     * key 格式: INTENT_XXX_语料内容 → 返回 INTENT_XXX
     * key 格式: INTENT_XXX → 返回 INTENT_XXX
     */
    private String extractIntentId(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        // 找到第二个下划线的位置
        int firstUnderscore = key.indexOf("_");
        if (firstUnderscore == -1) {
            return null;
        }

        int secondUnderscore = key.indexOf("_", firstUnderscore + 1);
        if (secondUnderscore == -1) {
            // 只有一个下划线，返回整个 key
            return key;
        }

        // 返回第二个下划线之前的部分
        return key.substring(0, secondUnderscore);
    }

    /**
     * 向量检索候选意图
     */
    public List<CandidateIntent> search(
            String userInput,
            Map<String, List<EcgUtterancePattern>> utteranceMap,
            Map<String, EcgIntentDefinition> intentMap) {

        // 如果语料缓存为空，先初始化
        if (corpusCache.isEmpty()) {
            initCorpus(utteranceMap);
        }

        // 1. 生成用户输入的向量
        float[] queryVector = embeddingService.embed(userInput);
        if (queryVector == null || queryVector.length == 0) {
            return new ArrayList<>();
        }

        // 2. 计算所有语料的相似度
        List<ScoredUtterance> results = new ArrayList<>();

        for (Map.Entry<String, float[]> entry : corpusCache.entrySet()) {
            String key = entry.getKey();
            float[] corpusVector = entry.getValue();

            float score = cosineSimilarity(queryVector, corpusVector);


            String intentId = extractIntentId(key);
            if (intentId == null) {
                continue;
            }

            // 检查 intentId 是否在 intentMap 中
            if (!intentMap.containsKey(intentId)) {
                // 调试时打印，但不报错
                continue;
            }

            // 查找对应的语料文本
            String utteranceText = null;
            List<String> texts = intentUtteranceMap.get(intentId);
            if (texts != null) {
                for (String text : texts) {
                    if (key.endsWith(text)) {
                        utteranceText = text;
                        break;
                    }
                }
            }

            EcgUtterancePattern pattern = new EcgUtterancePattern();
            pattern.setIntentId(intentId);
            pattern.setUtterance(utteranceText != null ? utteranceText : key);

            UtteranceVector uv = new UtteranceVector(pattern, corpusVector);
            ScoredUtterance su = new ScoredUtterance(uv, score);
            results.add(su);
        }

        // 3. 按相似度排序，取 Top-20
        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        if (results.size() > 20) {
            results = results.subList(0, 20);
        }

        // 4. 按意图聚合，取最高分
        Map<String, Float> intentScoreMap = new HashMap<>();
        Map<String, EcgIntentDefinition> intentDefMap = new HashMap<>();

        for (ScoredUtterance su : results) {
            String intentId = su.getUtterance().getUtterance().getIntentId();
            float score = su.getScore();

            if (!intentScoreMap.containsKey(intentId)
                || score > intentScoreMap.get(intentId)) {
                intentScoreMap.put(intentId, score);

                // 从 intentMap 中获取完整的意图定义
                EcgIntentDefinition def = intentMap.get(intentId);
                if (def != null) {
                    intentDefMap.put(intentId, def);
                }
            }
        }

        // 5. 构建候选列表（只保留有意图定义的）
        List<CandidateIntent> candidates = intentScoreMap.entrySet().stream()
            .filter(entry -> intentDefMap.containsKey(entry.getKey()))
            .map(entry -> {
                CandidateIntent candidate = new CandidateIntent();
                candidate.setIntentId(entry.getKey());
                candidate.setScore(entry.getValue());
                candidate.setIntentDefinition(intentDefMap.get(entry.getKey()));
                return candidate;
            })
            .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
            .limit(5)
            .collect(Collectors.toList());

        System.out.println("🔍 检索结果: 用户输入 '" + userInput + "'，找到 " + candidates.size() + " 个候选意图");
        for (CandidateIntent c : candidates) {
            String desc = c.getIntentDefinition() != null ?
                c.getIntentDefinition().getDescription() : "无描述";
            System.out.println("   - " + c.getIntentId() + " (" + desc + ") 相似度: " + String.format("%.4f", c.getScore()));
        }

        return candidates;
    }

    /**
     * 余弦相似度计算
     */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0f;
        }

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0f;
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 获取语料缓存大小
     */
    public int getCorpusSize() {
        return corpusCache.size();
    }

    /**
     * 清除缓存（用于刷新）
     */
    public void clearCache() {
        corpusCache.clear();
        intentUtteranceMap.clear();
        System.out.println("✅ 向量缓存已清除");
    }
}

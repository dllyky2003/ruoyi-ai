package com.ruoyi.ems.intent.classifier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 关键词意图分类器（第一层）
 * 基于 fast_path_routing.json 中的 keyword.hard 进行硬匹配
 */
@Slf4j
@Component
public class KeywordIntentClassifier implements IntentClassifier {

    /**
     * 配置加载路径，支持 classpath: 或 file: 前缀，也支持目录通配符
     * 默认加载 classpath:config/acls/global/fast_path_routing.json 和 classpath:config/acls/{cases}/{caseType}/fast_path_routing.json
     */
    @Value("${acls.keyword.config-patterns:classpath*:config/acls/**/fast_path_routing.json}")
    private String configPatterns;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 所有意图的关键词配置映射：intentCode -> KeywordConfig
     */
    private Map<String, KeywordConfig> keywordConfigMap = new HashMap<>();

    @PostConstruct
    public void init() {
        loadConfigs();
    }

    @Override
    public void warmUp() {
        // 预热操作（如有需要）
    }

    /**
     * 加载所有匹配的 fast_path_routing.json 配置文件
     */
    private void loadConfigs() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(configPatterns);

            if (resources.length == 0) {
                log.warn("未找到任何 fast_path_routing.json 配置文件，关键词匹配将返回 null");
                return;
            }

            Map<String, KeywordConfig> merged = new HashMap<>();

            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                try (InputStream is = resource.getInputStream()) {
                    // 解析 JSON，结构为 { "fast_path_routing": { "intents": { ... } } } 或直接 { "intents": { ... } }
                    Map<String, Object> root = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                    Object intentsObj = root.get("fast_path_routing");
                    if (intentsObj instanceof Map) {
                        Map<String, Object> routing = (Map<String, Object>) intentsObj;
                        Object intentsMap = routing.get("intents");
                        if (intentsMap instanceof Map) {
                            Map<String, Object> intents = (Map<String, Object>) intentsMap;
                            for (Map.Entry<String, Object> entry : intents.entrySet()) {
                                String intentCode = entry.getKey();
                                Map<String, Object> intentConfig = (Map<String, Object>) entry.getValue();
                                Object keywordObj = intentConfig.get("keyword");
                                if (keywordObj instanceof Map) {
                                    Map<String, Object> kw = (Map<String, Object>) keywordObj;
                                    List<String> hard = (List<String>) kw.get("hard");
                                    List<String> exclude = (List<String>) kw.get("exclude");
                                    if (hard != null && !hard.isEmpty()) {
                                        KeywordConfig config = new KeywordConfig();
                                        config.setHard(hard);
                                        config.setExclude(exclude != null ? exclude : Collections.emptyList());
                                        // 如果有 priority 则设置
                                        Object priorityObj = kw.get("priority");
                                        if (priorityObj instanceof Integer) {
                                            config.setPriority((Integer) priorityObj);
                                        }
                                        // 合并策略：如果已存在同一 intentCode，以最后加载的为准（但通常不会重复）
                                        merged.put(intentCode, config);
                                    }
                                }
                            }
                        }
                    } else {
                        // 兼容直接 { "intents": { ... } } 格式
                        Object directIntents = root.get("intents");
                        if (directIntents instanceof Map) {
                            Map<String, Object> intents = (Map<String, Object>) directIntents;
                            for (Map.Entry<String, Object> entry : intents.entrySet()) {
                                String intentCode = entry.getKey();
                                Map<String, Object> intentConfig = (Map<String, Object>) entry.getValue();
                                Object keywordObj = intentConfig.get("keyword");
                                if (keywordObj instanceof Map) {
                                    Map<String, Object> kw = (Map<String, Object>) keywordObj;
                                    List<String> hard = (List<String>) kw.get("hard");
                                    List<String> exclude = (List<String>) kw.get("exclude");
                                    if (hard != null && !hard.isEmpty()) {
                                        KeywordConfig config = new KeywordConfig();
                                        config.setHard(hard);
                                        config.setExclude(exclude != null ? exclude : Collections.emptyList());
                                        merged.put(intentCode, config);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("解析配置文件失败: {}", resource.getFilename(), e);
                }
            }

            this.keywordConfigMap = merged;
            log.info("关键词配置加载完成，共 {} 个意图", keywordConfigMap.size());
            log.debug("配置内容: {}", keywordConfigMap.keySet());

        } catch (IOException e) {
            log.error("加载关键词配置文件失败", e);
            this.keywordConfigMap = Collections.emptyMap();
        }
    }

    @Override
    public String getMode() {
        return "keyword";
    }

    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty() || keywordConfigMap.isEmpty()) {
            return IntentCommand.noMatch(getMode());
        }

        String text = asrText.trim();

        // ============================================================
        // ★ 改造：收集所有命中，按 (priority 降序, 关键词长度降序) 排序
        //   不再"先命中就返回"，避免 HashMap 遍历顺序不确定
        // ============================================================
        List<KeywordHit> hits = new ArrayList<>();

        for (Map.Entry<String, KeywordConfig> entry : keywordConfigMap.entrySet()) {
            String intentCode = entry.getKey();
            KeywordConfig config = entry.getValue();

            // 1. 检查排除词（不变）
            boolean excluded = false;
            for (String ex : config.getExclude()) {
                if (text.contains(ex)) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) {
                continue;
            }

            // 2. 检查硬关键词：同一意图只记录最长命中的那条
            String longestHit = null;
            for (String hard : config.getHard()) {
                if (text.contains(hard)) {
                    if (longestHit == null || hard.length() > longestHit.length()) {
                        longestHit = hard;
                    }
                }
            }
            if (longestHit != null) {
                int priority = config.getPriority() != null ? config.getPriority() : 0;
                hits.add(new KeywordHit(intentCode, longestHit, priority));
            }
        }

        if (hits.isEmpty()) {
            return IntentCommand.noMatch(getMode());
        }

        // 3. 排序：priority 降序 → 关键词长度降序 → intentCode 字典序（保证稳定）
        hits.sort((a, b) -> {
            int byPriority = Integer.compare(b.priority, a.priority);
            if (byPriority != 0) return byPriority;
            int byLength = Integer.compare(b.keyword.length(), a.keyword.length());
            if (byLength != 0) return byLength;
            return a.intentCode.compareTo(b.intentCode);
        });

        KeywordHit best = hits.get(0);

        if (log.isDebugEnabled()) {
            log.debug("Keyword 命中 {} 条，Top3: {}",
                hits.size(),
                hits.stream().limit(3)
                    .map(h -> h.intentCode + "(" + h.keyword + ", prio=" + h.priority + ")")
                    .collect(java.util.stream.Collectors.joining(", ")));
        }

        return IntentCommand.keywordHit(best.intentCode, best.keyword, getMode());
    }

    /**
     * 内部类：一条关键词命中记录。
     */
    private static class KeywordHit {
        final String intentCode;
        final String keyword;
        final int priority;

        KeywordHit(String intentCode, String keyword, int priority) {
            this.intentCode = intentCode;
            this.keyword = keyword;
            this.priority = priority;
        }
    }

    /**
     * 供外部调用获取当前加载的配置（用于调试）
     */
    public Map<String, KeywordConfig> getKeywordConfigMap() {
        return Collections.unmodifiableMap(keywordConfigMap);
    }

    /**
     * 刷新配置（如热更新时使用）
     */
    public void refreshConfigs() {
        loadConfigs();
    }

    // ==================== 内部类 KeywordConfig ====================

    /**
     * 关键词配置模型
     */
    public static class KeywordConfig {
        private List<String> hard = new ArrayList<>();
        private List<String> exclude = new ArrayList<>();
        private Integer priority = 0;

        public List<String> getHard() {
            return hard;
        }

        public void setHard(List<String> hard) {
            this.hard = hard;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public void setExclude(List<String> exclude) {
            this.exclude = exclude;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }
    }
}

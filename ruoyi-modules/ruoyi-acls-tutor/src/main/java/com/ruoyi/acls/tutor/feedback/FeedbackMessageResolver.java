package com.ruoyi.acls.tutor.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反馈文案解析器。
 *
 * <p>加载顺序（后者覆盖前者同名 key）：</p>
 * <ol>
 *   <li>classpath:config/acls/feedback/feedback_messages.yaml（全局）</li>
 *   <li>classpath:config/acls/cases/{caseId}/feedback_messages.yaml（案例专属）</li>
 * </ol>
 *
 * <p>找不到 key 时返回 fallback（调用方指定），fallback 也为空时返回通用文案。</p>
 */
@Slf4j
@Component
public class FeedbackMessageResolver {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String DEFAULT_FALLBACK = "当前临床流程不允许此操作。";

    /** key -> 文案 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            // 1. 先加载全局（先加载，会被后者覆盖）
            loadAll(resolver.getResources(
                "classpath*:config/acls/feedback/feedback_messages.yaml"), "GLOBAL");

            // 2. 再加载案例专属（后加载，覆盖全局同名 key）
            loadAll(resolver.getResources(
                "classpath*:config/acls/cases/*/feedback_messages.yaml"), "CASE");

            log.info("[Tutor][FeedbackResolver] 已加载反馈文案 {} 条", cache.size());
        } catch (Exception e) {
            log.warn("[Tutor][FeedbackResolver] 加载失败（不影响 BLOCK 阻断）: {}", e.getMessage(), e);
        }
    }

    private void loadAll(Resource[] resources, String scope) {
        for (Resource r : resources) {
            try (InputStream is = r.getInputStream()) {
                MessageFile file = YAML.readValue(is, MessageFile.class);
                if (file == null || file.getMessages() == null) {
                    continue;
                }
                for (FeedbackMessage msg : file.getMessages()) {
                    if (msg == null || msg.getKey() == null || msg.getKey().isBlank()) {
                        continue;
                    }
                    String prev = cache.put(msg.getKey(),
                        msg.getText() != null ? msg.getText() : "");
                    if (prev != null) {
                        log.info("[Tutor][FeedbackResolver] {} 覆盖 key={}: '{}' → '{}'",
                            scope, msg.getKey(), prev, msg.getText());
                    }
                }
                log.info("[Tutor][FeedbackResolver] 加载文件: scope={}, file={}, count={}",
                    scope, r.getFilename(), file.getMessages().size());
            } catch (Exception e) {
                log.warn("[Tutor][FeedbackResolver] 解析文件失败: file={}, err={}",
                    r.getFilename(), e.getMessage());
            }
        }
    }

    /**
     * 解析 key 到文案。
     *
     * @param key      反馈 key
     * @param fallback 找不到时的兜底（可为 null）
     * @return 文案，永不为 null
     */
    public String resolve(String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback != null && !fallback.isBlank() ? fallback : DEFAULT_FALLBACK;
        }
        String text = cache.get(key);
        if (text != null && !text.isBlank()) {
            return text;
        }
        log.debug("[Tutor][FeedbackResolver] key 未命中，回退: key={}, fallback={}", key, fallback);
        return fallback != null && !fallback.isBlank() ? fallback : DEFAULT_FALLBACK;
    }

    /** YAML 根 */
    @Data
    public static class MessageFile {
        @JsonProperty("version")
        private String version;

        @JsonProperty("messages")
        private List<FeedbackMessage> messages;
    }

    /** 单条文案 */
    @Data
    public static class FeedbackMessage {
        @JsonProperty("key")
        private String key;

        @JsonProperty("text")
        private String text;

        @JsonProperty("severity")
        private String severity;
    }
}

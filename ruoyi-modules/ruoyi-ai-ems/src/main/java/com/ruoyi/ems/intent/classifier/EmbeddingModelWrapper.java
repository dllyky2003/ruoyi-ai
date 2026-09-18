package com.ruoyi.ems.intent.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embedding 模型包装器 —— 走 Ollama /api/embeddings。
 *
 * <p>启动时同步探测一次，验证：
 * <ul>
 *   <li>Ollama 服务可达</li>
 *   <li>模型已加载</li>
 *   <li>返回向量维度与期望一致</li>
 * </ul>
 *
 * <p>探测失败 → {@link #isAvailable()} 返回 false → 向量层自动跳过
 * （行为与改造前一致，不会阻断整个流程）。
 * 探测成功 → 每次 {@link #embed(String)} 都走真实调用。
 */
@Slf4j
@Component
public class EmbeddingModelWrapper implements VectorIntentClassifier.EmbeddingModelWrapper {

    @Value("${acls.embedding.enabled:false}")
    private boolean enabled;

    @Value("${acls.embedding.provider:ollama}")
    private String provider;

    @Value("${acls.embedding.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${acls.embedding.model:bge-m3}")
    private String model;

    @Value("${acls.embedding.dimension:1024}")
    private int expectedDimension;

    @Value("${acls.embedding.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${acls.embedding.read-timeout-ms:10000}")
    private int readTimeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestClient restClient;
    private volatile boolean available = false;

    public EmbeddingModelWrapper() {
        // Spring 会注入 @Value 字段，构造器无需做事
    }

    /**
     * 启动探测。失败不影响应用启动，只是向量层不工作。
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[Embedding] 未启用（acls.embedding.enabled=false），向量层将跳过");
            return;
        }
        if (!"ollama".equalsIgnoreCase(provider)) {
            log.warn("[Embedding] 不支持的 provider: {}，仅支持 ollama，向量层跳过", provider);
            return;
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .baseUrl(baseUrl)
            .build();

        try {
            long t0 = System.currentTimeMillis();
            float[] probe = doEmbed("ping");
            long cost = System.currentTimeMillis() - t0;

            if (probe == null) {
                log.warn("[Embedding] 探测失败：返回空向量，向量层跳过");
                return;
            }
            if (probe.length != expectedDimension) {
                log.error("[Embedding] ❌ 维度不匹配: expected={}, actual={} —— 请确认模型与质心生成时一致",
                    expectedDimension, probe.length);
                return;
            }
            this.available = true;
            log.info("[Embedding] ✅ 就绪: provider={}, model={}, dim={}, baseUrl={}, 探测耗时={}ms",
                provider, model, probe.length, baseUrl, cost);
        } catch (Exception e) {
            log.warn("[Embedding] 探测异常: {} —— 向量层跳过", e.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        if (!available || text == null || text.isEmpty()) {
            return null;
        }
        try {
            return doEmbed(text);
        } catch (Exception e) {
            log.warn("[Embedding] 调用失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * 真实调用 Ollama /api/embeddings。
     *
     * <p>请求：{@code {"model":"bge-m3","prompt":"要编码的文本"}}
     * <p>响应：{@code {"embedding":[0.1, 0.2, ...]}}
     */
    private float[] doEmbed(String text) throws Exception {
        // 用 LinkedHashMap 保持字段顺序，某些服务端对顺序敏感（Ollama 不敏感，但稳妥起见）
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("prompt", text);

        String respJson = restClient.post()
            .uri("/api/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(req)
            .retrieve()
            .body(String.class);

        if (respJson == null || respJson.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(respJson);
        JsonNode embNode = root.get("embedding");
        if (embNode == null || !embNode.isArray()) {
            log.warn("[Embedding] 响应缺 embedding 字段: {}",
                respJson.length() > 200 ? respJson.substring(0, 200) + "..." : respJson);
            return null;
        }

        int n = embNode.size();
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (float) embNode.get(i).asDouble();
        }
        return out;
    }
}

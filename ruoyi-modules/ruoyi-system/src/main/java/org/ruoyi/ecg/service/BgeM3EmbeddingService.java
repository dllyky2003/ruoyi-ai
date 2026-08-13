package org.ruoyi.ecg.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BgeM3EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(BgeM3EmbeddingService.class);

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${spring.ai.ollama.embedding.model:nomic-embed-text}")
    private String modelName;

    public static final int VECTOR_DIM = 1024;

    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
//        this.httpClient = HttpClient.newBuilder()
//                .connectTimeout(Duration.ofSeconds(10))
//                .build();
//        this.objectMapper = new ObjectMapper();
//        log.info("向量化服务初始化完成，Ollama地址: {}, 模型: {}, 向量维度: {}", baseUrl, modelName, VECTOR_DIM);
//
//        // 预热
//        try {
//            embed("预热");
//            log.info("模型预热完成");
//        } catch (Exception e) {
//            log.warn("模型预热失败，请确认 Ollama 服务是否正常运行: {}", e.getMessage());
//        }
    }

    /**
     * 单条文本向量化
     */
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[VECTOR_DIM];
        }

        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("input", text);

            String jsonRequest = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embed"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Ollama 服务调用失败: {} - {}", response.statusCode(), response.body());
                return new float[VECTOR_DIM];
            }

            // 解析响应
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            List<List<Double>> embeddings = (List<List<Double>>) result.get("embeddings");

            if (embeddings == null || embeddings.isEmpty()) {
                log.warn("Ollama 返回空向量");
                return new float[VECTOR_DIM];
            }

            List<Double> embeddingList = embeddings.get(0);
            float[] resultArray = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                resultArray[i] = embeddingList.get(i).floatValue();
            }
            return resultArray;

        } catch (Exception e) {
            log.error("向量化失败: {}", text, e);
            return new float[VECTOR_DIM];
        }
    }

    /**
     * 批量文本向量化
     */
    @SuppressWarnings("unchecked")
    public float[][] embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new float[0][VECTOR_DIM];
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("input", texts);

            String jsonRequest = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embed"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Ollama 批量调用失败: {}", response.statusCode());
                return new float[texts.size()][VECTOR_DIM];
            }

            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            List<List<Double>> embeddings = (List<List<Double>>) result.get("embeddings");

            if (embeddings == null || embeddings.isEmpty()) {
                return new float[texts.size()][VECTOR_DIM];
            }

            List<float[]> results = new ArrayList<>();
            for (List<Double> embeddingList : embeddings) {
                float[] arr = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    arr[i] = embeddingList.get(i).floatValue();
                }
                results.add(arr);
            }
            return results.toArray(new float[0][]);

        } catch (Exception e) {
            log.error("批量向量化失败", e);
            return new float[texts.size()][VECTOR_DIM];
        }
    }

    public int getVectorDim() {
        return VECTOR_DIM;
    }


}

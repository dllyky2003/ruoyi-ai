package com.ruoyi.ems.intent.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import com.ruoyi.ems.domain.IntentCommand;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ParameterExtractor {

    private final ChatModel chatModel;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParameterExtractor(@Qualifier("throttledExtractionChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 对给定的意图结果进行参数抽取
     * @param intentResult 意图分类结果（必须包含 matchedDefinition 和 rawText）
     * @return 抽取到的参数 Map，所有值为 null 的条目已被过滤
     */
    public Map<String, Object> extract(IntentCommand intentResult) {
        if (intentResult == null || !intentResult.isValid()) {
            log.warn("意图无效，跳过参数抽取");
            return Collections.emptyMap();
        }

        // 检查是否有参数抽取规则
        if (intentResult.getMatchedDefinition() == null
            || intentResult.getMatchedDefinition().getExtraction() == null) {
            log.info("意图 {} 无需参数抽取", intentResult.getIntentCode());
            return Collections.emptyMap();
        }

        // 如果已有参数（可能来自缓存或静态预填），直接返回（但也要过滤 null）
        if (intentResult.getSlots() != null && !intentResult.getSlots().isEmpty()) {
            Map<String, Object> existingParams = intentResult.getSlots();
            Map<String, Object> cleaned = removeNullValues(existingParams);
            log.info("意图 {} 已有参数，过滤后返回 {} 个有效参数",
                intentResult.getIntentCode(), cleaned.size());
            return cleaned;
        }

        try {
            log.info("开始参数抽取，意图码: {}", intentResult.getIntentCode());

            // 1. 从 IntentResult 中获取 Prompt 对
            Map<String, String> promptPair = intentResult.buildExtractionPromptPair();
            if (promptPair == null || promptPair.isEmpty()) {
                log.warn("意图 {} 无有效的抽取提示词", intentResult.getIntentCode());
                return Collections.emptyMap();
            }

            String systemPrompt = promptPair.get("system");
            String userPrompt = promptPair.get("user");
            List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
            );
            ChatResponse response = chatModel.chat(messages);
            String llmResponse = response.aiMessage().text();

            // 2. 解析 JSON
            Map<String, Object> extractedParams = parseJsonToMap(llmResponse);
            if (extractedParams == null) {
                extractedParams = Collections.emptyMap();
            }
            // 3. 【核心修复】递归过滤 null 值，防止后续 ConcurrentHashMap NPE
            Map<String, Object> cleanedParams = removeNullValues(extractedParams);

            log.info("参数抽取完成，原始 {} 个字段，过滤后 {} 个有效参数",
                extractedParams.size(), cleanedParams.size());

            // 4. 将过滤后的参数回写到 IntentResult（可选，方便缓存）
            intentResult.setSlots(cleanedParams);

            return cleanedParams;

        } catch (Exception e) {
            log.error("参数抽取失败，意图码: {}", intentResult.getIntentCode(), e);
            // 保证返回的 Map 为空且安全
            intentResult.setSlots(Collections.emptyMap());
            return Collections.emptyMap();
        }
    }

    /**
     * 递归移除 Map 中所有值为 null 的条目（包括嵌套 Map/List 中的 null）
     * @param input 原始 Map
     * @return 清理后的 Map（不含 null 值），永不返回 null
     */
    private Map<String, Object> removeNullValues(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 跳过 null 值
            if (value == null) {
                log.debug("跳过 null 参数: {}", key);
                continue;
            }

            // 处理嵌套 Map
            if (value instanceof Map<?, ?> nestedMap) {
                // 递归清理嵌套 Map
                Map<String, Object> cleanedNested = removeNullValues((Map<String, Object>) nestedMap);
                if (!cleanedNested.isEmpty()) {
                    cleaned.put(key, cleanedNested);
                }
            }
            // 处理 List（移除 List 中的 null 元素以及 Map 中嵌套的 null）
            else if (value instanceof List<?> list) {
                List<Object> cleanedList = new ArrayList<>();
                for (Object item : list) {
                    if (item == null) {
                        continue;
                    }
                    // 如果列表元素是 Map，递归清理
                    if (item instanceof Map) {
                        Map<String, Object> cleanedItem = removeNullValues((Map<String, Object>) item);
                        if (!cleanedItem.isEmpty()) {
                            cleanedList.add(cleanedItem);
                        }
                    } else {
                        cleanedList.add(item);
                    }
                }
                if (!cleanedList.isEmpty()) {
                    cleaned.put(key, cleanedList);
                }
            }
            // 普通非 null 值直接保留
            else {
                cleaned.put(key, value);
            }
        }
        return cleaned;
    }

    /**
     * 解析 LLM 返回的 JSON 字符串为 Map
     */
    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String cleaned = json.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
            return MAPPER.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", json, e);
            return Collections.emptyMap();
        }
    }
}

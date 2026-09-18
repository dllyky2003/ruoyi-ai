package com.ruoyi.ems.intent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型客户端（适配 RuoYi ChatServiceFactory）
 * 负责调用统一模型服务进行意图分类，支持 Qwen2.5 等任意已注册模型
 *
 * 关键约束：
 *   1. 强制 JSON 输出（system prompt 尾部追加硬约束）
 *   2. 校验 + 重试（最多 2 次）
 *   3. Ollama provider 优先走 format=json（见 buildChatModel 建议）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    /** JSON 硬约束，追加在 system prompt 末尾 */
    private static final String JSON_ENFORCEMENT =
        "\n\n【输出格式硬性要求】\n" +
            "1. 你必须且只能输出一个合法的 JSON 对象，不要输出任何解释、寒暄、markdown 代码块标记。\n" +
            "2. 第一个字符必须是 '{'，最后一个字符必须是 '}'。\n" +
            "3. 如果无法判断，输出 {\"intent\":null,\"confidence\":0}。\n" +
            "4. 禁止输出 ```json 或 ``` 等包裹符号。\n";

    /** 最大重试次数（含首次） */
    private static final int MAX_ATTEMPTS = 2;

    private final IChatModelService chatModelService;
    private final ChatServiceFactory chatServiceFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${acls.classifier.model-name:qwen2.5:latest}")
    private String modelName;

    /**
     * 调用大模型进行推理（同步），带 JSON 校验与重试
     */
    public String chat(String systemPrompt, String userPrompt) {
        String enforcedSystem = enforceJson(systemPrompt);
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String raw = doChat(enforcedSystem, userPrompt, attempt);
                String json = extractJson(raw);

                if (isValidJson(json)) {
                    return json;
                }

                log.warn("第 {} 次调用返回非法 JSON，将重试。原始内容: {}", attempt, truncate(raw, 300));
                // 第二次时，把模型上次的错误输出回灌，强制纠偏
                userPrompt = userPrompt + "\n\n【纠正】你上次输出不是合法 JSON，请严格只输出 JSON 对象。";

            } catch (Exception e) {
                lastError = new RuntimeException("调用大模型失败: " + e.getMessage(), e);
                log.warn("第 {} 次调用异常: {}", attempt, e.getMessage());
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        log.error("模型连续 {} 次返回非 JSON，降级返回 {}", MAX_ATTEMPTS, "{}");
        return "{}";
    }

    /**
     * 单次底层调用
     */
    private String doChat(String systemPrompt, String userPrompt, int attempt) {
        log.debug("调用大模型[第{}次]，模型={}, system={}, user={}",
            attempt, modelName, len(systemPrompt), len(userPrompt));

        long start = System.currentTimeMillis();

        ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
        if (modelVo == null) {
            throw new IllegalArgumentException("模型不存在或未注册: " + modelName);
        }

        AbstractChatService chatService = chatServiceFactory.getOriginalService(modelVo.getProviderCode());
        ChatModel chatModel = chatService.buildChatModel(modelVo);

        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userPrompt));

        ChatResponse response = chatModel.chat(messages);
        String raw = response.aiMessage().text();

        log.debug("大模型响应耗时: {}ms，原始长度: {}",
            System.currentTimeMillis() - start, len(raw));
        return raw;
    }

    /**
     * 在 system prompt 末尾追加 JSON 硬约束；若已存在则不重复追加
     */
    private String enforceJson(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            return JSON_ENFORCEMENT.trim();
        }
        if (systemPrompt.contains("【输出格式硬性要求】")) {
            return systemPrompt;
        }
        return systemPrompt + JSON_ENFORCEMENT;
    }

    /**
     * 判断字符串是否是合法 JSON 对象
     */
    private boolean isValidJson(String s) {
        if (s == null || s.isEmpty() || "{}".equals(s)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(s);
            return node != null && node.isObject() && node.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 LLM 原始响应中提取纯 JSON
     * 处理：```json 包裹、前后解释、直接 JSON、多行夹带
     */
    private String extractJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "{}";
        }

        String trimmed = content.trim();

        // 去掉 markdown 代码块标记
        trimmed = trimmed.replaceAll("(?i)```json\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();

        // 直接以 { 开头
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        // 首尾花括号截取
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        log.warn("无法从响应中提取 JSON，原始内容: {}", truncate(content, 300));
        return "{}";
    }

    private int len(String s) {
        return s == null ? 0 : s.length();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

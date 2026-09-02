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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private final IChatModelService chatModelService;
    private final ChatServiceFactory chatServiceFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从配置文件读取：acls.classifier.model-name
     * 默认使用 qwen2.5:latest（需确保已在系统模型表中注册）
     */
    @Value("${acls.classifier.model-name:qwen2.5:latest}")
    private String modelName;

    /**
     * 调用大模型进行推理（同步）
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM 输出的 JSON 字符串（已清理 Markdown 标记）
     */
    public String chat(String systemPrompt, String userPrompt) {
        try {
            log.debug("调用大模型，模型名称: {}, 系统提示长度: {}, 用户提示长度: {}",
                modelName, systemPrompt.length(), userPrompt.length());

            long startTime = System.currentTimeMillis();

            // 1. 根据模型名称获取模型配置
            ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
            if (modelVo == null) {
                throw new IllegalArgumentException("模型不存在或未注册: " + modelName);
            }

            // 2. 获取对应的服务实现（如 Ollama、OpenAI 等）
            AbstractChatService chatService = chatServiceFactory.getOriginalService(modelVo.getProviderCode());

            // 3. 构建 ChatModel 实例
            ChatModel chatModel = chatService.buildChatModel(modelVo);

            // 4. 组装消息（ChatML 格式）
            List<ChatMessage> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                messages.add(SystemMessage.from(systemPrompt));
            }
            messages.add(UserMessage.from(userPrompt));

            // 5. 调用模型
            ChatResponse response = chatModel.chat(messages);
            String rawContent = response.aiMessage().text();

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("大模型响应耗时: {}ms", elapsed);

            // 6. 清理并提取纯 JSON（兼容 Markdown 包裹）
            String jsonContent = extractJson(rawContent);
            log.debug("清理后的响应: {}", jsonContent);

            return jsonContent;

        } catch (Exception e) {
            log.error("调用大模型失败，模型名称: {}", modelName, e);
            throw new RuntimeException("调用大模型失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 LLM 原始响应中提取纯 JSON
     * 处理情况：
     * 1. 被 ```json ... ``` 包裹
     * 2. 前后有额外解释文字
     * 3. 直接就是 JSON
     */
    private String extractJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "{}";
        }

        String trimmed = content.trim();

        // 去掉 markdown 代码块标记（不区分大小写）
        trimmed = trimmed.replaceAll("(?i)```json\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();

        // 如果以 { 开头，直接返回
        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        // 尝试提取第一个 { 到最后一个 }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        // 如果仍然不是 JSON，尝试按行提取包含 { 的行
        String[] lines = trimmed.split("\n");
        for (String line : lines) {
            String l = line.trim();
            if (l.startsWith("{") && l.endsWith("}")) {
                return l;
            }
        }

        log.warn("无法从响应中提取 JSON，原始内容: {}", content);
        return "{}";
    }
}

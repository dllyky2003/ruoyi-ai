package com.ruoyi.ems.engine.multiagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Getter
@Setter
public abstract class BaseAgent implements Agent {

    protected String name;
    protected String description;
    protected ChatModel chatModel;          // LangChain4j 的 ChatModel
    protected Double temperature;
    protected ObjectMapper objectMapper = new ObjectMapper();

    protected BaseAgent(String name, String description, ChatModel chatModel, Double temperature) {
        this.name = name;
        this.description = description;
        this.chatModel = chatModel;
        this.temperature = temperature != null ? temperature : 0.1;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        try {
            long startTime = System.currentTimeMillis();
            log.info("智能体 [{}] 开始分析", name);

            String userPrompt = buildPrompt(input);

            // 使用 LangChain4j 的消息列表
            List<ChatMessage> messages = List.of(
                SystemMessage.from("你是一个专业的" + description + "，请严格按JSON格式输出结果。"),
                UserMessage.from(userPrompt)
            );

            // 调用 LangChain4j 的 chat 方法
            ChatResponse response = chatModel.chat(messages);
            String content = response.aiMessage().text();

            AgentOutput output = parseResponse(content);
            output.setAgentName(name);
            output.setTrace(buildTrace(input, content, startTime));

            log.info("智能体 [{}] 完成分析，得分: {}", name, output.getScore());
            return output;

        } catch (Exception e) {
            log.error("智能体 [{}] 执行失败", name, e);
            return buildFallbackOutput(e.getMessage());
        }
    }

    protected abstract String buildPrompt(AgentInput input);
    protected abstract AgentOutput parseResponse(String response);

    protected String buildTrace(AgentInput input, String response, long startTime) {
        return String.format("""
                【智能体】%s
                【开始时间】%s
                【耗时】%d ms
                【输入摘要】会话:%s, 操作数:%d
                【LLM原始响应】%s
                """,
            name,
            LocalDateTime.now(),
            System.currentTimeMillis() - startTime,
            input.getSessionId(),
            input.getPerformance() != null && input.getPerformance().getActions() != null ?
                input.getPerformance().getActions().size() : 0,
            response.length() > 500 ? response.substring(0, 500) + "..." : response
        );
    }

    protected AgentOutput buildFallbackOutput(String error) {
        return AgentOutput.builder()
            .agentName(name)
            .score(50)
            .reasoning("智能体执行异常，使用降级评分: " + error)
            .hasCriticalViolation(false)
            .warnings(List.of("智能体降级"))
            .recommendations(List.of("请检查LLM服务状态"))
            .build();
    }

    protected <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}

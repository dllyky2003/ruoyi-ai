package com.ruoyi.ems.engine.multiagent.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.ems.engine.multiagent.BaseAgent;
import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import com.ruoyi.ems.engine.multiagent.prompts.FeedbackPrompt;

import dev.langchain4j.model.chat.ChatModel;   // ✅ 改为 LangChain4j 的 ChatModel
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class FeedbackAgent extends BaseAgent {

    @Builder
    public FeedbackAgent(String name, ChatModel chatModel, Double temperature) {   // ✅ 参数类型改为 ChatModel
        super(name, "教学反馈智能体", chatModel, temperature);
    }

    @Override
    protected String buildPrompt(AgentInput input) {
        // 根据实际 Prompt 构造
        String actionsJson = toJson(input.getPerformance().getActions());
        return String.format(FeedbackPrompt.SYSTEM, actionsJson);
    }

    @Override
    protected AgentOutput parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            int score = root.has("feedbackScore") ? root.get("feedbackScore").asInt() : 75;
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "反馈生成完成";
            List<String> suggestions = new ArrayList<>();
            if (root.has("suggestions")) {
                root.get("suggestions").forEach(n -> suggestions.add(n.asText()));
            }
            String summary = root.has("summary") ? root.get("summary").asText() : "";

            return AgentOutput.builder()
                .agentName(name)
                .score(score)
                .reasoning(reasoning)
                .detailedFindings(Map.of("summary", summary, "suggestions", suggestions))
                .warnings(List.of())
                .recommendations(suggestions)
                .hasCriticalViolation(false)
                .build();

        } catch (Exception e) {
            log.error("解析FeedbackAgent响应失败", e);
            return buildFallbackOutput(e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

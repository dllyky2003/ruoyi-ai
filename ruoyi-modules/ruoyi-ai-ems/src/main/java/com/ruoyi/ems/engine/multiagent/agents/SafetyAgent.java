package com.ruoyi.ems.engine.multiagent.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.ems.engine.multiagent.BaseAgent;
import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import com.ruoyi.ems.engine.multiagent.prompts.SafetyPrompt;

import dev.langchain4j.model.chat.ChatModel;   // ✅ 改为 LangChain4j 的 ChatModel
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class SafetyAgent extends BaseAgent {

    @Builder
    public SafetyAgent(String name, ChatModel chatModel, Double temperature) {   // ✅ 参数类型改为 ChatModel
        super(name, "安全稽查智能体", chatModel, temperature);
    }

    @Override
    protected String buildPrompt(AgentInput input) {
        // 根据实际 Prompt 类构造，示例：
        String actionsJson = toJson(input.getPerformance().getActions());
        // 如果有其他参数，如风险事件等
        return String.format(SafetyPrompt.SYSTEM, actionsJson);
    }

    @Override
    protected AgentOutput parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // 示例解析逻辑，根据实际 JSON 结构调整
            int score = root.has("safetyScore") ? root.get("safetyScore").asInt() : 80;
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "安全审查完成";
            List<String> violations = new ArrayList<>();
            if (root.has("violations")) {
                root.get("violations").forEach(n -> violations.add(n.asText()));
            }
            boolean hasCritical = root.has("hasCriticalViolation") && root.get("hasCriticalViolation").asBoolean();

            return AgentOutput.builder()
                .agentName(name)
                .score(score)
                .reasoning(reasoning)
                .detailedFindings(Map.of("violations", violations))
                .warnings(violations.isEmpty() ? List.of() : violations)
                .recommendations(violations.isEmpty() ?
                    List.of("无安全违规") :
                    List.of("请纠正以下违规操作：" + String.join(", ", violations)))
                .hasCriticalViolation(hasCritical)
                .criticalViolations(hasCritical ? List.of("存在严重安全违规") : null)
                .build();

        } catch (Exception e) {
            log.error("解析SafetyAgent响应失败", e);
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

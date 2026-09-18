package com.ruoyi.ems.engine.multiagent.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.ems.engine.multiagent.BaseAgent;
import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import com.ruoyi.ems.engine.multiagent.prompts.DecisionPrompt;

import dev.langchain4j.model.chat.ChatModel;   // ✅ 改为 LangChain4j 的 ChatModel
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class DecisionAgent extends BaseAgent {

    @Builder
    public DecisionAgent(String name, ChatModel chatModel, Double temperature) {   // ✅ 参数类型改为 ChatModel
        super(name, "路径决策智能体", chatModel, temperature);
    }

    @Override
    protected String buildPrompt(AgentInput input) {
        String actionsJson = toJson(input.getPerformance().getActions());
        String tracesJson = toJson(input.getTraces());
        String configJson = toJson(input.getScenarioConfig());
        return String.format(DecisionPrompt.SYSTEM, actionsJson, tracesJson, configJson);
    }

    @Override
    protected AgentOutput parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            boolean pathCorrect = root.has("pathCorrect") && root.get("pathCorrect").asBoolean();
            int score = root.has("sequenceScore") ? root.get("sequenceScore").asInt() : 70;
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "路径分析完成";
            String currentPos = root.has("currentPosition") ? root.get("currentPosition").asText() : "UNKNOWN";
            String nextExpected = root.has("nextExpected") ? root.get("nextExpected").asText() : null;

            List<String> errors = new ArrayList<>();
            if (root.has("sequenceErrors")) {
                root.get("sequenceErrors").forEach(n -> {
                    String step = n.has("step") ? n.get("step").asText() : "unknown";
                    String status = n.has("status") ? n.get("status").asText() : "ERROR";
                    errors.add(step + ": " + status);
                });
            }

            List<String> warnings = new ArrayList<>();
            if (!pathCorrect) warnings.add("路径选择错误");
            if (!errors.isEmpty()) warnings.addAll(errors);

            Map<String, Object> findings = Map.of(
                "pathCorrect", pathCorrect,
                "currentPosition", currentPos,
                "nextExpected", nextExpected != null ? nextExpected : "N/A",
                "errors", errors
            );

            return AgentOutput.builder()
                .agentName(name)
                .score(score)
                .reasoning(reasoning)
                .detailedFindings(findings)
                .warnings(warnings)
                .recommendations(pathCorrect ?
                    List.of("继续按照当前路径执行") :
                    List.of("请重新评估当前状态，选择正确算法分支"))
                .hasCriticalViolation(!pathCorrect)
                .criticalViolations(!pathCorrect ? List.of("算法路径选择错误") : null)
                .build();

        } catch (Exception e) {
            log.error("解析DecisionAgent响应失败", e);
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

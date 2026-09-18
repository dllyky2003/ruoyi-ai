package com.ruoyi.ems.engine.multiagent.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.ems.engine.multiagent.BaseAgent;
import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import com.ruoyi.ems.engine.multiagent.prompts.AssessmentPrompt;

import dev.langchain4j.model.chat.ChatModel;   // ✅ 改为 LangChain4j 的 ChatModel
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AssessmentAgent extends BaseAgent {

    @Builder
    public AssessmentAgent(String name, ChatModel chatModel, Double temperature) {   // ✅ 参数类型改为 ChatModel
        super(name, "临床评估智能体", chatModel, temperature);
    }

    @Override
    protected String buildPrompt(AgentInput input) {
        String actionsJson = toJson(input.getPerformance().getActions());
        String judgmentsJson = toJson(input.getPerformance().getJudgments());
        return String.format(AssessmentPrompt.SYSTEM, actionsJson, judgmentsJson);
    }

    @Override
    protected AgentOutput parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            List<String> completed = new ArrayList<>();
            if (root.has("completedAssessments")) {
                root.get("completedAssessments").forEach(n -> completed.add(n.asText()));
            }
            List<String> missing = new ArrayList<>();
            if (root.has("missingAssessments")) {
                root.get("missingAssessments").forEach(n -> missing.add(n.asText()));
            }
            int score = root.has("accuracyScore") ? root.get("accuracyScore").asInt() : 70;
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "评估完成";
            Map<String, Object> findings = new HashMap<>();
            if (root.has("detailedFindings")) {
                findings = objectMapper.convertValue(root.get("detailedFindings"), Map.class);
            }
            List<String> warnings = new ArrayList<>();
            if (!missing.isEmpty()) {
                warnings.add("缺失评估项: " + String.join(", ", missing));
            }

            return AgentOutput.builder()
                .agentName(name)
                .score(score)
                .reasoning(reasoning)
                .detailedFindings(findings)
                .warnings(warnings)
                .recommendations(missing.stream().map(m -> "请完成" + m + "评估").toList())
                .hasCriticalViolation(score < 50)
                .criticalViolations(score < 50 ? List.of("临床评估严重不完整") : null)
                .build();

        } catch (Exception e) {
            log.error("解析AssessmentAgent响应失败", e);
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

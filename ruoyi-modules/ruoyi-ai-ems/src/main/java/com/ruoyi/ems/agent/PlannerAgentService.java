package com.ruoyi.ems.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.agent.dto.Action;
import com.ruoyi.ems.agent.dto.Plan;
import com.ruoyi.ems.intent.client.OllamaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PlannerAgentService {

    @Autowired
    private OllamaClient ollamaClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("classpath:prompts/agent_planner_system.txt")
    private Resource systemPromptResource;

    private String systemPrompt;

    @PostConstruct
    public void init() throws IOException {
        // 读取文件内容到 systemPrompt 变量
        try (var inputStream = systemPromptResource.getInputStream()) {
            systemPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        log.info("已加载 Agent 规划器系统提示词，长度: {} 字符", systemPrompt.length());
    }

    public Plan generatePlan(String intent, String actType, Map<String, String> stepStatus) {
        // 构造用户提示词（包含当前上下文）
        String userPrompt = String.format("""
            当前上下文：
            意图: %s
            语用: %s
            当前步骤状态: %s
            请根据上述信息和规则生成操作计划。
            """, intent, actType, stepStatus);

        try {
            log.info("系统提示词: {}", systemPrompt);
            log.info("用户提示词: {}", userPrompt);
            String response = ollamaClient.chat(systemPrompt, userPrompt);
            log.info("LLM 原始返回: {}", response);

            String json = extractJson(response);
            return objectMapper.readValue(json, Plan.class);
        } catch (Exception e) {
            log.error("LLM 调用或解析失败，使用兜底计划", e);
            return getFallbackPlan();
        }
    }

    private Plan getFallbackPlan() {
        Plan fallback = new Plan();
        fallback.setPlan(List.of(
            new Action("REPORT", Map.of("message", "无法解析指令，请重试"))
        ));
        fallback.setFinalMessage("请重新描述您的操作需求");
        return fallback;
    }

    private String extractJson(String raw) {
        return raw.replaceAll("(?s)```json\\s*|```", "").trim();
    }
}

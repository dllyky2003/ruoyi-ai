package com.ruoyi.ems.controller;

import com.ruoyi.ems.agent.ExecutorAgentService;
import com.ruoyi.ems.agent.dto.Plan;
import com.ruoyi.ems.domain.ExecutionResult;

import com.ruoyi.ems.service.agent.PlannerAgentService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentTestController {

    @Autowired
    private PlannerAgentService plannerAgentService;

    @Autowired
    private ExecutorAgentService executorAgentService;

    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestBody AgentTestRequest request) {
        // 模拟上下文（实际应从会话中获取）
        Map<String, String> stepStatus = Map.of(
            "safety_clear", "DONE",
            "charge", "PENDING"
        );

        Plan plan = plannerAgentService.generatePlan(request.getIntentCode(), request.getActType(), stepStatus);
        ExecutionResult result = executorAgentService.execute(request.getSessionId(), plan, request.getIntentCode());
        return ResponseEntity.ok(result);
    }
}

@Data
class AgentTestRequest {
    private String sessionId = "test-session-001";
    private String intentCode;
    private String actType;
    private String userInput;
}

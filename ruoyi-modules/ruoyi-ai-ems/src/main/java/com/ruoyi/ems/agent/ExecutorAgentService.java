package com.ruoyi.ems.agent;

import com.ruoyi.ems.agent.dto.Action;
import com.ruoyi.ems.agent.dto.ActionRuntime;
import com.ruoyi.ems.agent.dto.Plan;
import com.ruoyi.ems.domain.ExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExecutorAgentService {

    private final Map<String, ActionRuntime> runtimeMap = new ConcurrentHashMap<>();

    public ExecutionResult execute(String sessionId, Plan plan, String intentCode) {
        ActionRuntime runtime = runtimeMap.computeIfAbsent(sessionId, k -> new ActionRuntime());
        runtime.setIntentCode(intentCode);

        for (Action action : plan.getPlan()) {
            String op = action.getOp();
            Map<String, Object> params = action.getParams();

            switch (op) {
                case "CHECK_STEP":
                    String stepId = (String) params.get("step_id");
                    if (!runtime.isStepDone(stepId)) {
                        // 使用 ExecutionResult.blocked 表示拦截（已有方法）
                        return ExecutionResult.blocked("前置条件不满足: " + stepId + " 未完成，请先执行该步骤");
                    }
                    break;

                case "EXECUTE_STEP":
                    runtime.advance();
                    log.info("执行步骤推进: {}", runtime.getCurrentStepId());
                    break;

                case "ABORT_STEP":
                    String abortStep = (String) params.get("step_id");
                    runtime.abort(abortStep);
                    log.info("已中止步骤: {}", abortStep);
                    break;

                case "MEASURE_VITAL":
                    String vitalType = (String) params.get("vital_type");
                    // 模拟测量（实际应调用监护仪模拟器）
                    int value = 120 + (int)(Math.random() * 20);
                    runtime.getStepResults().put(vitalType, value);
                    log.info("测量 {}: {}", vitalType, value);
                    break;

                case "REPORT":
                    String msg = plan.getFinalMessage();
                    String rendered = renderTemplate(msg, runtime);
                    // 使用现有的 success 方法
                    return ExecutionResult.success(rendered);

                default:
                    return ExecutionResult.rejected("未知操作: " + op);
            }
        }
        return ExecutionResult.success("计划执行完毕");
    }

    private String renderTemplate(String template, ActionRuntime runtime) {
        String result = template;
        for (Map.Entry<String, Object> entry : runtime.getStepResults().entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * 获取指定会话的运行时状态（用于外部读取当前步骤进度）
     */
    public ActionRuntime getRuntime(String sessionId) {
        return runtimeMap.get(sessionId);
    }

}

package com.ruoyi.ems.orchestrator.service;

import com.ruoyi.ems.agent.dto.Action;
import com.ruoyi.ems.agent.dto.Plan;
import com.ruoyi.ems.config.loader.ActionChainConfigLoader;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.ExecutionResult;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.VrClientService;
import com.ruoyi.ems.orchestrator.model.ActionChainRuntime;

import com.ruoyi.ems.orchestrator.model.StepStatus;
import com.ruoyi.ems.tts.TtsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanExecutor {

    private final AtomicActionDispatcher actionDispatcher;
    private final TeamManager teamManager;
    private final VrClientService vrClientService;
    private final TtsService ttsService;
    private final ActionChainConfigLoader actionChainConfigLoader;  // ★ 新增

    /**
     * 执行 LLM 生成的计划
     */
    public ExecutionResult executePlan(String sessionId, Plan plan, CaseContext context) {
        if (plan == null || plan.getPlan() == null || plan.getPlan().isEmpty()) {
            return ExecutionResult.rejected("计划为空，无法执行");
        }

        log.info("▶️ [{}] 开始执行计划，共 {} 个操作", sessionId, plan.getPlan().size());

        // 获取当前会话的动作链运行时（需由外部传入或从 TeamManager 获取）
        ActionChainRuntime runtime = teamManager.getChain(sessionId);
        if (runtime == null) {
            return ExecutionResult.rejected("动作链运行时未初始化");
        }

        for (Action action : plan.getPlan()) {
            String op = action.getOp();
            Map<String, Object> params = action.getParams();

            log.info("📌 [{}] 执行操作: {} {}", sessionId, op, params);

            ExecutionResult result;
            switch (op) {
                case "CHECK_STEP":
                    result = handleCheckStep(sessionId, params, runtime);
                    break;
                case "EXECUTE_STEP":
                    result = handleExecuteStep(sessionId, runtime, context);
                    break;
                case "ABORT_STEP":
                    result = handleAbortStep(sessionId, params, runtime);
                    break;
                case "REPORT":
                    result = handleReport(sessionId, params, context);
                    break;
                case "MEASURE_VITAL":
                    result = handleMeasureVital(sessionId, params, context);
                    break;
                default:
                    result = ExecutionResult.rejected("未知操作: " + op);
                    break;
            }

            if (!result.isSuccess()) {
                log.error("❌ [{}] 操作 {} 执行失败: {}", sessionId, op, result.getErrorMessage());
                return result;
            }
        }

        log.info("✅ [{}] 计划执行完成", sessionId);
        return ExecutionResult.success("计划执行完成");
    }

    // ==================== 各原子操作处理 ====================

    private ExecutionResult handleCheckStep(String sessionId, Map<String, Object> params, ActionChainRuntime runtime) {
        String stepId = (String) params.get("step_id");
        if (stepId == null) {
            return ExecutionResult.rejected("CHECK_STEP缺少step_id");
        }
        boolean done = runtime.isStepDone(stepId);
        log.info("🔍 [{}] 检查步骤 {}: {}", sessionId, stepId, done ? "已完成" : "未完成");
        return ExecutionResult.success("步骤 " + stepId + " 状态: " + (done ? "DONE" : "PENDING"));
    }

    private ExecutionResult handleExecuteStep(String sessionId, ActionChainRuntime runtime, CaseContext context) {
        // 1. 推进到下一步
        runtime.advance();
        String stepId = runtime.getCurrentStepId();
        if (stepId == null) {
            return ExecutionResult.rejected("没有更多步骤可执行");
        }

        log.info("⚙️ [{}] 执行步骤: {}", sessionId, stepId);

        // 2. 从配置加载器获取步骤模板
        ActionChainConfigLoader.StepTemplate template = actionChainConfigLoader.getStepTemplate(stepId);
        if (template == null) {
            return ExecutionResult.rejected("步骤 " + stepId + " 未在 step_library.json 中定义");
        }

        String refAtomic = template.getRefAtomic();
        if (refAtomic == null || refAtomic.isEmpty()) {
            return ExecutionResult.rejected("步骤 " + stepId + " 缺少 refAtomic 配置");
        }

        // 3. 调用 AtomicActionDispatcher 派发任务（同步等待完成）
        try {
            actionDispatcher.dispatch(sessionId, refAtomic, template.getDuty(), null, null, context);
            // 标记步骤为完成（假设 dispatch 同步完成）
            runtime.updateStepStatus(stepId, StepStatus.DONE);
            return ExecutionResult.success("步骤 " + stepId + " 完成");
        } catch (Exception e) {
            log.error("步骤执行异常", e);
            runtime.updateStepStatus(stepId, StepStatus.FAILED);
            return ExecutionResult.rejected("步骤执行异常: " + e.getMessage());
        }
    }

    private ExecutionResult handleAbortStep(String sessionId, Map<String, Object> params, ActionChainRuntime runtime) {
        String stepId = (String) params.get("step_id");
        if (stepId != null) {
            runtime.updateStepStatus(stepId, StepStatus.ABORTED);
            log.info("⛔ [{}] 中止步骤: {}", sessionId, stepId);
            vrClientService.sendAnimation(sessionId, "abort", 0);
        }
        return ExecutionResult.success("已中止步骤 " + stepId);
    }

    private ExecutionResult handleReport(String sessionId, Map<String, Object> params, CaseContext context) {
        String message = (String) params.get("message");
        if (message == null) {
            message = "操作完成";
        }
        log.info("📢 [{}] 报告: {}", sessionId, message);
        ttsService.synthesizeAndSend(sessionId, message);
        context.setVar("lastReport", message, "SYSTEM");
        return ExecutionResult.success(message);
    }

    private ExecutionResult handleMeasureVital(String sessionId, Map<String, Object> params, CaseContext context) {
        String type = (String) params.get("vital_type");
        if (type == null) return ExecutionResult.rejected("缺少vital_type");
        // 模拟测量
        int value = 0;
        if ("bp".equals(type)) {
            value = 120 + (int)(Math.random() * 20);
            context.setVar("last_bp", value, "SYSTEM");
        } else if ("hr".equals(type)) {
            value = 70 + (int)(Math.random() * 30);
            context.setVar("last_hr", value, "SYSTEM");
        }
        return ExecutionResult.success(type + " 测量完成: " + value);
    }
}

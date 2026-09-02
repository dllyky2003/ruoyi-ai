package com.ruoyi.ems.orchestrator.service;

import com.ruoyi.ems.agent.dto.Plan;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.orchestrator.model.ActionChainConfig;
import com.ruoyi.ems.orchestrator.model.ActionChainRuntime;
import com.ruoyi.ems.orchestrator.model.OrchestrationConfig;
import com.ruoyi.ems.orchestrator.model.StageConfig;
import com.ruoyi.ems.orchestrator.model.StepStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

/**
 * 编排解释器 - 每个会话一个独立实例
 * 负责管理 Stage 索引、前置条件校验、流程推进、ACK 等待
 * 同时支持动作链（Action Chain）的执行
 */
@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class OrchestrationInterpreter {

    private final PreconditionValidator preconditionValidator;
    private final StageExecutor stageExecutor;
    private final ContinuousTaskScheduler continuousTaskScheduler;
    private final AtomicActionDispatcher actionDispatcher;
    private final TeamManager teamManager;
    private final PlanExecutor planExecutor;

    // 会话状态
    private String sessionId;
    private OrchestrationConfig config;
    private CaseContext caseContext;
    private final Set<String> completedIntents = new HashSet<>();
    private int currentStageIndex = 0;
    private boolean isAborted = false;
    private boolean isRunning = false;

    // ACK 等待机制
    private CompletableFuture<Boolean> ackFuture;
    private static final int ACK_TIMEOUT_SECONDS = 10;

    // 动作链运行时（每个会话一个）
    private ActionChainRuntime chainRuntime;

    @PostConstruct
    public void init() {
        log.debug("🆕 创建新的编排解释器实例");
    }

    /**
     * 加载编排配置和上下文
     */
    public void loadContext(String sessionId, OrchestrationConfig config, CaseContext context) {
        this.sessionId = sessionId;
        this.config = config;
        this.caseContext = context;
        this.completedIntents.clear();
        this.currentStageIndex = 0;
        this.isAborted = false;
        this.ackFuture = null;
        this.chainRuntime = null;
    }

    /**
     * 执行动作链（从意图定义中读取步骤）
     * @param sessionId 会话ID
     * @param intentCode 意图编码（用于获取步骤列表）
     * @param def 意图定义（包含 execution.actionChain）
     * @throws PreconditionFailedException 前置条件不满足时抛出
     */
    public void executeActionChain(String sessionId, String intentCode, IntentDefinition def)
        throws PreconditionFailedException {
        ActionChainConfig chain =  def.getExecution();
        if (chain == null || chain.steps() == null || chain.steps().isEmpty()) {
            log.warn("意图 {} 无动作链配置", intentCode);
            return;
        }

        // 1. 获取或创建运行时状态
        this.chainRuntime = teamManager.getOrCreateChain(sessionId, intentCode,
            chain.steps().stream().map(ActionChainConfig.StepConfig::stepId).toList());

        // 2. 初始化步骤状态（如果尚未初始化）
        // 注意：getOrCreateChain 已创建并设置所有步骤为 PENDING

        // 3. 遍历步骤顺序执行
        for (ActionChainConfig.StepConfig step : chain.steps()) {
            String stepId = step.stepId();

            // 检查是否已完成（跳过已完成的）
            if (chainRuntime.isStepDone(stepId)) {
                log.debug("步骤 {} 已完成，跳过", stepId);
                continue;
            }

            // 检查前置条件
            List<String> preconditions = chain.preconditions() != null ?
                chain.preconditions().get(stepId) : null;
            if (preconditions != null) {
                for (String preId : preconditions) {
                    if (!chainRuntime.isStepDone(preId)) {
                        throw new PreconditionFailedException(
                            String.format("前置条件 %s 未完成，无法执行 %s", preId, stepId)
                        );
                    }
                }
            }

            // 执行原子动作（异步并等待完成）
            try {
                // 标记为进行中
                chainRuntime.updateStepStatus(stepId, StepStatus.IN_PROGRESS);

                // 调用 AtomicActionDispatcher 派发任务（同步等待完成）
                // 注意：dispatch 内部会通过 TeamManager 分配任务并等待完成
                actionDispatcher.dispatch(
                    sessionId,
                    step.refAtomic(),
                    step.duty(),
                    null,  // staticParams
                    null,  // spelParams
                    caseContext
                );

                // 等待任务完成（TeamManager 内部通过事件驱动，但 dispatch 是同步等待）
                // 如果 dispatch 是同步的，这里无需额外等待
                // 如果是异步，需要等待，但 dispatch 当前实现是同步的，所以直接标记完成
                chainRuntime.updateStepStatus(stepId, StepStatus.DONE);
                log.info("✅ 步骤 {} 执行完成", stepId);

            } catch (Exception e) {
                chainRuntime.updateStepStatus(stepId, StepStatus.FAILED);
                log.error("步骤 {} 执行失败", stepId, e);
                throw new PreconditionFailedException("步骤执行失败: " + e.getMessage(), e);
            }
        }

        // 所有步骤完成，触发完成事件
        if (chain.onComplete() != null && chain.onComplete().transitionEvent() != null) {
            log.info("🎯 动作链 {} 完成，触发事件: {}", intentCode, chain.onComplete().transitionEvent());
            // 将完成事件记录到上下文
            caseContext.setVar("lastTransitionEvent", chain.onComplete().transitionEvent(), "SYSTEM");
        }
    }

    /**
     * 记录已完成的意图（用于前置条件校验）
     */
    public void addCompletedIntent(String intentCode) {
        this.completedIntents.add(intentCode);
        log.debug("📝 [{}] 记录已完成意图: {}", sessionId, intentCode);
    }

    /**
     * 执行 LLM 生成的计划（委托给 PlanExecutor）
     */
    public void executePlan(String sessionId, Plan plan, CaseContext context) {
        // 先确保有动作链运行时（如果还没有，使用默认S2-6步骤）
        if (this.chainRuntime == null) {
            // 尝试从上下文中获取意图编码，若无则默认
            String intentCode = context.getVar("currentIntent", String.class);
            if (intentCode == null) intentCode = "S2-6";
            // 使用默认步骤列表（简化，实际应从配置加载）
            List<String> defaultSteps = List.of("prep_sedation", "set_sync", "select_energy",
                "charge", "safety_clear", "discharge", "reassess");
            this.chainRuntime = teamManager.getOrCreateChain(sessionId, intentCode, defaultSteps);
        }
        // 委托给 PlanExecutor
        planExecutor.executePlan(sessionId, plan, context);
    }

    /**
     * 执行编排（由外部调用）
     */
    public void execute() throws Exception {
        if (config == null) {
            log.warn("⚠️ [{}] 编排配置为空，跳过执行", sessionId);
            return;
        }

        isRunning = true;
        log.info("▶️ [{}] 开始执行编排: {}", sessionId, config.getDescription());

        try {
            // 1. 检查前置条件
            if (!preconditionValidator.validate(config.getPreconditions(), caseContext, completedIntents)) {
                log.warn("⛔ [{}] 前置条件不满足，终止执行", sessionId);
                handleAbort();
                return;
            }

            // 2. 逐 Stage 执行
            var stages = config.getStages();
            while (currentStageIndex < stages.size()) {
                StageConfig stage = stages.get(currentStageIndex);
                log.info("📌 [{}] 执行 Stage {}/{}: {}", sessionId, currentStageIndex + 1, stages.size(), stage.getId());

                // 执行当前 Stage（串行/并行/原子）
                stageExecutor.execute(sessionId, stage, caseContext);

                // 如果 Stage 需要 ACK，等待组长确认
                if (Boolean.TRUE.equals(stage.getRequiresAck())) {
                    boolean ackReceived = waitForAck();
                    if (!ackReceived) {
                        log.warn("⏰ [{}] ACK 超时或未确认，终止 Stage [{}]", sessionId, stage.getId());
                        // 触发错误恢复
                        handleErrorRecovery(new TimeoutException("ACK timeout"));
                        return;
                    }
                }

                currentStageIndex++;
            }

            // 3. 启动持续性任务（如 CPR）
            if (config.getContinuousTask() != null && Boolean.TRUE.equals(config.getContinuousTask().getEnabled())) {
                continuousTaskScheduler.start(sessionId, config.getContinuousTask(), () -> {
                    log.info("📢 [{}] 自动上报：按压深度5cm，频率105次/分", sessionId);
                });
            }

            // 4. 启动后置条件定时器
            startPostConditions();

            log.info("✅ [{}] 编排执行完成", sessionId);

        } catch (Exception e) {
            log.error("❌ [{}] 编排执行异常", sessionId, e);
            handleErrorRecovery(e);
        } finally {
            isRunning = false;
        }
    }

    /**
     * 等待组长 ACK 确认（带超时）
     */
    private boolean waitForAck() {
        if (ackFuture != null && !ackFuture.isDone()) {
            ackFuture.cancel(true);
        }
        ackFuture = new CompletableFuture<>();
        log.info("⏳ [{}] 等待组长 ACK 确认（超时 {} 秒）...", sessionId, ACK_TIMEOUT_SECONDS);

        try {
            return ackFuture.get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("⏰ [{}] ACK 等待超时", sessionId);
            return false;
        } catch (Exception e) {
            log.error("ACK 等待异常", e);
            return false;
        }
    }

    /**
     * 外部调用：发送 ACK 信号
     */
    public void sendAck(boolean confirmed) {
        if (ackFuture != null && !ackFuture.isDone()) {
            ackFuture.complete(confirmed);
            log.info("✅ [{}] 收到 ACK: {}", sessionId, confirmed ? "确认继续" : "拒绝/取消");
        } else {
            log.warn("⚠️ [{}] 无等待中的 ACK 请求", sessionId);
        }
    }

    /**
     * 中断信号（用于外部打断持续任务）
     */
    public void interrupt(String intentCode) {
        var continuous = config != null ? config.getContinuousTask() : null;
        if (continuous != null && continuous.getInterruptOn() != null
            && continuous.getInterruptOn().contains(intentCode)) {
            log.info("⏹️ [{}] 接收到中断意图: {}, 停止持续任务", sessionId, intentCode);
            continuousTaskScheduler.stop(sessionId);
        }
    }

    private void handleAbort() {
        isAborted = true;
        continuousTaskScheduler.stop(sessionId);
        if (config != null && config.getPreconditions() != null
            && "abort_with_alert".equals(config.getPreconditions().getOnTimeoutAction())) {
            log.warn("🚨 [{}] 执行中止，触发告警", sessionId);
        }
    }

    private void handleErrorRecovery(Exception e) {
        var recovery = config != null ? config.getErrorRecovery() : null;
        if (recovery != null) {
            if (recovery.getAutoRetryCount() != null && recovery.getAutoRetryCount() > 0) {
                log.info("🔄 [{}] 错误恢复：自动重试（剩余 {} 次）", sessionId, recovery.getAutoRetryCount());
                // TODO: 实现重试逻辑
            }
            if (recovery.getFallbackIntent() != null) {
                log.info("🔁 [{}] 错误恢复：回退到意图 {}", sessionId, recovery.getFallbackIntent());
                // 触发回退意图
            }
        }
        isAborted = true;
    }

    private void startPostConditions() {
        if (config == null || config.getPostConditions() == null) return;
        for (var pc : config.getPostConditions()) {
            log.info("⏲️ [{}] 启动后置条件: {} ({} {})", sessionId, pc.getTriggerIntent(), pc.getCondition(), pc.getValue());
            // TODO: 使用 ScheduledExecutorService 实现定时触发
        }
    }

    // ==================== Getters ====================
    public boolean isRunning() { return isRunning; }
    public boolean isAborted() { return isAborted; }
    public ActionChainRuntime getChainRuntime() { return chainRuntime; }

    /**
     * 前置条件不满足异常
     */
    public static class PreconditionFailedException extends Exception {
        public PreconditionFailedException(String message) {
            super(message);
        }
        public PreconditionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

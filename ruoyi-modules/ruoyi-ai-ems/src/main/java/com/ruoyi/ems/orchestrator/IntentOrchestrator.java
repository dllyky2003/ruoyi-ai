package com.ruoyi.ems.orchestrator;

import com.ruoyi.ems.action.SystemAction;
import com.ruoyi.ems.agent.dto.Plan;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.ActionChainConfigLoader;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.DecisionResult;
import com.ruoyi.ems.domain.ExecutionResult;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.engine.RuleMatcher;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.event.GlobalEventReceivedEvent;
import com.ruoyi.ems.event.StateTransitionEvent;
import com.ruoyi.ems.executor.factory.IntentExecutorFactory;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.mapper.SlotToVariableMapper;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.orchestrator.model.ActionChainConfig;
import com.ruoyi.ems.orchestrator.model.ActionChainRuntime;
import com.ruoyi.ems.orchestrator.model.StepStatus;
import com.ruoyi.ems.orchestrator.service.PlanExecutor;
import com.ruoyi.ems.rule.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 意图编排器
 * 统一入口：接收意图命令，协调各组件完成流程处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentOrchestrator {

    // ========== 依赖（构造器注入） ==========
    private final EmsContextManager contextManager;
    private final RuleMatcher ruleMatcher;
    private final RuleEvaluator ruleEvaluator;
    private final StateMachineFacade stateMachineFacade;
    private final SlotToVariableMapper slotMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final YamlStateMachineLoader configLoader;
    private final IntentExecutorFactory executorFactory;
    private final com.ruoyi.ems.service.agent.PlannerAgentService plannerAgentService;
    private final PlanExecutor planExecutor;
    private final TeamManager teamManager;
    // ★ 新增：意图定义加载器
    private final CaseIntentDefinitionsLoader intentDefinitionsLoader;
    private final ActionChainConfigLoader actionChainConfigLoader;
    // ============================================================
    // 公开方法
    // ============================================================

    public DecisionResult processIntent(String caseInstanceId, IntentCommand command) {
        CaseContext context = contextManager.getOrCreate(caseInstanceId);
        String caseType = context.getCaseType();

        slotMapper.mapSlotsToVariables(caseInstanceId, command.getSlots(), context);
        contextManager.update(caseInstanceId, context);



        ResolvedCaseTypeConfig config = configLoader.getConfig(caseType);
        if (config == null) {
            return DecisionResult.rejected("UNKNOWN", "未知案例类型: " + caseType, context);
        }

        String currentState = stateMachineFacade.getCurrentState(caseInstanceId, config);
        context.setCurrentState(currentState);
        contextManager.update(caseInstanceId, context);

        if (config.getGlobalEventIntents().contains(command.getIntentCode())) {
            return handleGlobalEvent(caseInstanceId, context, command);
        }

        // ★ 判断是否为动作链意图（基于配置）
        if (isActionChainIntent(caseType, command.getIntentCode())) {
            return handleActionChainIntent(caseInstanceId, context, command);
        }

        ExecutionResult execResult = executorFactory.execute(command, caseInstanceId);

        if (execResult != null && execResult.getResponseText() != null) {
            context.setVar("lastResponse", execResult.getResponseText(), "SYSTEM");
            contextManager.update(caseInstanceId, context);
        }

        if (execResult != null && execResult.isBlocked()) {
            return DecisionResult.rejected(
                currentState,
                "⚠️ 操作被拦截：" + execResult.getBlockReason(),
                context
            );
        }

        if (execResult != null && !execResult.isSuccess()) {
            return DecisionResult.rejected(
                currentState,
                "❌ 执行失败：" + execResult.getErrorMessage(),
                context
            );
        }

        if (execResult != null && execResult.isRequiresStateTransition()) {
            String eventCode = execResult.getEventCode();
            String newState = stateMachineFacade.transition(caseInstanceId, config, eventCode);

            if (newState == null) {
                return DecisionResult.rejected(currentState, "状态机拒绝事件: " + eventCode, context);
            }

            context.setCurrentState(newState);
            contextManager.update(caseInstanceId, context);

            eventPublisher.publishEvent(
                StateTransitionEvent.builder()
                    .caseInstanceId(caseInstanceId)
                    .fromState(currentState)
                    .toState(newState)
                    .eventCode(eventCode)
                    .intentCode(command.getIntentCode())
                    .success(true)
                    .contextSnapshot(context.getVariablesSnapshot())
                    .build()
            );

            List<SystemAction> actions = buildSystemActionsForIntent(execResult, context);
            return DecisionResult.success(currentState, newState, eventCode, context, actions);
        }

        if (execResult != null && execResult.getAssignedMember() != null) {
            context.setVar("lastAssignedMember",
                execResult.getAssignedMember().getFullName(),
                "SYSTEM");
            contextManager.update(caseInstanceId, context);
        }

        if (execResult != null && execResult.isSuccess()) {
            return DecisionResult.globalEvent(currentState, command.getIntentCode(), context);
        }

        TransitionRule matchedRule = ruleMatcher.findMatchingRule(config, currentState, command);
        if (matchedRule == null) {
            return handleDefaultTransition(caseInstanceId, context, config, command);
        }

        if (matchedRule.getCondition() != null && !matchedRule.getCondition().isEmpty()) {
            boolean passed = ruleEvaluator.evaluateCondition(matchedRule.getCondition(), context);
            if (!passed) {
                return DecisionResult.rejected(currentState,
                    "条件不满足: " + matchedRule.getCondition(), context);
            }
        }

        if (matchedRule.getAction() != null && !matchedRule.getAction().isEmpty()) {
            ruleEvaluator.executeAction(matchedRule.getAction(), context);
        }

        contextManager.update(caseInstanceId, context);

        String eventCode = matchedRule.getEvent();
        String newState = stateMachineFacade.transition(caseInstanceId, config, eventCode);

        if (newState == null) {
            return DecisionResult.rejected(currentState, "状态机拒绝事件: " + eventCode, context);
        }

        context.setCurrentState(newState);
        contextManager.update(caseInstanceId, context);

        List<SystemAction> actions = buildSystemActions(matchedRule, context);

        eventPublisher.publishEvent(
            StateTransitionEvent.builder()
                .caseInstanceId(caseInstanceId)
                .fromState(currentState)
                .toState(newState)
                .eventCode(eventCode)
                .intentCode(command.getIntentCode())
                .success(true)
                .contextSnapshot(context.getVariablesSnapshot())
                .build()
        );

        return DecisionResult.success(currentState, newState, eventCode, context, actions);
    }
    // ============================================================
    // 私有辅助方法
    // ============================================================

    private DecisionResult handleActionChainIntent(String caseInstanceId,
                                                   CaseContext context,
                                                   IntentCommand command) {
        String intentCode = command.getIntentCode();
        String actType = command.getActType();

        // ★ 如果 actType 为 null，从意图定义中获取默认值
        if (actType == null) {
            IntentDefinition def = intentDefinitionsLoader.getIntent(intentCode);
            if (def != null) {
                actType = def.getActType();
            }
        }
        if (actType == null) {
            actType = "发令性指令";
            log.warn("意图 {} 的 actType 为 null，使用默认值: {}", intentCode, actType);
        }

        log.info("🔗 进入动作链处理: intentCode={}, actType={}", intentCode, actType);

        // ★ 移除旧的 chain 实例，确保不会复用之前意图的步骤状态
        teamManager.removeChain(caseInstanceId);

        List<String> stepIds = getStepIdsForIntent(context.getCaseType(), intentCode);
        if (stepIds.isEmpty()) {
            log.warn("动作链步骤为空，无法执行: {}", intentCode);
            return DecisionResult.rejected(context.getCurrentState(),
                "动作链步骤为空，无法执行：" + intentCode, context);
        }

        ActionChainRuntime runtime = teamManager.getOrCreateChain(caseInstanceId, intentCode, stepIds);

        Map<String, String> stepStatus = runtime.getStepStatusMap().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue() == StepStatus.DONE ? "DONE" : "PENDING"
            ));

        Plan plan = plannerAgentService.generatePlan(intentCode, actType, stepStatus);
        log.info("📋 生成计划: {}", plan);

        ExecutionResult result;
        try {
            result = planExecutor.executePlan(caseInstanceId, plan, context);
        } catch (Exception e) {
            log.error("执行计划异常", e);
            return DecisionResult.rejected(context.getCurrentState(),
                "执行计划失败: " + e.getMessage(), context);
        }

        if (result.isSuccess()) {
            context.setVar("lastResponse", result.getResponseText(), "SYSTEM");
            contextManager.update(caseInstanceId, context);

            if (runtime.isAllStepsDone()) {
                String eventCode = intentCode + "_DONE";
                ResolvedCaseTypeConfig config = configLoader.getConfig(context.getCaseType());
                String newState = stateMachineFacade.transition(caseInstanceId, config, eventCode);
                if (newState != null) {
                    context.setCurrentState(newState);
                    contextManager.update(caseInstanceId, context);
                    eventPublisher.publishEvent(
                        StateTransitionEvent.builder()
                            .caseInstanceId(caseInstanceId)
                            .fromState(context.getCurrentState())
                            .toState(newState)
                            .eventCode(eventCode)
                            .intentCode(intentCode)
                            .success(true)
                            .contextSnapshot(context.getVariablesSnapshot())
                            .build()
                    );
                    return DecisionResult.success(context.getCurrentState(), newState,
                        eventCode, context, buildSystemActionsForIntent(result, context));
                }
            }
            return DecisionResult.globalEvent(context.getCurrentState(), intentCode, context);
        } else {
            return DecisionResult.rejected(context.getCurrentState(),
                "执行失败: " + result.getErrorMessage(), context);
        }
    }
    /**
     * 判断是否为动作链意图（基于配置）
     */
    /**
     * 判断是否为动作链意图（从 chain.json 加载）
     */
    private boolean isActionChainIntent(String caseType, String intentCode) {
        if (intentCode == null) return false;
        // 使用 ActionChainConfigLoader 检查该 caseType 下是否有该意图的动作链
        return actionChainConfigLoader.isActionChain(caseType, intentCode);
    }
    /**
     * 从意图定义中获取步骤ID列表
     */
    private List<String> getStepIdsForIntent(String caseType, String intentCode) {
        return actionChainConfigLoader.getStepsForIntent(caseType, intentCode);
    }

    private DecisionResult handleGlobalEvent(String caseInstanceId,
                                             CaseContext context,
                                             IntentCommand command) {
        context.setVar("lastGlobalEvent", command.getIntentCode(), "SYSTEM");
        contextManager.update(caseInstanceId, context);

        eventPublisher.publishEvent(
            GlobalEventReceivedEvent.builder()
                .caseInstanceId(caseInstanceId)
                .intentCode(command.getIntentCode())
                .eventCode(command.getIntentCode() + "_DONE")
                .currentState(context.getCurrentState())
                .rawText(command.getRawText())
                .slots(command.getSlots())
                .confidence(command.getConfidence())
                .build()
        );

        return DecisionResult.globalEvent(context.getCurrentState(), command.getIntentCode(), context);
    }

    private DecisionResult handleDefaultTransition(String caseInstanceId,
                                                   CaseContext context,
                                                   ResolvedCaseTypeConfig config,
                                                   IntentCommand command) {
        if (!config.isAllowUnlistedTransitions()) {
            return DecisionResult.rejected(context.getCurrentState(),
                "当前状态下不允许此操作: " + command.getIntentCode(), context);
        }

        String targetState = config.getIntentToStateMapping().get(command.getIntentCode());
        if (targetState == null || !config.getStates().contains(targetState)) {
            return DecisionResult.rejected(context.getCurrentState(),
                "无法找到目标状态: " + command.getIntentCode(), context);
        }

        String newState = stateMachineFacade.transition(caseInstanceId, config, "SYS_DEFAULT_TRANSITION");
        if (newState == null) {
            return DecisionResult.rejected(context.getCurrentState(), "默认转移失败", context);
        }

        context.setCurrentState(newState);
        contextManager.update(caseInstanceId, context);

        return DecisionResult.success(context.getCurrentState(), newState,
            "SYS_DEFAULT_TRANSITION", context, null);
    }

    @SuppressWarnings("unused")
    private List<SystemAction> buildSystemActions(TransitionRule rule, CaseContext context) {
        List<SystemAction> actions = new ArrayList<>();
        if (rule.getSystemActionType() != null && !rule.getSystemActionType().isEmpty()) {
            SystemAction action = SystemAction.builder()
                .type(rule.getSystemActionType())
                .params(rule.getSystemActionParams() != null ? rule.getSystemActionParams() : new HashMap<>())
                .source("STATE_MACHINE")
                .description(rule.getDescription())
                .build();
            actions.add(action);
        }
        return actions;
    }

    @SuppressWarnings("unused")
    private List<SystemAction> buildSystemActionsForIntent(ExecutionResult result, CaseContext context) {
        List<SystemAction> actions = new ArrayList<>();
        if (result.getSystemActions() != null) {
            actions.addAll(result.getSystemActions());
        }
        if (result.getRoleInstructions() != null && !result.getRoleInstructions().isEmpty()) {
            for (Map.Entry<String, String> entry : result.getRoleInstructions().entrySet()) {
                SystemAction action = SystemAction.builder()
                    .type("ROLE_INSTRUCTION")
                    .params(new HashMap<>(Map.of(
                        "role", entry.getKey(),
                        "instruction", entry.getValue()
                    )))
                    .source("EXECUTOR")
                    .description("给 " + entry.getKey() + " 的指令: " + entry.getValue())
                    .build();
                actions.add(action);
            }
        }
        return actions;
    }
}

package com.ruoyi.ems.engine;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.rule.IRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class StateMachineBuilderFactory {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EmsContextManager contextManager;

    @Autowired
    private IRuleEvaluator ruleEvaluator;

    private final Map<String, StateMachineBuilder.Builder<String, String>> builderCache = new ConcurrentHashMap<>();
    private final Map<String, Object> buildLocks = new ConcurrentHashMap<>();

    // ==================== 核心方法 ====================

    public StateMachineBuilder.Builder<String, String> getOrCreateBuilder(ResolvedCaseTypeConfig config) {
        String caseType = config.getCode();

        StateMachineBuilder.Builder<String, String> builder = builderCache.get(caseType);
        if (builder != null) {
            return builder;
        }

        Object lock = buildLocks.computeIfAbsent(caseType, k -> new Object());
        synchronized (lock) {
            builder = builderCache.get(caseType);
            if (builder != null) {
                return builder;
            }

            log.info("🏗️ 构建状态机定义: {} (状态: {}, 转移: {})",
                caseType, config.getStates().size(), config.getTransitions().size());
            builder = buildStateMachine(config);

            builderCache.put(caseType, builder);
            buildLocks.remove(caseType);
            return builder;
        }
    }

    @SuppressWarnings("deprecation")
    public StateMachine<String, String> buildMachine(ResolvedCaseTypeConfig config) {
        StateMachineBuilder.Builder<String, String> builder = getOrCreateBuilder(config);
        StateMachine<String, String> stateMachine = builder.build();
        stateMachine.start();
        return stateMachine;
    }

    @SuppressWarnings("deprecation")
    public StateMachine<String, String> buildMachine(ResolvedCaseTypeConfig config, String caseInstanceId) {
        StateMachine<String, String> stateMachine = buildMachine(config);

        // ★ 注入 sessionId 到 extendedState
        stateMachine.getExtendedState().getVariables().put("sessionId", caseInstanceId);

        String targetState = null;
        CaseContext context = contextManager.getSnapshot(caseInstanceId);
        if (context != null && context.getCurrentState() != null) {
            String savedState = context.getCurrentState();
            if (config.getStates().contains(savedState)) {
                targetState = savedState;
                log.debug("📂 状态机恢复: {} -> {}", caseInstanceId, savedState);
            }
        }

        if (targetState != null) {
            final String finalState = targetState;
            try {
                stateMachine.getStateMachineAccessor().doWithAllRegions(access -> {
                    access.resetStateMachine(
                        new DefaultStateMachineContext<>(finalState, null, null, null)
                    );
                });
            } catch (Exception e) {
                log.warn("状态机恢复失败: caseInstanceId={}, targetState={}, err={}",
                    caseInstanceId, finalState, e.getMessage());
            }
        }

        return stateMachine;
    }

    public void clearCache() {
        builderCache.clear();
        buildLocks.clear();
        log.info("🔄 状态机构建器缓存已清除");
    }

    public void clearCache(String caseType) {
        builderCache.remove(caseType);
        buildLocks.remove(caseType);
        log.info("🔄 清除案例缓存: {}", caseType);
    }

    // ==================== 私有构建方法 ====================

    private StateMachineBuilder.Builder<String, String> buildStateMachine(ResolvedCaseTypeConfig config) {
        try {
            StateMachineBuilder.Builder<String, String> builder = StateMachineBuilder.builder();
            configureStates(builder, config);
            configureTransitions(builder, config);
            return builder;
        } catch (Exception e) {
            log.error("❌ 状态机构建失败: {}", config.getCode(), e);
            throw new RuntimeException("状态机构建失败: " + config.getCode(), e);
        }
    }

    private void configureStates(StateMachineBuilder.Builder<String, String> builder,
                                 ResolvedCaseTypeConfig config) throws Exception {
        var stateConfigurer = builder.configureStates();
        var stateBuilder = stateConfigurer.withStates()
            .initial(config.getInitialState());

        Set<String> stateSet = new HashSet<>(config.getStates());
        stateBuilder.states(stateSet);

        for (String endState : config.getEndStates()) {
            stateBuilder.end(endState);
        }
    }

    private void configureTransitions(StateMachineBuilder.Builder<String, String> builder,
                                      ResolvedCaseTypeConfig config) throws Exception {
        var transitionConfigurer = builder.configureTransitions();

        for (TransitionRule rule : config.getTransitions()) {
            if (!rule.isEnabled()) {
                continue;
            }

            var transition = transitionConfigurer.withExternal()
                .source(rule.getSource())
                .target(rule.getTarget());

            if (StringUtils.hasText(rule.getEvent())) {
                transition.event(rule.getEvent());
            }

            if (rule.hasCondition()) {
                transition.guard(createGuard(rule));
            }
            if (rule.hasAction()) {
                transition.action(createAction(rule));
            }
            if (rule.getSystemActionType() != null && !rule.getSystemActionType().isEmpty()) {
                transition.action(createSystemAction(rule));
            }
        }
    }

    // ==================== Guard 和 Action 创建 ====================

    private Guard<String, String> createGuard(TransitionRule rule) {
        String expression = rule.getCondition();
        return context -> {
            // ★ context 是 StateContext，不是 StateMachineContext
            String instanceId = resolveInstanceId(context);
            if (instanceId == null) {
                log.warn("Guard 执行时无法获取 sessionId: rule={}", rule.getDisplayName());
                return false;
            }

            CaseContext caseContext = contextManager.getSnapshot(instanceId);
            if (caseContext == null) {
                log.warn("Guard 执行时找不到上下文: {}, rule={}", instanceId, rule.getDisplayName());
                return false;
            }

            try {
                boolean result = ruleEvaluator.evaluateCondition(expression, caseContext);
                log.trace("Guard 评估: {} → {}", expression, result);
                return result;
            } catch (Exception e) {
                log.warn("Guard 评估异常: {}, 错误: {}", expression, e.getMessage());
                return false;
            }
        };
    }

    private Action<String, String> createAction(TransitionRule rule) {
        String expression = rule.getAction();
        return context -> {
            String instanceId = resolveInstanceId(context);
            if (instanceId == null) {
                log.warn("Action 执行时无法获取 sessionId: rule={}", rule.getDisplayName());
                return;
            }

            CaseContext caseContext = contextManager.getSnapshot(instanceId);
            if (caseContext == null) {
                log.warn("Action 执行时找不到上下文: {}", instanceId);
                return;
            }

            try {
                Map<String, Object> beforeVars = new HashMap<>(caseContext.getVariables());
                ruleEvaluator.executeAction(expression, caseContext);
                persistCtxChanges(instanceId, beforeVars, caseContext);
                log.trace("Action 执行成功: {}", expression);
            } catch (Exception e) {
                log.warn("Action 执行异常: {}", expression, e);
            }
        };
    }

    private Action<String, String> createSystemAction(TransitionRule rule) {
        String systemActionType = rule.getSystemActionType();
        Map<String, Object> params = rule.getSystemActionParams();
        return context -> {
            String instanceId = resolveInstanceId(context);
            if (instanceId == null) {
                log.warn("系统Action 执行时无法获取 sessionId: type={}", systemActionType);
                return;
            }
            try {
                log.debug("📤 系统动作: sessionId={}, type={}, params={}",
                    instanceId, systemActionType, params);
            } catch (Exception e) {
                log.warn("系统Action 执行异常: {}", systemActionType, e);
            }
        };
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 StateContext 的 extendedState 中解析 sessionId。
     *
     * <p>Guard / Action 的 lambda 参数是 {@link StateContext}，不是 StateMachineContext。</p>
     */
    private String resolveInstanceId(StateContext<String, String> context) {
        StateMachine<String, String> sm = context.getStateMachine();
        if (sm == null) return null;

        Map<Object, Object> vars = sm.getExtendedState().getVariables();
        if (vars != null) {
            Object id = vars.get("sessionId");
            if (id != null) return id.toString();
        }

        String smId = sm.getId();
        if (smId != null && !"null".equals(smId)) {
            return smId;
        }
        return null;
    }

    private void persistCtxChanges(String instanceId,
                                   Map<String, Object> beforeVars,
                                   CaseContext ctx) {
        if (ctx == null) return;

        Map<String, Object> afterVars = ctx.getVariables();
        Map<String, Object> changes = new LinkedHashMap<>();

        for (Map.Entry<String, Object> e : afterVars.entrySet()) {
            Object before = beforeVars.get(e.getKey());
            if (!Objects.equals(before, e.getValue())) {
                changes.put(e.getKey(), e.getValue());
            }
        }
        for (String k : beforeVars.keySet()) {
            if (!afterVars.containsKey(k)) {
                changes.put(k, null);
            }
        }

        if (changes.isEmpty()) {
            log.trace("Action 无变量变更: session={}", instanceId);
            return;
        }

        long version = ctx.getVersion();
        boolean ok = contextManager.batchSetVarsWithVersion(
            instanceId, changes, "SYSTEM", version);

        if (!ok) {
            CaseContext latest = contextManager.getSnapshot(instanceId);
            if (latest != null) {
                ok = contextManager.batchSetVarsWithVersion(
                    instanceId, changes, "SYSTEM", latest.getVersion());
            }
        }

        if (ok) {
            log.debug("Action 上下文变更已写入: session={}, changes={}",
                instanceId, changes.keySet());
        } else {
            log.warn("Action 上下文变更写入失败（重试后仍失败）: session={}, changes={}",
                instanceId, changes.keySet());
        }
    }

    // ==================== 静态辅助方法 ====================

    public static StateMachineBuilder.Builder<String, String> createBuilder() {
        return StateMachineBuilder.builder();
    }
}

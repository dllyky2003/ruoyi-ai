package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;

import com.ruoyi.ems.rule.IRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CaseStateMachineDefinitionLoader {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EmsContextManager contextManager;

    @Autowired
    private IRuleEvaluator ruleEvaluator;

    private final Map<String, StateMachineBuilder.Builder<String, String>> definitionCache = new ConcurrentHashMap<>();
    private final Map<String, Object> buildLocks = new ConcurrentHashMap<>();

    // ==================== 核心方法 ====================

    public StateMachineBuilder.Builder<String, String> loadDefinition(ResolvedCaseTypeConfig config) {
        String caseType = config.getCode();

        return definitionCache.computeIfAbsent(caseType, key -> {
            Object lock = buildLocks.computeIfAbsent(key, k -> new Object());
            synchronized (lock) {
                if (definitionCache.containsKey(key)) {
                    return definitionCache.get(key);
                }
                log.info("📝 加载状态机定义: {} (状态: {}, 转移: {})",
                    caseType, config.getStates().size(), config.getTransitions().size());
                try {
                    StateMachineBuilder.Builder<String, String> builder = doLoadDefinition(config);
                    definitionCache.put(key, builder);
                    buildLocks.remove(key);
                    return builder;
                } catch (Exception e) {
                    log.error("❌ 状态机定义加载失败: {}", caseType, e);
                    throw new RuntimeException("状态机定义加载失败: " + caseType, e);
                }
            }
        });
    }

    private StateMachineBuilder.Builder<String, String> doLoadDefinition(ResolvedCaseTypeConfig config) throws Exception {
        StateMachineBuilder.Builder<String, String> builder = StateMachineBuilder.builder();

        // 1. 配置状态
        configureStates(builder, config);

        // 2. 配置转移
        configureTransitions(builder, config);

        return builder;
    }

    // ==================== 状态配置 ====================

    private void configureStates(StateMachineBuilder.Builder<String, String> builder,
                                 ResolvedCaseTypeConfig config) throws Exception {
        var stateConfigurer = builder.configureStates();
        var stateBuilder = stateConfigurer.withStates()
            .initial(config.getInitialState());

        // 使用 Set 而非 List
        Set<String> stateSet = Set.copyOf(config.getStates());
        stateBuilder.states(stateSet);

        // 逐个添加终止状态
        for (String endState : config.getEndStates()) {
            stateBuilder.end(endState);
        }
    }

    // ==================== 转移配置 ====================

    private void configureTransitions(StateMachineBuilder.Builder<String, String> builder,
                                      ResolvedCaseTypeConfig config) throws Exception {
        var transitionConfigurer = builder.configureTransitions();

        for (TransitionRule rule : config.getTransitions()) {
            if (!rule.isEnabled()) {
                continue;
            }

            // 【关键修正】使用 var 类型推断，避免显式引用 ExternalTransitionConfigurer
            var transition = transitionConfigurer.withExternal()
                .source(rule.getSource())
                .target(rule.getTarget())
                .event(rule.getEvent());

            // 添加 Guard（条件）
            if (rule.hasCondition()) {
                transition.guard(createGuard(rule));
            }

            // 添加 Action（执行）
            if (rule.hasAction()) {
                transition.action(createAction(rule));
            }

            // 如果有系统动作类型，额外添加系统动作 Action
            if (rule.getSystemActionType() != null && !rule.getSystemActionType().isEmpty()) {
                transition.action(createSystemAction(rule));
            }
        }
    }

    // ==================== Guard 创建 ====================

    private Guard<String, String> createGuard(TransitionRule rule) {
        String expression = rule.getCondition();

        return context -> {
            String instanceId = context.getStateMachine().getId();
            CaseContext caseContext = contextManager.getSnapshot(instanceId);

            if (caseContext == null) {
                log.warn("Guard 执行时找不到上下文: {}, 使用默认值 false", instanceId);
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

    // ==================== Action 创建 ====================

    private Action<String, String> createAction(TransitionRule rule) {
        String expression = rule.getAction();

        return context -> {
            String instanceId = context.getStateMachine().getId();
            CaseContext caseContext = contextManager.getSnapshot(instanceId);

            if (caseContext == null) {
                log.warn("Action 执行时找不到上下文: {}", instanceId);
                return;
            }

            try {
                ruleEvaluator.executeAction(expression, caseContext);
                contextManager.update(instanceId, caseContext);
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
            String instanceId = context.getStateMachine().getId();
            CaseContext caseContext = contextManager.getSnapshot(instanceId);

            if (caseContext == null) {
                log.warn("系统Action 执行时找不到上下文: {}", instanceId);
                return;
            }

            try {
                log.debug("📤 系统动作: type={}, params={}", systemActionType, params);
            } catch (Exception e) {
                log.warn("系统Action 执行异常: {}", systemActionType, e);
            }
        };
    }

    // ==================== 缓存管理 ====================

    public boolean isLoaded(String caseType) {
        return definitionCache.containsKey(caseType);
    }

    public void clearCache() {
        definitionCache.clear();
        buildLocks.clear();
        log.info("🔄 状态机定义缓存已清除");
    }

    public void clearCache(String caseType) {
        definitionCache.remove(caseType);
        buildLocks.remove(caseType);
        log.info("🔄 清除案例定义缓存: {}", caseType);
    }

    public Map<String, String> getCacheStats() {
        Map<String, String> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, StateMachineBuilder.Builder<String, String>> entry : definitionCache.entrySet()) {
            stats.put(entry.getKey(), "已加载");
        }
        return stats;
    }
}

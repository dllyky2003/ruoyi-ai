package com.ruoyi.ems.engine;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.rule.IRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
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
    // 每个 caseType 单独锁对象，避免全局锁竞争
    private final Map<String, Object> buildLocks = new ConcurrentHashMap<>();

    // ==================== 核心方法（已修复递归更新问题） ====================

    /**
     * 获取或创建状态机构建器（线程安全，使用双重检查锁）
     */
    public StateMachineBuilder.Builder<String, String> getOrCreateBuilder(ResolvedCaseTypeConfig config) {
        String caseType = config.getCode(); // 作为缓存键

        // 第一步：无锁快速检查
        StateMachineBuilder.Builder<String, String> builder = builderCache.get(caseType);
        if (builder != null) {
            return builder;
        }

        // 第二步：获取锁（每个 caseType 独立锁）
        Object lock = buildLocks.computeIfAbsent(caseType, k -> new Object());
        synchronized (lock) {
            // 第三步：双重检查，防止其他线程已经构建完成
            builder = builderCache.get(caseType);
            if (builder != null) {
                return builder;
            }

            // 第四步：真正构建
            log.info("🏗️ 构建状态机定义: {} (状态: {}, 转移: {})",
                caseType, config.getStates().size(), config.getTransitions().size());
            builder = buildStateMachine(config);

            // 第五步：放入缓存
            builderCache.put(caseType, builder);
            // 可选：移除锁对象，节省内存（但保留也无妨）
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

        CaseContext context = contextManager.getSnapshot(caseInstanceId);
        if (context != null && context.getCurrentState() != null) {
            try {
                String savedState = context.getCurrentState();
                if (config.getStates().contains(savedState)) {
                    stateMachine.getStateMachineAccessor().doWithAllRegions(access -> {
                        access.resetStateMachine(
                            new DefaultStateMachineContext<>(savedState, null, null, null, null)
                        );
                    });
                    log.debug("📂 状态机恢复: {} -> {}", caseInstanceId, savedState);
                }
            } catch (Exception e) {
                log.warn("状态恢复失败，使用初始状态: {}", e.getMessage());
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

    // ==================== 私有构建方法（保持不变） ====================

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
                .target(rule.getTarget())
                .event(rule.getEvent());

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

    // ==================== Guard 和 Action 创建（保持不变） ====================

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

    // ==================== 静态辅助方法 ====================

    public static StateMachineBuilder.Builder<String, String> createBuilder() {
        return StateMachineBuilder.builder();
    }
}

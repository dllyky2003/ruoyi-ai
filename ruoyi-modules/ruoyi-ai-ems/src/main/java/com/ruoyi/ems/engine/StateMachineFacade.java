package com.ruoyi.ems.engine;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.rule.IRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class StateMachineFacade {

    private final Map<String, StateMachine<String, String>> machineCache = new ConcurrentHashMap<>();

    private final StateMachineBuilderFactory builderFactory;
    private final RedisStatePersister persister;
    private final EmsContextManager contextManager;
    private final IRuleEvaluator ruleEvaluator;

    public StateMachineFacade(StateMachineBuilderFactory builderFactory,
                              RedisStatePersister persister,
                              EmsContextManager contextManager,
                              IRuleEvaluator ruleEvaluator) {
        this.builderFactory = builderFactory;
        this.persister = persister;
        this.contextManager = contextManager;
        this.ruleEvaluator = ruleEvaluator;
    }

    /**
     * 获取或创建状态机实例（线程安全，避免递归更新）
     */
    public StateMachine<String, String> getOrCreateMachine(String instanceId, ResolvedCaseTypeConfig config) {
        StateMachine<String, String> sm = machineCache.get(instanceId);
        if (sm == null) {
            synchronized (this) {
                sm = machineCache.get(instanceId);
                if (sm == null) {
                    StateMachineBuilder.Builder<String, String> builder = builderFactory.getOrCreateBuilder(config);
                    sm = builder.build();
                    sm.start();
                    restoreState(sm, instanceId);
                    machineCache.put(instanceId, sm);
                }
            }
        }
        return sm;
    }

    /**
     * 获取当前状态
     */
    public String getCurrentState(String instanceId, ResolvedCaseTypeConfig config) {
        StateMachine<String, String> sm = getOrCreateMachine(instanceId, config);
        return sm.getState().getId();
    }

    /**
     * 执行状态转移
     * @return 新状态，如果转移失败则返回 null
     */
    public String transition(String instanceId, ResolvedCaseTypeConfig config, String eventCode) {
        StateMachine<String, String> sm = getOrCreateMachine(instanceId, config);
        boolean accepted = sm.sendEvent(eventCode);

        if (!accepted) {
            log.warn("状态机拒绝事件: {}, 当前状态: {}", eventCode, sm.getState().getId());
            return null;
        }

        String newState = sm.getState().getId();
        persistState(sm, instanceId);
        return newState;
    }

    /**
     * 构建状态机 Builder（直接委托给 builderFactory）
     */
    private StateMachineBuilder.Builder<String, String> buildStateMachineBuilder(ResolvedCaseTypeConfig config) {
        return builderFactory.getOrCreateBuilder(config);
    }

    private void restoreState(StateMachine<String, String> sm, String instanceId) {
        try {
            persister.restore(sm, instanceId);
        } catch (Exception e) {
            log.debug("状态恢复失败（首次启动正常）: {}", e.getMessage());
        }
    }

    private void persistState(StateMachine<String, String> sm, String instanceId) {
        try {
            persister.persist(sm, instanceId);
        } catch (Exception e) {
            log.warn("状态持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 清除缓存（状态机实例重建）
     */
    public void clearCache(String instanceId) {
        machineCache.remove(instanceId);
    }
}

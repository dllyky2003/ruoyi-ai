package com.ruoyi.ems.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 状态机持久化器
 *
 * 实现 Spring StateMachine 的 StateMachinePersister 接口，
 * 将状态机实例的当前状态持久化到 Redis，支持服务器重启后的状态恢复。
 *
 * 设计原则：
 * 1. 只持久化状态 ID（不持久化扩展状态），因为业务变量由 CaseContext 独立管理
 * 2. 使用 String 序列化（简单、可读、无版本兼容问题）
 * 3. 支持 TTL 自动过期
 *
 * 与 CaseContext 的关系：
 * - 状态机持久化：只存状态 ID
 * - 上下文持久化：存完整的 CaseContext（变量）
 * - 两者通过 caseInstanceId 关联
 *
 * @author RuoYi EMS
 * @version 2.0 (修正泛型参数：S=String, E=String, T=String)
 */
@Component
@Slf4j
public class RedisStatePersister implements StateMachinePersister<String, String, String> {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Redis Key 前缀
     */
    private static final String KEY_PREFIX = "acls:sm:state:";

    /**
     * 默认 TTL（2小时）
     */
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);

    // ==================== 核心接口方法 ====================

    /**
     * 持久化状态机当前状态
     *
     * @param stateMachine 状态机实例
     * @param contextObj   上下文对象（caseInstanceId）
     * @throws Exception 持久化异常
     */
    @Override
    public void persist(StateMachine<String, String> stateMachine, String contextObj) throws Exception {
        if (stateMachine == null || contextObj == null) {
            log.warn("持久化跳过：状态机或上下文为空");
            return;
        }

        String caseInstanceId = contextObj;
        String currentState = stateMachine.getState().getId();

        if (currentState == null) {
            log.warn("持久化跳过：当前状态为空, 案例: {}", caseInstanceId);
            return;
        }

        String key = buildKey(caseInstanceId);
        redisTemplate.opsForValue().set(key, currentState, DEFAULT_TTL);

        log.trace("💾 状态持久化: {} → {} (案例: {})", key, currentState, caseInstanceId);
    }

    /**
     * 恢复状态机状态
     *
     * @param stateMachine 状态机实例
     * @param contextObj   上下文对象（caseInstanceId）
     * @return 恢复后的状态机实例
     * @throws Exception 恢复异常
     */
    @Override
    @SuppressWarnings("deprecation")
    public StateMachine<String, String> restore(StateMachine<String, String> stateMachine, String contextObj) throws Exception {
        if (stateMachine == null || contextObj == null) {
            log.warn("恢复跳过：状态机或上下文为空");
            return stateMachine;
        }

        String caseInstanceId = contextObj;
        String key = buildKey(caseInstanceId);
        String savedState = redisTemplate.opsForValue().get(key);

        if (savedState == null) {
            log.trace("📂 无已保存状态，使用初始状态 (案例: {})", caseInstanceId);
            return stateMachine;
        }

        // 恢复状态
        try {
            stateMachine.getStateMachineAccessor().doWithAllRegions(access -> {
                // resetStateMachine 虽标记为过时，但仍可用
                access.resetStateMachine(
                    new DefaultStateMachineContext<>(savedState, null, null, null, null)
                );
            });
            log.info("📂 状态恢复成功: {} → {} (案例: {})", key, savedState, caseInstanceId);
        } catch (Exception e) {
            log.warn("状态恢复失败: {}, 错误: {}", savedState, e.getMessage());
            // 不抛出异常，使用默认初始状态
        }

        return stateMachine;
    }

    // ==================== 扩展方法 ====================

    /**
     * 删除已保存的状态
     */
    public void delete(String caseInstanceId) {
        if (caseInstanceId == null) return;
        String key = buildKey(caseInstanceId);
        Boolean deleted = redisTemplate.delete(key);
        if (deleted) {
            log.debug("🗑️ 已删除状态: {}", key);
        }
    }

    /**
     * 检查状态是否已保存
     */
    public boolean exists(String caseInstanceId) {
        if (caseInstanceId == null) return false;
        String key = buildKey(caseInstanceId);
        return redisTemplate.hasKey(key);
    }

    /**
     * 获取已保存的状态（不恢复）
     */
    public String getSavedState(String caseInstanceId) {
        if (caseInstanceId == null) return null;
        String key = buildKey(caseInstanceId);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 更新 TTL
     */
    public void refreshTTL(String caseInstanceId) {
        if (caseInstanceId == null) return;
        String key = buildKey(caseInstanceId);
        redisTemplate.expire(key, DEFAULT_TTL);
    }

    // ==================== 私有辅助方法 ====================

    private String buildKey(String caseInstanceId) {
        return KEY_PREFIX + caseInstanceId;
    }
}

package com.ruoyi.ems.context;

import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.exception.VariableAccessDeniedException;
import com.ruoyi.ems.exception.VariableValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class EmsContextManager {

    private final Map<String, CaseContext> cache = new ConcurrentHashMap<>();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private VariableMetaManager metaManager;

    private static final String REDIS_KEY_PREFIX = "acls:ctx:";
    private static final Duration TTL = Duration.ofHours(2);

    /**
     * 获取或创建上下文
     */
    public CaseContext getOrCreate(String caseInstanceId) {
        return cache.computeIfAbsent(caseInstanceId, id -> {
            CaseContext ctx = (CaseContext) redisTemplate.opsForValue()
                .get(REDIS_KEY_PREFIX + id);
            if (ctx != null) {
                log.debug("从 Redis 恢复上下文: {}", id);
                return ctx;
            }
            ctx = new CaseContext();
            ctx.setCaseInstanceId(id);
            ctx.setSessionStartTime(LocalDateTime.now());
            ctx.setLastUpdateTime(LocalDateTime.now());
            return ctx;
        });
    }

    /**
     * 更新上下文（持久化）
     */
    public void update(String caseInstanceId, CaseContext context) {
        context.setLastUpdateTime(LocalDateTime.now());
        cache.put(caseInstanceId, context);

        CompletableFuture.runAsync(() -> {
            try {
                redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + caseInstanceId, context, TTL);
            } catch (Exception e) {
                log.warn("Redis 持久化失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 安全修改变量（含权限校验）
     */
    public void setVar(String caseInstanceId, String varName, Object value, String writer) {
        CaseContext ctx = getOrCreate(caseInstanceId);
        String caseType = ctx.getCaseType();

        // 权限检查
        if (!metaManager.checkWritePermission(caseType, varName, writer)) {
            throw new VariableAccessDeniedException(
                String.format("变量 %s 不允许 %s 写入", varName, writer)
            );
        }

        // 值校验
        if (!metaManager.validateValue(caseType, varName, value)) {
            throw new VariableValidationException(
                String.format("变量 %s 的值 %s 不合法", varName, value)
            );
        }

        ctx.setVar(varName, value, writer);
        update(caseInstanceId, ctx);
    }

    /**
     * 批量安全赋值（用于槽位映射）
     */
    public void batchSetVars(String caseInstanceId, Map<String, Object> varMap, String writer) {
        for (Map.Entry<String, Object> entry : varMap.entrySet()) {
            try {
                setVar(caseInstanceId, entry.getKey(), entry.getValue(), writer);
            } catch (Exception e) {
                log.warn("变量 {} 赋值失败: {}", entry.getKey(), e.getMessage());
            }
        }
    }
    /**
     * 清除指定实例的上下文（本地缓存 + Redis）
     */
    public void clearContext(String caseInstanceId) {
        // 1. 清除本地缓存
        cache.remove(caseInstanceId);

        // 2. 清除 Redis 中的上下文
        try {
            String redisKey = REDIS_KEY_PREFIX + caseInstanceId;
            redisTemplate.delete(redisKey);
            log.debug("Redis 上下文已清除: {}", redisKey);
        } catch (Exception e) {
            log.warn("清除 Redis 上下文失败: {}", e.getMessage());
        }

        log.debug("上下文已清除: {}", caseInstanceId);
    }
    public CaseContext getSnapshot(String caseInstanceId) {
        return cache.get(caseInstanceId);
    }
}

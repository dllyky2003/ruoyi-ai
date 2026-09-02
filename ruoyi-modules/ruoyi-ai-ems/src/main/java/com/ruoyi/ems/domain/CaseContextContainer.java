package com.ruoyi.ems.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 案例上下文容器
 * 管理 CaseContext 的生命周期，支持内存缓存和 Redis 持久化
 */
@Component
@Slf4j
public class CaseContextContainer {

    // 内存缓存：caseInstanceId → CaseContext
    private final Map<String, CaseContext> contextMap = new ConcurrentHashMap<>();
    // 最后访问时间戳（用于过期清理）
    private final Map<String, Long> lastAccessMap = new ConcurrentHashMap<>();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 核心读写方法 ====================

    /**
     * 获取或创建上下文
     */
    public CaseContext getOrCreate(String caseInstanceId) {
        return contextMap.computeIfAbsent(caseInstanceId, id -> {
            // 1. 尝试从 Redis 恢复
            CaseContext cached = (CaseContext) redisTemplate.opsForValue().get("ctx:" + id);
            if (cached != null) {
                log.debug("📂 从 Redis 恢复上下文: {}", id);
                contextMap.put(id, cached);
                lastAccessMap.put(id, System.currentTimeMillis());
                return cached;
            }

            // 2. 创建新上下文
            CaseContext ctx = new CaseContext();
            ctx.setCaseInstanceId(id);
            ctx.setLastUpdateTime(LocalDateTime.now());
            ctx.setSessionStartTime(LocalDateTime.now());
            ctx.setVariables(new ConcurrentHashMap<>());
            ctx.setChangeLog(new ArrayList<>());
            // 可设置默认变量，例如：
            ctx.setVar("elapsedSecondsSinceLastIntervention", 0, "SYSTEM");
            ctx.setVar("sessionStartTime", LocalDateTime.now(), "SYSTEM");

            log.debug("📂 新建上下文: {}", id);
            lastAccessMap.put(id, System.currentTimeMillis());
            return ctx;
        });
    }

    /**
     * 更新上下文（写入内存 + 异步持久化到 Redis）
     */
    public void update(String caseInstanceId, CaseContext context) {
        if (context == null) {
            log.warn("更新上下文失败: 上下文为空");
            return;
        }

        // 更新最后更新时间
        LocalDateTime now = LocalDateTime.now();
        context.setLastUpdateTime(now);

        // 计算距上次更新经过的秒数（用于超时监控）
        long elapsedSeconds = Duration.between(
            context.getLastUpdateTime(),
            now
        ).getSeconds();
        context.setVar("elapsedSecondsSinceLastIntervention", (int) elapsedSeconds, "SYSTEM");

        // 更新内存缓存
        contextMap.put(caseInstanceId, context);
        lastAccessMap.put(caseInstanceId, System.currentTimeMillis());

        // 异步持久化到 Redis（简化版，直接使用同步，或可改为异步）
        try {
            redisTemplate.opsForValue().set(
                "ctx:" + caseInstanceId,
                context,
                1,
                TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Redis 持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 获取上下文快照（只读）
     */
    public CaseContext getSnapshot(String caseInstanceId) {
        CaseContext ctx = contextMap.get(caseInstanceId);
        if (ctx != null) {
            lastAccessMap.put(caseInstanceId, System.currentTimeMillis());
        }
        return ctx;
    }

    /**
     * 删除上下文（清理内存和 Redis）
     */
    public void remove(String caseInstanceId) {
        contextMap.remove(caseInstanceId);
        lastAccessMap.remove(caseInstanceId);
        try {
            redisTemplate.delete("ctx:" + caseInstanceId);
        } catch (Exception e) {
            log.warn("Redis 删除失败: {}", e.getMessage());
        }
        log.debug("🗑️ 已删除上下文: {}", caseInstanceId);
    }

    // ==================== 定时清理 ====================

    /**
     * 清理超过30分钟未访问的上下文（释放内存）
     */
    @Scheduled(fixedRate = 60000)
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        long expireThreshold = 30 * 60 * 1000; // 30分钟

        contextMap.entrySet().removeIf(entry -> {
            Long lastAccess = lastAccessMap.get(entry.getKey());
            if (lastAccess != null && (now - lastAccess) > expireThreshold) {
                log.debug("🧹 清理过期上下文: {}", entry.getKey());
                lastAccessMap.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断上下文是否存在
     */
    public boolean exists(String caseInstanceId) {
        return contextMap.containsKey(caseInstanceId);
    }

    /**
     * 获取所有上下文的 ID 列表
     */
    public Set<String> getAllInstanceIds() {
        return new HashSet<>(contextMap.keySet());
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return contextMap.size();
    }

    /**
     * 手动刷新 TTL（延长过期时间）
     */
    public void refreshTTL(String caseInstanceId) {
        if (caseInstanceId != null) {
            lastAccessMap.put(caseInstanceId, System.currentTimeMillis());
        }
    }
}

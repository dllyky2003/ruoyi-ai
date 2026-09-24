package com.ruoyi.ems.room.protocol;

import com.ruoyi.ems.room.protocol.dto.CmdResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契约 V1.0.0 §5.4 幂等缓存。
 *
 * <p>同一 attemptId + idempotencyKey 的第二次到达直接返回原结果，
 * 不重复执行状态机。按 attemptId 分桶，避免跨会话污染。</p>
 *
 * <p>内存版实现，仅供阶段 5 验收。生产版应落 Redis。</p>
 */
@Slf4j
@Component
public class IdempotencyCache {

    /** 缓存保留时长，超出后允许重放（防内存泄漏） */
    private static final Duration TTL = Duration.ofHours(2);

    /** attemptId -> (idempotencyKey -> entry) */
    private final Map<String, Map<String, Entry>> store = new ConcurrentHashMap<>();

    public CmdResultMessage get(String attemptId, String idempotencyKey) {
        Map<String, Entry> bucket = store.get(attemptId);
        if (bucket == null) return null;
        Entry e = bucket.get(idempotencyKey);
        if (e == null) return null;
        if (Instant.now().isAfter(e.expireAt)) {
            bucket.remove(idempotencyKey);
            return null;
        }
        return e.result;
    }

    public void put(String attemptId, String idempotencyKey, CmdResultMessage result) {
        store.computeIfAbsent(attemptId, k -> new ConcurrentHashMap<>())
            .put(idempotencyKey, new Entry(result, Instant.now().plus(TTL)));
    }

    /** 尝试结束时清理整个 attempt 桶 */
    public void evict(String attemptId) {
        store.remove(attemptId);
    }

    private record Entry(CmdResultMessage result, Instant expireAt) {}
}

package com.ruoyi.ems.room.model;


import com.ruoyi.ems.room.dto.VitalValueDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生理体征缓存。
 * <p>
 * 数据结构：emsSessionId → (variableName → CacheEntry)
 * Unity 每次上报 vitals_update 或响应 vitals_query 时更新。
 * 状态机条件评估时从这里读值，避免每次同步等 Unity。
 * </p>
 */
@Slf4j
@Component
public class PhysiologyVariableCache {

    /** 默认 TTL：超过该毫秒数的值被视为"陈旧" */
    public static final long DEFAULT_STALE_MS = 2000L;

    private record CacheEntry(VitalValueDto value, long updatedAt) {}

    private final Map<String, Map<String, CacheEntry>> cache = new ConcurrentHashMap<>();

    // ==================== 写入 ====================

    /**
     * 更新单个变量。
     */
    public void update(String emsSessionId, String varName, VitalValueDto value) {
        if (emsSessionId == null || varName == null || value == null) return;

        cache.computeIfAbsent(emsSessionId, k -> new ConcurrentHashMap<>())
            .put(varName, new CacheEntry(value, System.currentTimeMillis()));
    }

    /**
     * 批量更新。vitals_response / vitals_update 的 values 整体写入。
     */
    public void updateAll(String emsSessionId, Map<String, VitalValueDto> values) {
        if (emsSessionId == null || values == null || values.isEmpty()) return;

        Map<String, CacheEntry> target = cache.computeIfAbsent(
            emsSessionId, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        values.forEach((name, val) -> {
            if (name != null && val != null) {
                target.put(name, new CacheEntry(val, now));
            }
        });
    }

    // ==================== 读取 ====================

    public Optional<VitalValueDto> get(String emsSessionId, String varName) {
        Map<String, CacheEntry> map = cache.get(emsSessionId);
        if (map == null) return Optional.empty();
        CacheEntry entry = map.get(varName);
        return entry != null ? Optional.of(entry.value()) : Optional.empty();
    }

    /**
     * 读取原始值（只取 value 字段）。
     */
    public Optional<Object> getValue(String emsSessionId, String varName) {
        return get(emsSessionId, varName).map(VitalValueDto::getValue);
    }

    /**
     * 读取并判断是否陈旧。超过 maxAgeMs 的值视为过期，返回 empty。
     */
    public Optional<VitalValueDto> getIfFresh(String emsSessionId, String varName, long maxAgeMs) {
        Map<String, CacheEntry> map = cache.get(emsSessionId);
        if (map == null) return Optional.empty();
        CacheEntry entry = map.get(varName);
        if (entry == null) return Optional.empty();
        if (System.currentTimeMillis() - entry.updatedAt() > maxAgeMs) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public Map<String, VitalValueDto> snapshot(String emsSessionId) {
        Map<String, CacheEntry> map = cache.get(emsSessionId);
        if (map == null) return Map.of();
        Map<String, VitalValueDto> result = new ConcurrentHashMap<>();
        map.forEach((k, v) -> result.put(k, v.value()));
        return result;
    }

    /**
     * 批量读取多个变量，用于状态机条件评估。
     * 不存在的变量不返回，由调用方决定是否触发查询。
     */
    public Map<String, Object> getValues(String emsSessionId, Iterable<String> varNames) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        Map<String, CacheEntry> map = cache.get(emsSessionId);
        if (map == null) return result;
        for (String name : varNames) {
            CacheEntry e = map.get(name);
            if (e != null) result.put(name, e.value().getValue());
        }
        return result;
    }

    public boolean isFresh(String emsSessionId, String varName, long maxAgeMs) {
        return getIfFresh(emsSessionId, varName, maxAgeMs).isPresent();
    }

    public boolean isEmpty(String emsSessionId) {
        Map<String, CacheEntry> map = cache.get(emsSessionId);
        return map == null || map.isEmpty();
    }

    // ==================== 清理 ====================

    public void clear(String emsSessionId) {
        cache.remove(emsSessionId);
        log.debug("体征缓存已清理: emsSessionId={}", emsSessionId);
    }

    public void clearVar(String emsSessionId, String varName) {
        Map<String, CacheEntry> map = cache.get(emsSessionId);
        if (map != null) map.remove(varName);
    }
}

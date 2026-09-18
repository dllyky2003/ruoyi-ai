package com.ruoyi.ems.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原子动作注册表。
 *
 * <p>原子动作是技能内部的最小执行单元，描述"谁（actor）执行什么 VR 动画、
 * 持续多久、是否需要口头报告"等执行细节。
 *
 * <p>注意：原子动作<b>不参与契约构建</b>。契约的粒度是"意图 → 技能 → 变量"，
 * 原子动作不提供变量写入/读取信息，属于技能的内部实现细节。
 */
@Slf4j
@Component
public class AtomicActionRegistry {

    /** 原子动作注册表：refAtomic -> 元数据 */
    private final Map<String, AtomicActionMeta> registry = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 是否已加载过，供幂等保护使用 */
    private volatile boolean loaded = false;

    // ====================================================================
    // 加载入口
    // ====================================================================


    public void load() {
        if (loaded) {
            log.debug("AtomicActionRegistry 已加载，跳过重复加载");
            return;
        }
        doLoad();
        loaded = true;
    }

    /**
     * 全量重新加载。
     * 供 CaseConfigLoader.reloadAll() 调用。
     *
     * 注意：原子动作是全局单文件，没有 case 维度，因此没有 reloadCase。
     */
    public synchronized void reloadAll() {
        log.info("🔄 重新加载原子动作注册表（全量）...");
        registry.clear();
        loaded = false;
        doLoad();
        loaded = true;
    }

    // ====================================================================
    // 内部加载逻辑
    // ====================================================================

    private void doLoad() {
        try (InputStream is = new ClassPathResource("config/atomic_actions.json").getInputStream()) {
            Map<String, AtomicActionMeta> map = objectMapper.readValue(
                is, new TypeReference<Map<String, AtomicActionMeta>>() {}
            );
            registry.putAll(map);
            log.info("✅ 已加载 {} 个原子动作定义", registry.size());
        } catch (Exception e) {
            log.warn("⚠️ 未找到 atomic_actions.json，使用空注册表", e);
        }
    }

    // ====================================================================
    // 原有查询接口（保留）
    // ====================================================================

    public AtomicActionMeta getMeta(String refAtomic) {
        return registry.get(refAtomic);
    }

    public int getDuration(String refAtomic) {
        AtomicActionMeta meta = registry.get(refAtomic);
        return meta != null && meta.getDurationSec() != null ? meta.getDurationSec() : 3;
    }

    public boolean isAsync(String refAtomic) {
        AtomicActionMeta meta = registry.get(refAtomic);
        return meta != null && Boolean.TRUE.equals(meta.getAsync());
    }

    public String getVrAnimation(String refAtomic) {
        AtomicActionMeta meta = registry.get(refAtomic);
        return meta != null ? meta.getVrAnimation() : null;
    }

    // ====================================================================
    // ★ 新增：契约诊断用的只读接口
    // ====================================================================

    /**
     * 列出所有原子动作 ID。
     * 用途：诊断——某个技能引用的原子动作是否在注册表中存在。
     */
    public Set<String> getAllActionIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * 取全部原子动作元数据（只读视图）。
     */
    public Map<String, AtomicActionMeta> getAllActions() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * 判断某个原子动作是否存在。
     */
    public boolean exists(String refAtomic) {
        return refAtomic != null && registry.containsKey(refAtomic);
    }

    // ====================================================================
    // 内部数据类（保留）
    // ====================================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtomicActionMeta {
        private String actor;
        private Integer durationSec;
        private String requiresVerbal;
        private Boolean loop;
        private Boolean async;
        private String vrAnimation;
    }
}

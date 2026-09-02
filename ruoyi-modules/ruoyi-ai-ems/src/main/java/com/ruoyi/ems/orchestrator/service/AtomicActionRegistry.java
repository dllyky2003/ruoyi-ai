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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AtomicActionRegistry {

    private final Map<String, AtomicActionMeta> registry = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void load() {
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)  // ★ 允许字段扩展，忽略未知字段
    public static class AtomicActionMeta {
        private String actor;
        private Integer durationSec;
        private String requiresVerbal;
        private Boolean loop;
        // ★ 新增字段，与 atomic_actions.json 保持一致
        private Boolean async;          // 是否异步执行
        private String vrAnimation;     // VR 动画名称
    }
}

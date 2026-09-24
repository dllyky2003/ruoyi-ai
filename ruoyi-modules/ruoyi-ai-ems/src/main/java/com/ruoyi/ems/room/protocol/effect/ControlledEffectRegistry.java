package com.ruoyi.ems.room.protocol.effect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契约 V1.0.0 §7.2 —— 受控效果注册表。
 *
 * <p>Spring 启动时自动收集所有 {@link ControlledEffect} 实现并按
 * effectId 注册。未注册的 effectId 一律拒绝。</p>
 */
@Slf4j
@Component
public class ControlledEffectRegistry {

    private final Map<String, ControlledEffect> effects = new ConcurrentHashMap<>();

    @Autowired
    public ControlledEffectRegistry(List<ControlledEffect> effectList) {
        if (effectList != null) {
            for (ControlledEffect e : effectList) {
                if (e.getEffectId() == null || e.getEffectId().isBlank()) {
                    log.warn("[OVR][Registry] 跳过未声明 effectId 的效果: {}",
                        e.getClass().getSimpleName());
                    continue;
                }
                ControlledEffect prev = effects.put(e.getEffectId(), e);
                if (prev != null) {
                    log.warn("[OVR][Registry] effectId 冲突: {}, 覆盖实现 {} -> {}",
                        e.getEffectId(),
                        prev.getClass().getSimpleName(),
                        e.getClass().getSimpleName());
                } else {
                    log.info("[OVR][Registry] 注册受控效果: effectId={}, impl={}",
                        e.getEffectId(), e.getClass().getSimpleName());
                }
            }
        }
        log.info("[OVR][Registry] 受控效果注册完成，共 {} 个: {}",
            effects.size(), effects.keySet());
    }

    /** 按 effectId 取效果，未注册返回 null */
    public ControlledEffect get(String effectId) {
        return effects.get(effectId);
    }

    /** 所有已注册 effectId（只读） */
    public Set<String> allEffectIds() {
        return Collections.unmodifiableSet(effects.keySet());
    }
}

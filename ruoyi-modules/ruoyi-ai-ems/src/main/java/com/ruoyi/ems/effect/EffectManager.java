package com.ruoyi.ems.effect;

import com.ruoyi.ems.config.loader.PhysiologyConfigLoader;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.OperationEffect;
import com.ruoyi.ems.domain.PhysiologyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class EffectManager {

    // 每个会话的活跃效果列表（包含效果定义、开始时间、当前进度等）
    private final Map<String, List<ActiveEffect>> activeEffects = new ConcurrentHashMap<>();

    // 注入配置加载器以获取效果模板（统一入口 PhysiologyConfigLoader）
    private final PhysiologyConfigLoader configLoader;

    public EffectManager(PhysiologyConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    // ============================================================
    // 3 参版本：最底层实现
    // ============================================================
    /**
     * 触发一个操作效果
     *
     * @param sessionId 会话ID
     * @param caseId    案例ID（用于加载效果配置）
     * @param effectKey 操作标识（如 "OP_ADENOSINE"）
     */
    public void triggerEffect(String sessionId, String caseId, String effectKey) {
        // 防御：caseId 为空时直接跳过
        if (caseId == null || caseId.isBlank()) {
            log.warn("triggerEffect 收到空 caseId，跳过: session={}, effectKey={}",
                sessionId, effectKey);
            return;
        }

        // 从配置中获取效果模板列表
        PhysiologyConfig config = configLoader.loadCase(caseId);
        if (config == null) {
            log.debug("案例 {} 生理配置不存在，跳过效果触发", caseId);
            return;
        }
        if (config.getOperationEffects() == null) {
            log.debug("案例 {} 未配置任何操作效果", caseId);
            return;
        }
        List<OperationEffect> templates = config.getOperationEffects().get(effectKey);
        if (templates == null || templates.isEmpty()) {
            log.debug("操作 {} 未配置效果", effectKey);
            return;
        }

        // 为每个模板创建一个活跃效果实例
        List<ActiveEffect> newEffects = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (OperationEffect template : templates) {
            ActiveEffect effect = new ActiveEffect();
            effect.setTarget(template.getTarget());
            effect.setDelta((int) template.getDelta());

            // duration / recovery 单位：秒 → 毫秒。加 null 防御。
            Integer durationSec = template.getDuration();
            effect.setDuration(durationSec != null ? durationSec * 1000L : 0L);

            Integer recoverySec = template.getRecovery();
            effect.setRecovery(recoverySec != null ? recoverySec * 1000L : 0L);

            effect.setCurve(template.getCurve());
            effect.setStartTime(now);
            newEffects.add(effect);
        }

        // 添加到该会话的活跃列表
        activeEffects.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(newEffects);
        log.info("✅ 效果已触发: session={}, effect={}, count={}",
            sessionId, effectKey, newEffects.size());
    }

    // ============================================================
    // 2 参版本：便捷重载
    // ============================================================
    /**
     * 触发效果（简化版本，自动提取 sessionId 和 caseType）。
     *
     * <p>防御：caseType 未设置时不抛 NPE，只打 WARN 并跳过。
     * 场景：测试 / 早期初始化阶段上下文可能尚未设置 caseType。
     */
    public void triggerEffect(String effectId, CaseContext context) {
        if (context == null) {
            log.warn("CaseContext 为 null，跳过效果触发: effectId={}", effectId);
            return;
        }
        String sessionId = context.getSessionId();
        String caseId = context.getCaseType();

        if (caseId == null || caseId.isBlank()) {
            log.warn("caseType 未设置，跳过效果触发: sessionId={}, effectId={}",
                sessionId, effectId);
            return;
        }
        triggerEffect(sessionId, caseId, effectId);
    }

    // ============================================================
    // 效果修正量计算
    // ============================================================
    /**
     * 获取当前时刻所有活跃效果的修正量（按参数汇总）
     *
     * @param sessionId 会话ID
     * @return Map<参数名, 修正量>（例如 phys.heartRate → -20）
     */
    public Map<String, Integer> getCurrentModifiers(String sessionId) {
        Map<String, Integer> modifiers = new HashMap<>();
        List<ActiveEffect> effects = activeEffects.get(sessionId);
        if (effects == null || effects.isEmpty()) {
            return modifiers;
        }

        long now = System.currentTimeMillis();
        Iterator<ActiveEffect> it = effects.iterator();
        while (it.hasNext()) {
            ActiveEffect effect = it.next();

            // 已过期：duration == 0 视为立即结束
            if (effect.getDuration() > 0
                && now > effect.getStartTime() + effect.getDuration()) {
                it.remove();
                log.debug("效果已过期: target={}", effect.getTarget());
                continue;
            }

            // 计算当前时刻的修正量（根据曲线类型）
            int currentDelta = calculateCurrentDelta(effect, now);
            if (currentDelta != 0) {
                modifiers.merge(effect.getTarget(), currentDelta, Integer::sum);
            }
        }
        return modifiers;
    }

    /**
     * 根据曲线类型计算当前修正量
     */
    private int calculateCurrentDelta(ActiveEffect effect, long now) {
        long elapsed = now - effect.getStartTime();
        long totalDuration = effect.getDuration();

        // duration 缺省或非法：直接返回原始 delta，不做插值
        if (totalDuration <= 0) {
            return effect.getDelta();
        }

        if (elapsed <= 0) {
            return effect.getDelta();
        }

        double progress = (double) elapsed / totalDuration;
        if (progress >= 1.0) {
            return 0;
        }

        // 曲线类型（null 视为默认线性）
        String curve = effect.getCurve();
        if (curve == null || curve.isBlank()) {
            curve = "DEFAULT_LINEAR";
        }

        double factor;
        switch (curve) {
            case "INSTANT_DROP_LINEAR_RECOVER":
                if (progress <= 0.1) {
                    factor = 1.0;
                } else {
                    factor = 1.0 - (progress - 0.1) / 0.9;
                }
                break;

            case "LINEAR_DECAY_LINEAR_RECOVER":
                factor = 1.0 - progress;
                break;

            case "DEFAULT_LINEAR":
            default:
                factor = 1.0 - progress;
                break;
        }

        factor = Math.max(0, Math.min(1, factor));
        return (int) Math.round(effect.getDelta() * factor);
    }

    /**
     * 清除会话的所有效果（会话结束时调用）
     */
    public void clearEffects(String sessionId) {
        activeEffects.remove(sessionId);
        log.debug("清除会话 {} 的效果", sessionId);
    }

    // ============================================================
    // 内部类：活跃效果实例
    // ============================================================
    @lombok.Data
    private static class ActiveEffect {
        private String target;
        private int delta;
        private long duration;
        private long recovery;
        private String curve;
        private long startTime;
    }
}

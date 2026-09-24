package com.ruoyi.ems.effect;

import com.ruoyi.ems.config.loader.PhysiologyConfigLoader;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.OperationEffect;
import com.ruoyi.ems.domain.PhysiologyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生理效果管理器。
 *
 * <p>职责：接收"操作/药物"触发，按条件查表得到效果模板，转成
 * {@link ActiveEffect} 存入会话级列表，由 {@link #getCurrentModifiers(String)}
 * 每帧计算当前修正量。</p>
 *
 * <p>★ 2026-09-23 扩展：支持按 condition 分支查表，用于"正确/错误/延时"
 * 三种结果对应不同体征反应。查找顺序：
 * <ol>
 *   <li>{@code effectKey__condition}（如 {@code adenosine_6mg__wrong_critical}）</li>
 *   <li>{@code effectKey}（基础 key，兼容旧配置）</li>
 *   <li>{@code effectKey__correct}（兜底到正确分支）</li>
 * </ol>
 *
 * <p>★ 2026-09-23 螺旋 3：新增 {@link EffectBranch} 维度。branch 由学员操作质量决定
 * （NORMAL / ERROR / DELAYED），用于审计与日志；condition 仍由调用方决定，
 * ERROR/DELAYED 分支可直接用 {@link EffectBranch#getDefaultCondition()} 作为 condition。</p>
 *
 * <p>★ 效果类型：
 * <ul>
 *   <li>{@code DELTA}（默认）：数值型变量加减，走曲线插值</li>
 *   <li>{@code ENUM_DOWN} / {@code ENUM_UP}：有序枚举位移（如 phys.consciousness），
 *       通过特殊 key {@code target + "__enum_shift"} 向上游传递 ±steps</li>
 * </ul>
 */
@Component
@Slf4j
public class EffectManager {

    /** 每个会话的活跃效果列表 */
    private final Map<String, List<ActiveEffect>> activeEffects = new ConcurrentHashMap<>();

    private final PhysiologyConfigLoader configLoader;

    public EffectManager(PhysiologyConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    // ============================================================
    // 入口 0（螺旋 3 新增）：带 branch + condition
    // ============================================================

    /**
     * 触发效果（带 branch + condition）。
     *
     * <p>branch 仅用于审计/日志，不参与查表（查表仍按 condition）。
     * 调用方如需按 branch 分派效果，应自行把 branch 映射到 condition
     * （见 {@link EffectBranch#getDefaultCondition()}）。</p>
     *
     * @param effectKey 效果标识，如 {@code adenosine_6mg} / {@code CARDIOVERSION_100J}
     * @param condition 条件分支，{@code correct / wrong_minor / wrong_critical / delayed}；
     *                  传 null 或空串表示只查基础 key
     * @param branch    学员操作分支；传 null 表示未指定（向后兼容）
     * @param context   案例上下文（提供 sessionId + caseType）
     */
    public void triggerEffect(String effectKey, String condition,
                              EffectBranch branch, CaseContext context) {
        if (context == null) {
            log.warn("CaseContext 为 null，跳过: effectKey={}, branch={}", effectKey, branch);
            return;
        }
        String sessionId = context.getSessionId();
        String caseId = context.getCaseType();
        if (caseId == null || caseId.isBlank()) {
            log.warn("caseType 未设置，跳过: session={}, effectKey={}, branch={}",
                sessionId, effectKey, branch);
            return;
        }

        PhysiologyConfig config = configLoader.loadCase(caseId);
        if (config == null || config.getOperationEffects() == null) {
            log.debug("案例 {} 生理配置不存在或未配 operation_effects", caseId);
            return;
        }

        Map<String, List<OperationEffect>> all = config.getOperationEffects();
        List<OperationEffect> templates = lookupTemplates(all, effectKey, condition);
        if (templates == null || templates.isEmpty()) {
            log.debug("操作/药物 {} 未配置效果 (condition={}, branch={})",
                effectKey, condition, branch);
            return;
        }

        long now = System.currentTimeMillis();
        List<ActiveEffect> newEffects = buildActiveEffects(templates, now, branch);
        activeEffects.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(newEffects);

        log.info("✅ 效果已触发: session={}, effect={}, condition={}, branch={}, count={}",
            sessionId, effectKey, condition, branch, newEffects.size());
    }

    // ============================================================
    // 入口 1（旧，保留）：带 condition，无 branch
    // ============================================================

    /**
     * 触发效果（带 condition 分支，无 branch）。
     *
     * <p>等价于 {@code triggerEffect(effectKey, condition, null, context)}。</p>
     *
     * @param effectKey 效果标识，如 {@code adenosine_6mg} / {@code CARDIOVERSION_100J}
     * @param condition 条件分支，{@code correct / wrong_minor / wrong_critical / delayed}；
     *                  传 null 或空串表示只查基础 key
     * @param context   案例上下文（提供 sessionId + caseType）
     */
    public void triggerEffect(String effectKey, String condition, CaseContext context) {
        triggerEffect(effectKey, condition, null, context);
    }

    // ============================================================
    // 入口 2（旧，兼容）：无 condition、无 branch
    // ============================================================

    /**
     * 触发效果（简化版，无 condition）。
     *
     * <p>等价于 {@code triggerEffect(effectKey, null, null, context)}，只查基础 key。</p>
     */
    public void triggerEffect(String effectKey, CaseContext context) {
        triggerEffect(effectKey, null, null, context);
    }

    // ============================================================
    // 入口 3（最底层，保留旧签名）：sessionId + caseId + effectKey
    // ============================================================

    /**
     * 触发效果（最底层，无 condition、无 branch）。
     *
     * <p>仅查 {@code effectKey}，供 {@code StateMachineFacade} / 异常层等
     * 不感知 condition / branch 的调用点使用。</p>
     */
    public void triggerEffect(String sessionId, String caseId, String effectKey) {
        if (caseId == null || caseId.isBlank()) {
            log.warn("triggerEffect 收到空 caseId，跳过: session={}, effectKey={}",
                sessionId, effectKey);
            return;
        }

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

        long now = System.currentTimeMillis();
        List<ActiveEffect> newEffects = buildActiveEffects(templates, now, null);
        activeEffects.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(newEffects);

        log.info("✅ 效果已触发: session={}, effect={}, count={}",
            sessionId, effectKey, newEffects.size());
    }

    // ============================================================
    // 效果修正量计算
    // ============================================================

    /**
     * 获取当前时刻所有活跃效果的修正量（按参数汇总）。
     *
     * <p>返回两类 key：
     * <ul>
     *   <li>普通数值型 target：{@code phys.heartRate → -20}</li>
     *   <li>枚举型 target（ENUM_DOWN/UP）：{@code phys.consciousness__enum_shift → -1}</li>
     * </ul>
     *
     * <p>调用方需理解 {@code __enum_shift} 约定：value 为 ±steps，
     * 表示该枚举变量应向上/下移动 N 级。</p>
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

            // 已过期：duration > 0 且当前时刻已超出
            if (effect.getDuration() > 0
                && now > effect.getStartTime() + effect.getDuration()) {
                it.remove();
                log.debug("效果已过期: target={}, branch={}",
                    effect.getTarget(), effect.getBranch());
                continue;
            }

            // ★ 枚举位移：不走曲线插值，直接给 ±steps（同会话内累积）
            if (isEnumShift(effect)) {
                int dir = "ENUM_UP".equals(effect.getType()) ? 1 : -1;
                int shift = dir * (effect.getSteps() != null ? effect.getSteps() : 1);
                if (shift != 0) {
                    String enumKey = effect.getTarget() + "__enum_shift";
                    modifiers.merge(enumKey, shift, Integer::sum);
                }
                continue;
            }

            // DELTA：按曲线计算当前修正量
            int currentDelta = calculateCurrentDelta(effect, now);
            if (currentDelta != 0) {
                modifiers.merge(effect.getTarget(), currentDelta, Integer::sum);
            }
        }
        return modifiers;
    }

    /** 判断是否为枚举位移效果 */
    private boolean isEnumShift(ActiveEffect effect) {
        String type = effect.getType();
        return "ENUM_DOWN".equals(type) || "ENUM_UP".equals(type);
    }

    /**
     * 根据曲线类型计算当前修正量（仅 DELTA 类型使用）。
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
     * 清除会话的所有效果（会话结束时调用）。
     */
    public void clearEffects(String sessionId) {
        activeEffects.remove(sessionId);
        log.debug("清除会话 {} 的效果", sessionId);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 按 condition 查找效果模板。
     * 查找顺序：{@code effectKey__condition} → {@code effectKey}
     * → {@code effectKey__correct}。
     */
    private List<OperationEffect> lookupTemplates(Map<String, List<OperationEffect>> all,
                                                  String effectKey,
                                                  String condition) {
        // 1) effectKey__condition
        if (condition != null && !condition.isBlank()) {
            List<OperationEffect> t = all.get(effectKey + "__" + condition);
            if (t != null && !t.isEmpty()) {
                return t;
            }
        }
        // 2) 基础 key
        List<OperationEffect> base = all.get(effectKey);
        if (base != null && !base.isEmpty()) {
            return base;
        }
        // 3) 兜底 correct
        return all.get(effectKey + "__correct");
    }

    /**
     * 把效果模板列表转成 ActiveEffect 列表。
     *
     * @param templates 模板列表
     * @param now       当前时刻（毫秒）
     * @param branch    学员操作分支（审计用，可 null）
     */
    private List<ActiveEffect> buildActiveEffects(List<OperationEffect> templates,
                                                  long now,
                                                  EffectBranch branch) {
        List<ActiveEffect> newEffects = new ArrayList<>(templates.size());
        for (OperationEffect template : templates) {
            ActiveEffect effect = new ActiveEffect();
            effect.setTarget(template.getTarget());
            effect.setDelta((int) template.getDelta());
            effect.setType(template.getType() != null ? template.getType() : "DELTA");
            effect.setSteps(template.getSteps() != null ? template.getSteps() : 0);

            Integer durationSec = template.getDuration();
            effect.setDuration(durationSec != null ? durationSec * 1000L : 0L);

            Integer recoverySec = template.getRecovery();
            effect.setRecovery(recoverySec != null ? recoverySec * 1000L : 0L);

            effect.setCurve(template.getCurve());
            effect.setStartTime(now);
            effect.setBranch(branch);        // ★ 螺旋 3：审计用
            newEffects.add(effect);
        }
        return newEffects;
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

        /** 效果类型：DELTA（默认） / ENUM_DOWN / ENUM_UP / SET */
        private String type;

        /** 枚举位移级数（ENUM_DOWN/UP 时使用） */
        private Integer steps;

        /** ★ 螺旋 3：触发本效果时的学员操作分支（审计用，可 null） */
        private EffectBranch branch;
    }
}

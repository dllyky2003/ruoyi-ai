package com.ruoyi.ems.domain;


import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 阶段一致性检查结果。
 *
 * <p>由 {@code PhaseManager.checkConsistency(...)} 返回，供
 * {@code AclsCommandEngine.processCommand} 在 guardRail 之后、
 * operationArbiter 之前决策使用。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li><b>默认 WARN，不 BLOCK</b>。硬拦截只保留在 guardRail 和 operationArbiter。</li>
 *   <li>{@code BLOCK} 仅在配置显式开启 {@code phase.hardBlock=true} 时使用，
 *       作为逃生通道，避免未来某案例需要强约束时无处可放。</li>
 *   <li>结果对象不可变语义：构造后字段只读，避免下游误改。</li>
 * </ul>
 */
@Data
public class PhaseCheckResult {

    /**
     * 检查级别。
     */
    public enum Level {
        /** 通过，无冲突 */
        PASS,
        /** 软警告，允许执行，附带提示信息 */
        WARN,
        /** 硬拦截，拒绝执行（仅配置显式开启时使用） */
        BLOCK
    }

    /** 检查级别 */
    private Level level = Level.PASS;

    /** 当前主阶段（可能为 null，表示未初始化） */
    private String primaryPhase;

    /** 当前候选阶段集合 */
    private Set<String> activePhases = new LinkedHashSet<>();

    /** 用户可读的提示/拒绝消息 */
    private String message;

    /** 命中的阶段 ID（若由某条 PhaseDefinition 触发警告，记录其 id） */
    private String hitPhaseId;

    // ============================================================
    // 工厂方法
    // ============================================================

    /**
     * 通过。无冲突。
     *
     * @param primaryPhase 当前主阶段
     * @param activePhases 当前候选阶段集合
     */
    public static PhaseCheckResult pass(String primaryPhase, Set<String> activePhases) {
        PhaseCheckResult r = new PhaseCheckResult();
        r.setLevel(Level.PASS);
        r.setPrimaryPhase(primaryPhase);
        r.setActivePhases(activePhases != null
            ? new LinkedHashSet<>(activePhases)
            : new LinkedHashSet<>());
        return r;
    }

    /**
     * 软警告。允许继续执行，附带提示。
     *
     * @param primaryPhase 当前主阶段
     * @param activePhases 当前候选阶段集合
     * @param hitPhaseId   触发警告的阶段 ID（可为 null）
     * @param message      用户可读提示
     */
    public static PhaseCheckResult warn(String primaryPhase,
                                        Set<String> activePhases,
                                        String hitPhaseId,
                                        String message) {
        PhaseCheckResult r = new PhaseCheckResult();
        r.setLevel(Level.WARN);
        r.setPrimaryPhase(primaryPhase);
        r.setActivePhases(activePhases != null
            ? new LinkedHashSet<>(activePhases)
            : new LinkedHashSet<>());
        r.setHitPhaseId(hitPhaseId);
        r.setMessage(message);
        return r;
    }

    /**
     * 硬拦截。拒绝执行。
     *
     * <p><b>谨慎使用</b>：只有配置显式开启 {@code phase.hardBlock=true} 时，
     * {@code PhaseManager} 才会返回此结果。默认路径不走这里。</p>
     *
     * @param primaryPhase 当前主阶段
     * @param activePhases 当前候选阶段集合
     * @param hitPhaseId   触发拦截的阶段 ID（可为 null）
     * @param message      用户可读拒绝原因
     */
    public static PhaseCheckResult block(String primaryPhase,
                                         Set<String> activePhases,
                                         String hitPhaseId,
                                         String message) {
        PhaseCheckResult r = new PhaseCheckResult();
        r.setLevel(Level.BLOCK);
        r.setPrimaryPhase(primaryPhase);
        r.setActivePhases(activePhases != null
            ? new LinkedHashSet<>(activePhases)
            : new LinkedHashSet<>());
        r.setHitPhaseId(hitPhaseId);
        r.setMessage(message);
        return r;
    }

    // ============================================================
    // 便捷查询
    // ============================================================

    public boolean isPassed() {
        return level == Level.PASS;
    }

    public boolean isWarned() {
        return level == Level.WARN;
    }

    public boolean isBlocked() {
        return level == Level.BLOCK;
    }

    /**
     * 只读视图：获取候选阶段集合。
     */
    public Set<String> getActivePhasesView() {
        return Collections.unmodifiableSet(activePhases);
    }
}

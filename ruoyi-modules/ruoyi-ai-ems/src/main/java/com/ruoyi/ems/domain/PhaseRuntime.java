package com.ruoyi.ems.domain;

import lombok.Data;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 阶段运行时状态。
 *
 * <p>挂在 {@link com.ruoyi.ems.domain.CaseContext} 上，<b>不进入 variables</b>，
 * 避免污染 modifiedVariables 和 resetDefaults。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code activePhases}：当前所有候选阶段（可多个）</li>
 *   <li>{@code primaryPhase}：当前确定性主阶段（唯一）</li>
 *   <li>{@code primaryPhaseSince}：主阶段进入时间戳（毫秒）</li>
 *   <li>{@code lockedPhase}：被锁定的阶段（若非空且未过期，主阶段不可切换）</li>
 *   <li>{@code lockUntil}：锁定到期时间戳（毫秒）</li>
 *   <li>{@code phaseEnteredAt}：各阶段最近一次进入时间戳，用于诊断</li>
 *   <li>{@code lastTransitionIntent}：最近一次触发阶段变化的意图</li>
 *   <li>{@code revision}：阶段状态版本号，每次主阶段变化 +1，便于审计</li>
 * </ul>
 */
@Data
public class PhaseRuntime {

    /** 当前候选阶段集合（可为多个） */
    private Set<String> activePhases = new LinkedHashSet<>();

    /** 当前确定性主阶段（唯一） */
    private String primaryPhase;

    /** 主阶段进入时间戳（毫秒） */
    private long primaryPhaseSince;

    /** 被锁定的阶段 ID；非空且未过期时主阶段不可切换 */
    private String lockedPhase;

    /** 锁定到期时间戳（毫秒） */
    private long lockUntil;

    /** 各阶段最近一次进入时间戳，用于诊断与滞回 */
    private Map<String, Long> phaseEnteredAt = new HashMap<>();

    /** 最近一次触发阶段变化的意图 */
    private String lastTransitionIntent;

    /** 阶段状态版本号，每次主阶段变化 +1 */
    private int revision;

    /**
     * 深拷贝。
     *
     * <p>用于 {@link com.ruoyi.ems.domain.CaseContext#copy()}。
     * 集合字段全部新建实例，避免副本与原对象共享内部集合。</p>
     */
    public PhaseRuntime copy() {
        PhaseRuntime c = new PhaseRuntime();
        c.setActivePhases(this.activePhases != null
            ? new LinkedHashSet<>(this.activePhases)
            : new LinkedHashSet<>());
        c.setPrimaryPhase(this.primaryPhase);
        c.setPrimaryPhaseSince(this.primaryPhaseSince);
        c.setLockedPhase(this.lockedPhase);
        c.setLockUntil(this.lockUntil);
        c.setPhaseEnteredAt(this.phaseEnteredAt != null
            ? new HashMap<>(this.phaseEnteredAt)
            : new HashMap<>());
        c.setLastTransitionIntent(this.lastTransitionIntent);
        c.setRevision(this.revision);
        return c;
    }

    /**
     * 是否处于锁定状态（锁定阶段非空且未到期）。
     */
    public boolean isLocked() {
        return lockedPhase != null && System.currentTimeMillis() < lockUntil;
    }

    /**
     * 清除锁定。
     */
    public void clearLock() {
        this.lockedPhase = null;
        this.lockUntil = 0L;
    }
}

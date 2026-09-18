package com.ruoyi.ems.domain;


import lombok.Data;

import java.util.List;

/**
 * 阶段定义（对应 _flow.yaml 中 stateMachine.phases 下的单个条目）。
 *
 * <p>数据来源：{@code config/acls/**／_flow.yaml} 的
 * {@code stateMachine.phases} 节点。
 * 由 {@link com.ruoyi.ems.config.loader.YamlStateMachineLoader} 通过
 * SnakeYAML 反序列化，并按 {@code id} 合并父子配置。</p>
 *
 * <p><b>设计原则</b>：</p>
 * <ul>
 *   <li>Phase SM 只做宏阶段软约束，默认不 BLOCK。</li>
 *   <li>阶段进入/退出条件复用 SpEL，与 {@code TransitionRule.condition} 同源，
 *       不引入第二套 DSL。</li>
 *   <li>{@code hardBlock} 是逃生通道，默认 false；硬保证仍由 guardrails 承担。</li>
 * </ul>
 */
@Data
public class PhaseDefinition {

    // ==================== 基础元数据 ====================

    /** 阶段 ID，如 BLS / CPR / AED / ACLS / POST_ROSC / TRANSPORT（唯一） */
    private String id;

    /** 阶段显示名称，用于提示词与 UI */
    private String displayName;

    /** 主阶段选择优先级（数值越大优先级越高） */
    private Integer priority = 0;

    // ==================== 归属与触发 ====================

    /**
     * 属于该阶段的细粒度状态编码列表。
     * 用于"状态命中"：当前 {@code CaseContext.currentState} 落在其中即视为命中。
     */
    private List<String> states;
    /**
     * ★ 子阶段列表（支持嵌套）。
     *
     * <p><b>互斥约定</b>：一个 PhaseDefinition 要么有 {@link #states}（叶子阶段），
     * 要么有 {@code children}（容器阶段），不应同时有两者。
     * 由 {@code YamlStateMachineLoader} 在加载时校验。</p>
     *
     * <p><b>典型用途</b>：</p>
     * <ul>
     *   <li>临床状态（如 TACHYCARDIA_STABLE）包含算法步骤子阶段
     *       （ASSESS_STABILITY / CHECK_QRS_WIDTH / VAGAL / ADENOSINE_6MG ...）</li>
     *   <li>算法步骤子阶段再包含更细的操作状态</li>
     * </ul>
     *
     * <p><b>运行时不变</b>：{@code PhaseManager} 只处理叶子阶段的 states，
     * 容器阶段不参与运行时决策，仅用于主图展示和配置审校。</p>
     */
    private List<PhaseDefinition> children;


    /**
     * 顶层 phase 的别名前缀，用于把 XXX_EXIT_TO_PARENT 之类的状态
     * 归并回所属的 phase。
     *
     * 例：CARDIAC_ARREST 的 aliases = ["F_103_"]
     *     SVT_PATH 的 aliases = ["F_101_", "F_102_"]
     */
    private List<String> aliases;

    /**
     * 触发进入该阶段的意图编码列表。
     * 用于"意图命中"。
     */
    private List<String> entryIntents;

    /**
     * 进入条件（SpEL）。
     * 复用 {@code SpELExpressionEvaluator.evaluateCondition(...)}。
     * 示例：{@code "#process.cpr_in_progress == true"}
     */
    private String entryCondition;

    /**
     * 退出条件（SpEL，可选）。
     * 若为空，则仅靠主阶段选择算法自动切换。
     */
    private String exitCondition;

    // ==================== 允许 / 警告 ====================

    /**
     * 该阶段下允许的意图编码列表。
     * 与 {@code ResolvedCaseTypeConfig.alwaysAllowedIntents} 取并集，
     * 构成当前阶段的完整允许集合。
     */
    private List<String> allowedIntents;

    /**
     * 该阶段下软警告的意图编码列表。
     * 命中时返回 {@code PhaseCheckResult.WARN}，但不阻断。
     */
    private List<String> softWarnIntents;

    // ==================== 依赖 ====================

    /**
     * 前置阶段列表。
     * 只有当前 {@code activePhases} 覆盖全部前置阶段时，本阶段才可进入候选。
     */
    private List<String> requiresPhases;

    // ==================== 锁定 ====================

    /** 锁定配置。{@code null} 表示无锁定。 */
    private PhaseLockConfig lock;

    // ==================== 逃生通道 ====================

    /**
     * 是否允许硬拦截（默认 false）。
     *
     * <p><b>谨慎使用</b>：Phase SM 默认只做软约束，硬保证由 guardrails 承担。
     * 仅当某案例确实需要"阶段级硬拦截"时才置 true。</p>
     */
    private Boolean hardBlock = false;

    // ==================== 便捷方法 ====================
    /**
     * 是否为容器阶段（有子阶段，无直接 states）。
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    /**
     * 是否为叶子阶段（有直接 states，无子阶段）。
     */
    public boolean isLeaf() {
        return !hasChildren();
    }

    /** 是否启用锁定。 */
    public boolean isLockEnabled() {
        return lock != null && Boolean.TRUE.equals(lock.getEnabled());
    }

    /** 是否启用硬拦截逃生通道。 */
    public boolean isHardBlockEnabled() {
        return Boolean.TRUE.equals(hardBlock);
    }

    /** 状态是否属于本阶段。 */
    public boolean containsState(String state) {
        return state != null && states != null && states.contains(state);
    }

    /** 意图是否为本阶段的进入意图。 */
    public boolean containsEntryIntent(String intentCode) {
        return intentCode != null && entryIntents != null && entryIntents.contains(intentCode);
    }

    /** 意图是否为本阶段允许。 */
    public boolean containsAllowedIntent(String intentCode) {
        return intentCode != null && allowedIntents != null && allowedIntents.contains(intentCode);
    }

    /** 意图是否为本阶段软警告。 */
    public boolean containsSoftWarnIntent(String intentCode) {
        return intentCode != null && softWarnIntents != null && softWarnIntents.contains(intentCode);
    }

    /** 获取显示名（用于日志）。 */
    public String getDisplayName() {
        return id + (displayName != null ? "(" + displayName + ")" : "");
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    // ============================================================
    // 内嵌：锁定配置
    // ============================================================

    /**
     * 阶段锁定配置。
     *
     * <p>锁定语义：当 {@code enabled=true} 且某阶段成为主阶段时，
     * {@code lockedPhase} 被置为该阶段，{@code lockUntil = now + durationMs}。
     * 在锁定未到期且未被显式解除之前，主阶段不切换。</p>
     *
     * <p>解除路径（任一命中即解除）：</p>
     * <ol>
     *   <li>{@code releaseIntents} 包含当前意图编码</li>
     *   <li>{@code releaseCondition}（SpEL）求值为 true</li>
     *   <li>{@code durationMs} 到期</li>
     * </ol>
     *
     * <p>若 {@code durationMs} 为 null，则只靠前两条解除，永不自动过期。</p>
     */
    @Data
    public static class PhaseLockConfig {

        /** 是否启用锁定（默认 false） */
        private Boolean enabled = false;

        /** 锁定持续时间（毫秒）。null 表示不自动过期 */
        private Long durationMs;

        /** 可提前解除锁定的意图编码列表 */
        private List<String> releaseIntents;

        /** 可提前解除锁定的 SpEL 条件（可选） */
        private String releaseCondition;

        // ---- 便捷方法 ----

        public boolean hasDuration() {
            return durationMs != null && durationMs > 0;
        }

        public boolean containsReleaseIntent(String intentCode) {
            return intentCode != null
                && releaseIntents != null
                && releaseIntents.contains(intentCode);
        }

        public boolean hasReleaseCondition() {
            return releaseCondition != null && !releaseCondition.isBlank();
        }
    }
}

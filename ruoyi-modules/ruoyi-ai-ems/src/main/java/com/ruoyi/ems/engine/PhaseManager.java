package com.ruoyi.ems.engine;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.*;

import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 阶段状态机（Phase SM）管理器。
 *
 * <p><b>定位</b>：夹在 guardrails（硬）与 operationArbiter（硬）之间，
 * 只做宏阶段软一致性校验 + 主阶段选择 + 锁定/滞回管理。
 * <b>不替代</b>任何现有层，<b>不处理</b>窄QRS/宽QRS/具体药物等细分分支。</p>
 *
 * <p><b>核心职责</b>：</p>
 * <ol>
 *   <li>{@link #initialize}：会话创建时确定初始主阶段</li>
 *   <li>{@link #checkConsistency}：intent 到达时判断是否与当前主阶段一致（默认 WARN）</li>
 *   <li>{@link #updateAfterTransition}：细粒度状态转移后重新选择主阶段（含锁定/滞回）</li>
 *   <li>{@link #reEvaluate}：事实驱动重评估（Skill 完成 / 变量变化）</li>
 * </ol>
 *
 * <p><b>多候选 + 确定性主阶段</b>：候选阶段可以有多个（activePhases），
 * 但主阶段（primaryPhase）始终唯一，靠 priority + YAML 顺序 + 滞回 + 锁定共同保证。</p>
 *
 * <p><b>候选生成的三类命中与优先级</b>（v2 语义）：</p>
 * <ol>
 *   <li><b>状态命中</b>（phase.states 包含 currentState）：最强。若存在状态命中，
 *       主阶段只在状态命中的 phase 中选择，避免"事实已就绪但细粒度状态尚未路由"时
 *       提前切换到下一个阶段。</li>
 *   <li><b>意图命中</b>（phase.entryIntents 包含当前 intent）：中等。</li>
 *   <li><b>事实命中</b>（phase.entryCondition 为 true）：最弱。</li>
 * </ol>
 *
 * <p><b>★ v2.1 分层修正</b>：所有阶段遍历使用 {@link ResolvedCaseTypeConfig#getLeafPhases()}
 * （递归展开 children），保证容器阶段（如 SVT_PATH）内部的子阶段（如 SVT_ASSESSMENT）
 * 也能作为主阶段候选。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhaseManager {

    private final SpELExpressionEvaluator evaluator;
    private final EmsContextManager contextManager;

    /** 写回 PhaseRuntime 时的最大重试次数（乐观锁冲突重试） */
    private static final int MAX_RETRY = 3;

    // ============================================================
    // 1. 初始化（会话创建时调用一次）
    // ============================================================

    /**
     * 初始化 PhaseRuntime。
     *
     * <p>幂等：若 runtime 已有 primaryPhase，直接返回。</p>
     *
     * @param sessionId 会话 ID
     * @param config    案例配置（含 phases）
     */
    public void initialize(String sessionId, ResolvedCaseTypeConfig config) {
        if (config == null || !config.hasPhases()) {
            log.debug("案例无 phase 配置，跳过 PhaseRuntime 初始化: session={}", sessionId);
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            CaseContext ctx = contextManager.getSnapshot(sessionId);
            if (ctx == null) {
                log.warn("初始化 PhaseRuntime 失败：上下文不存在, session={}", sessionId);
                return;
            }

            PhaseRuntime runtime = ctx.ensurePhaseRuntime();
            if (runtime.getPrimaryPhase() != null) {
                log.debug("PhaseRuntime 已初始化，跳过: session={}, primary={}",
                    sessionId, runtime.getPrimaryPhase());
                return;
            }

            String initialPhase = config.getInitialPhase();
            // ★ initialPhase 若指向容器，getPhaseByIdRecursive 会返回容器定义，
            //   但主阶段必须是叶子，因此兜底到最高优先级叶子
            if (initialPhase != null) {
                PhaseDefinition initDef = getPhaseByIdRecursive(config, initialPhase);
                if (initDef == null || initDef.hasChildren()) {
                    log.warn("initialPhase={} 不是有效叶子，回退到最高优先级叶子", initialPhase);
                    initialPhase = null;
                }
            }
            if (initialPhase == null) {
                // ★ v2.1：改用叶子阶段，避免选中容器
                initialPhase = pickHighestPriority(config.getLeafPhases());
            }
            if (initialPhase == null) {
                log.warn("无法确定初始主阶段: session={}, leafCount={}",
                    sessionId, config.getLeafPhases().size());
                return;
            }

            long now = System.currentTimeMillis();
            runtime.setPrimaryPhase(initialPhase);
            runtime.setPrimaryPhaseSince(now);
            runtime.getActivePhases().add(initialPhase);
            runtime.getPhaseEnteredAt().put(initialPhase, now);
            runtime.setRevision(0);

            // 应用初始锁定（若 initialPhase 配置了 lock）
            applyLock(initialPhase, runtime, config, now);

            boolean ok = contextManager.updatePhaseRuntime(
                sessionId, runtime, ctx.getVersion());
            if (ok) {
                log.info("✅ PhaseRuntime 初始化成功: session={}, primary={}, active={}, locked={}",
                    sessionId, initialPhase, runtime.getActivePhases(), runtime.getLockedPhase());
                return;
            }
            log.warn("PhaseRuntime 初始化写回冲突，重试: session={}, attempt={}",
                sessionId, attempt + 1);
        }
        log.error("PhaseRuntime 初始化重试 {} 次仍失败: session={}", MAX_RETRY, sessionId);
    }

    // ============================================================
    // 2. 一致性检查（intent 到达时）
    // ============================================================

    /**
     * 检查当前 intent 与当前主阶段是否一致。
     *
     * <p>默认策略：</p>
     * <ul>
     *   <li>intent 属于 alwaysAllowedIntents → PASS</li>
     *   <li>intent 属于当前 phase 的 allowedIntents / entryIntents → PASS</li>
     *   <li>intent 属于当前 phase 的 softWarnIntents → WARN</li>
     *   <li>intent 不属于当前 phase 任何集合 → WARN（若 hardBlock 开启则 BLOCK）</li>
     * </ul>
     *
     * <p>本方法<b>只读</b>，不修改 PhaseRuntime。主阶段的更新由
     * {@link #updateAfterTransition} 负责。</p>
     *
     * @param sessionId 会话 ID（仅用于日志）
     * @param ctx       当前上下文快照
     * @param command   当前意图命令（可为 null）
     * @param config    案例配置
     * @return 检查结果（PASS / WARN / BLOCK）
     */
    public PhaseCheckResult checkConsistency(String sessionId,
                                             CaseContext ctx,
                                             IntentCommand command,
                                             ResolvedCaseTypeConfig config) {
        if (config == null || !config.hasPhases()) {
            return PhaseCheckResult.pass(null, Collections.emptySet());
        }
        if (ctx == null) {
            return PhaseCheckResult.pass(null, Collections.emptySet());
        }

        PhaseRuntime runtime = ctx.getPhaseRuntime();
        if (runtime == null || runtime.getPrimaryPhase() == null) {
            return PhaseCheckResult.pass(null, Collections.emptySet());
        }

        String primary = runtime.getPrimaryPhase();
        Set<String> active = runtime.getActivePhases();

        String intentCode = command != null ? command.getIntentCode() : null;

        // 1. 无 intentCode / META_* / 系统事件：PASS
        if (intentCode == null || intentCode.isEmpty()) {
            return PhaseCheckResult.pass(primary, active);
        }
        if (intentCode.startsWith("META_")) {
            return PhaseCheckResult.pass(primary, active);
        }

        // 2. 常驻意图：PASS
        Set<String> always = config.getAlwaysAllowedIntents();
        if (always != null && always.contains(intentCode)) {
            return PhaseCheckResult.pass(primary, active);
        }

        // 3. 当前 phase 未定义（配置缺失）：PASS，不误伤
        //    ★ v2.1：改用递归查找，覆盖 SVT_PATH 的子阶段
        PhaseDefinition phaseDef = getPhaseByIdRecursive(config, primary);
        if (phaseDef == null) {
            log.debug("当前主阶段 {} 无 PhaseDefinition，跳过一致性检查", primary);
            return PhaseCheckResult.pass(primary, active);
        }

        // 4. 属于 allowedIntents：PASS
        if (phaseDef.containsAllowedIntent(intentCode)) {
            return PhaseCheckResult.pass(primary, active);
        }

        // 5. 属于 entryIntents：PASS（进入意图本身是允许的）
        if (phaseDef.containsEntryIntent(intentCode)) {
            return PhaseCheckResult.pass(primary, active);
        }

        // 6. 属于 softWarnIntents：WARN
        if (phaseDef.containsSoftWarnIntent(intentCode)) {
            String msg = String.format("当前处于「%s」阶段，%s 不是常规操作，请确认。",
                phaseDef.getDisplayName(), intentCode);
            log.info("⚠️ Phase 软警告(softWarn): session={}, intent={}, phase={}",
                sessionId, intentCode, primary);
            return PhaseCheckResult.warn(primary, active, phaseDef.getId(), msg);
        }

        // 7. 不属于当前 phase 任何集合：WARN 或 BLOCK
        String msg = buildConflictMessage(config, phaseDef, intentCode);
        if (phaseDef.isHardBlockEnabled()) {
            log.warn("🛑 Phase 硬拦截(hardBlock): session={}, intent={}, phase={}",
                sessionId, intentCode, primary);
            return PhaseCheckResult.block(primary, active, phaseDef.getId(), msg);
        }
        log.info("⚠️ Phase 软警告(不一致): session={}, intent={}, phase={}",
            sessionId, intentCode, primary);
        return PhaseCheckResult.warn(primary, active, phaseDef.getId(), msg);
    }

    /**
     * 构建不一致时的提示消息。
     * 若 intent 属于其他 phase 的 entryIntents，提示会指出"该操作属于 X 阶段"。
     */
    private String buildConflictMessage(ResolvedCaseTypeConfig config,
                                        PhaseDefinition currentPhase,
                                        String intentCode) {
        String otherPhase = null;
        // ★ v2.1：遍历叶子阶段，容器阶段不参与一致性匹配
        List<PhaseDefinition> leafPhases = config.getLeafPhases();
        if (leafPhases != null) {
            for (PhaseDefinition p : leafPhases) {
                if (p == null || p.getId() == null || p.getId().equals(currentPhase.getId())) {
                    continue;
                }
                if (p.containsEntryIntent(intentCode)) {
                    otherPhase = p.getDisplayName();
                    break;
                }
            }
        }
        if (otherPhase != null) {
            return String.format("当前处于「%s」阶段，%s 属于「%s」阶段的操作，请确认。",
                currentPhase.getDisplayName(), intentCode, otherPhase);
        }
        return String.format("当前处于「%s」阶段，执行 %s 可能不符合流程。",
            currentPhase.getDisplayName(), intentCode);
    }

    // ============================================================
    // 3. 状态转移后更新主阶段
    // ============================================================

    /**
     * 细粒度状态转移成功后调用，重新选择主阶段。
     *
     * <p>算法：</p>
     * <ol>
     *   <li>生成候选阶段（事实命中 ∪ 状态命中 ∪ 意图命中）</li>
     *   <li><b>★ 状态命中优先</b>：若存在状态命中的 phase，只在这些 phase 中选主阶段</li>
     *   <li>按 requiresPhases 过滤（"曾经进入过"语义）</li>
     *   <li>锁定检查：若锁定未解除，保持锁定阶段</li>
     *   <li>优先级过滤 → 滞回 → 确定性选择</li>
     *   <li>写回 PhaseRuntime（含 activePhases 更新 + 锁定应用）</li>
     * </ol>
     *
     * @param sessionId  会话 ID
     * @param config     案例配置
     * @param newState   刚转移到的细粒度状态（可为 null，此时用 ctx.currentState）
     * @param intentCode 触发转移的意图编码（可为 null）
     * @return 更新后的主阶段；失败返回 null
     */
    public String updateAfterTransition(String sessionId,
                                        ResolvedCaseTypeConfig config,
                                        String newState,
                                        String intentCode) {
        if (config == null || !config.hasPhases()) {
            return null;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            CaseContext latest = contextManager.getSnapshot(sessionId);
            if (latest == null) {
                log.warn("updateAfterTransition 失败：上下文不存在, session={}", sessionId);
                return null;
            }

            PhaseRuntime runtime = latest.ensurePhaseRuntime();
            String oldPrimary = runtime.getPrimaryPhase();

            // 1. 生成候选
            String effectiveState = newState != null ? newState : latest.getCurrentState();
            Set<PhaseDefinition> candidates = generateCandidates(
                config, latest, effectiveState, intentCode);

            // 2. ★ 状态命中优先：若存在状态命中，只在状态命中的 phase 里选主阶段
            Set<PhaseDefinition> stateHits = candidates.stream()
                .filter(p -> p.containsState(effectiveState))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<PhaseDefinition> effective = stateHits.isEmpty() ? candidates : stateHits;

            // 3. requiresPhases 过滤（基于"曾经进入过"）
            effective = filterByRequires(effective, runtime);

            // 4. 主阶段选择（含锁定检查）
            String newPrimary = selectPrimary(
                effective, runtime, config, latest, intentCode);

            // 5. 无变化检查
            Set<String> newActive = computeActiveIds(candidates, newPrimary);
            boolean primaryChanged = !Objects.equals(oldPrimary, newPrimary);
            boolean activeChanged = !Objects.equals(runtime.getActivePhases(), newActive);
            boolean lockChanged = checkLockChange(runtime, config, newPrimary);

            if (!primaryChanged && !activeChanged && !lockChanged) {
                return newPrimary;
            }

            // 6. 更新 runtime
            long now = System.currentTimeMillis();
            if (primaryChanged) {
                runtime.setPrimaryPhase(newPrimary);
                runtime.setPrimaryPhaseSince(now);
                runtime.setLastTransitionIntent(intentCode);
                runtime.setRevision(runtime.getRevision() + 1);
                if (newPrimary != null) {
                    runtime.getPhaseEnteredAt().put(newPrimary, now);
                }
            }
            runtime.setActivePhases(newActive);

            // 7. 锁定：仅当 primary 切换时应用
            if (primaryChanged) {
                applyLock(newPrimary, runtime, config, now);
            }

            // 8. 写回
            boolean ok = contextManager.updatePhaseRuntime(
                sessionId, runtime, latest.getVersion());
            if (ok) {
                if (primaryChanged) {
                    log.info("🔄 Phase 切换: session={}, {} -> {}, active={}, locked={}",
                        sessionId, oldPrimary, newPrimary, newActive, runtime.getLockedPhase());
                } else {
                    log.debug("Phase 无主阶段变化，active 更新: session={}, active={}",
                        sessionId, newActive);
                }
                return newPrimary;
            }
            log.warn("Phase 写回冲突，重试: session={}, attempt={}", sessionId, attempt + 1);
        }

        log.error("updateAfterTransition 重试 {} 次仍失败: session={}", MAX_RETRY, sessionId);
        return null;
    }

    // ============================================================
    // 4. 事实驱动重评估
    // ============================================================

    /**
     * 事实变化后重新评估主阶段。
     *
     * <p>典型场景：Skill 完成写入了 {@code process.cpr_in_progress=true}，
     * 但细粒度状态未变，此时需要靠 entryCondition 触发阶段切换。</p>
     *
     * @param sessionId 会话 ID
     * @param config    案例配置
     * @return 更新后的主阶段；无变化或失败返回当前主阶段或 null
     */
    public String reEvaluate(String sessionId, ResolvedCaseTypeConfig config) {
        if (config == null || !config.hasPhases()) {
            return null;
        }

        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            return null;
        }
        String currentState = ctx.getCurrentState();
        String currentPrimary = ctx.getPrimaryPhase();

        String newPrimary = updateAfterTransition(
            sessionId, config, currentState, null);

        if (newPrimary != null && !newPrimary.equals(currentPrimary)) {
            log.info("♻️ Phase 事实驱动重评估: session={}, {} -> {}",
                sessionId, currentPrimary, newPrimary);
        }
        return newPrimary != null ? newPrimary : currentPrimary;
    }

    // ============================================================
    // 5. 只读接口
    // ============================================================

    /**
     * 获取当前主阶段。
     */
    public String getPrimaryPhase(CaseContext ctx) {
        if (ctx == null) {
            return null;
        }
        PhaseRuntime runtime = ctx.getPhaseRuntime();
        return runtime != null ? runtime.getPrimaryPhase() : null;
    }

    // ============================================================
    // 内部：候选生成
    // ============================================================

    /**
     * 生成候选阶段：事实命中 ∪ 状态命中 ∪ 意图命中。
     *
     * <p>三类命中在这里不做优先级区分——优先级由调用方（updateAfterTransition）
     * 通过"状态命中优先"逻辑处理。当前主阶段始终在候选里，防止抖动。</p>
     *
     * <p><b>★ v2.1</b>：遍历叶子阶段（含 children 递归展开），
     * 保证容器内的子阶段可作为候选。</p>
     */
    private Set<PhaseDefinition> generateCandidates(ResolvedCaseTypeConfig config,
                                                    CaseContext ctx,
                                                    String currentState,
                                                    String intentCode) {
        Set<PhaseDefinition> candidates = new LinkedHashSet<>();
        // ★ v2.1：改用叶子阶段
        List<PhaseDefinition> leafPhases = config.getLeafPhases();
        if (leafPhases == null || leafPhases.isEmpty()) {
            return candidates;
        }

        for (PhaseDefinition phase : leafPhases) {
            if (phase == null || phase.getId() == null) {
                continue;
            }
            boolean hit = false;

            // a. 事实命中（entryCondition）
            if (phase.getEntryCondition() != null && !phase.getEntryCondition().isBlank()) {
                try {
                    if (evaluator.evaluateCondition(phase.getEntryCondition(), ctx)) {
                        hit = true;
                    }
                } catch (Exception e) {
                    log.warn("Phase entryCondition 求值失败: phase={}, expr={}, err={}",
                        phase.getId(), phase.getEntryCondition(), e.getMessage());
                }
            }

            // b. 状态命中
            if (!hit && phase.containsState(currentState)) {
                hit = true;
            }

            // c. 意图命中
            if (!hit && phase.containsEntryIntent(intentCode)) {
                hit = true;
            }

            // d. 当前主阶段始终在候选里（防止自己掉出候选导致抖动）
            if (!hit) {
                PhaseRuntime runtime = ctx.getPhaseRuntime();
                if (runtime != null && phase.getId().equals(runtime.getPrimaryPhase())) {
                    hit = true;
                }
            }

            if (hit) {
                candidates.add(phase);
            }
        }
        return candidates;
    }

    // ============================================================
    // 内部：前置依赖过滤
    // ============================================================

    /**
     * 过滤 requiresPhases 不满足的候选。
     *
     * <p><b>语义（v2 修正）</b>：检查"曾经进入过"（{@code phaseEnteredAt}），
     * 而非"当前活跃"（{@code activePhases}）。</p>
     *
     * <p>理由：{@code activePhases} 每次 {@code updateAfterTransition} 都重算，
     * 前置阶段完成并退出后就不再出现在 {@code activePhases} 里，导致依赖它的
     * 后续阶段永远无法进入。用 {@code phaseEnteredAt} 记录"曾经进入"，
     * 更符合"前置阶段"的直觉。</p>
     */
    private Set<PhaseDefinition> filterByRequires(Set<PhaseDefinition> candidates,
                                                  PhaseRuntime runtime) {
        Set<PhaseDefinition> result = new LinkedHashSet<>();

        // 曾经进入过的阶段集合（含当前主阶段，因为 initialize 会写入 phaseEnteredAt）
        Set<String> visited = runtime.getPhaseEnteredAt() != null
            ? runtime.getPhaseEnteredAt().keySet()
            : Collections.emptySet();

        for (PhaseDefinition p : candidates) {
            List<String> requires = p.getRequiresPhases();
            if (requires == null || requires.isEmpty()) {
                result.add(p);
                continue;
            }
            boolean ok = true;
            for (String req : requires) {
                if (req != null && !visited.contains(req)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                result.add(p);
            } else {
                log.debug("Phase {} 被 requiresPhases 过滤: requires={}, visited={}",
                    p.getId(), requires, visited);
            }
        }
        return result;
    }

    // ============================================================
    // 内部：主阶段选择
    // ============================================================

    /**
     * 主阶段选择算法。
     *
     * <ol>
     *   <li>锁定检查：未解除则保持锁定阶段</li>
     *   <li>无候选 → 保持当前主阶段</li>
     *   <li>优先级过滤（取 priority 最大的一批）</li>
     *   <li>滞回：当前主阶段在 top 里就保持</li>
     *   <li>确定性选择：top 里按 YAML 顺序取第一个</li>
     * </ol>
     *
     * <p><b>注意</b>：调用方已通过"状态命中优先"过滤传入 candidates。
     * 本方法不再处理命中类型区分。</p>
     */
    private String selectPrimary(Set<PhaseDefinition> candidates,
                                 PhaseRuntime runtime,
                                 ResolvedCaseTypeConfig config,
                                 CaseContext ctx,
                                 String intentCode) {
        // ---- 1. 锁定检查 ----
        if (runtime.isLocked()) {
            String locked = runtime.getLockedPhase();
            if (containsId(candidates, locked)) {
                return locked;
            }
            // 尝试提前解除
            // ★ v2.1：递归查找锁定阶段定义
            PhaseDefinition lockedDef = getPhaseByIdRecursive(config, locked);
            if (lockedDef != null && lockedDef.isLockEnabled()) {
                PhaseDefinition.PhaseLockConfig lock = lockedDef.getLock();
                boolean released = false;
                if (lock.containsReleaseIntent(intentCode)) {
                    released = true;
                    log.info("🔓 Phase 锁定解除（releaseIntent）: phase={}, intent={}",
                        locked, intentCode);
                } else if (lock.hasReleaseCondition()) {
                    try {
                        if (evaluator.evaluateCondition(lock.getReleaseCondition(), ctx)) {
                            released = true;
                            log.info("🔓 Phase 锁定解除（releaseCondition）: phase={}, expr={}",
                                locked, lock.getReleaseCondition());
                        }
                    } catch (Exception e) {
                        log.warn("releaseCondition 求值失败: phase={}, expr={}, err={}",
                            locked, lock.getReleaseCondition(), e.getMessage());
                    }
                }
                if (!released) {
                    // 锁定未解除，保持锁定阶段
                    return locked;
                }
                runtime.clearLock();
            } else {
                // 锁定阶段没有配置 lock（异常情况），清除锁定
                log.warn("锁定阶段 {} 无 PhaseDefinition 或未启用 lock，强制清除", locked);
                runtime.clearLock();
            }
        }

        // ---- 2. 无候选 → 保持 ----
        if (candidates.isEmpty()) {
            return runtime.getPrimaryPhase();
        }

        // ---- 3. 优先级过滤 ----
        int maxPriority = candidates.stream()
            .mapToInt(p -> p.getPriority() != null ? p.getPriority() : 0)
            .max().orElse(0);

        List<PhaseDefinition> top = candidates.stream()
            .filter(p -> (p.getPriority() != null ? p.getPriority() : 0) == maxPriority)
            .collect(Collectors.toList());

        // ---- 4. 滞回：当前主阶段在 top 里就保持 ----
        String current = runtime.getPrimaryPhase();
        if (current != null && top.stream().anyMatch(p -> current.equals(p.getId()))) {
            return current;
        }

        // ---- 5. 确定性选择：按 config 声明顺序（叶子展开后的顺序）----
        List<PhaseDefinition> ordered = orderByConfig(config, top);
        if (ordered.isEmpty()) {
            return runtime.getPrimaryPhase();
        }
        return ordered.get(0).getId();
    }

    // ============================================================
    // 内部：辅助方法
    // ============================================================

    /**
     * 按阶段声明顺序排列候选。
     *
     * <p><b>★ v2.1</b>：使用叶子阶段顺序（容器展开后的顺序）。</p>
     */
    private List<PhaseDefinition> orderByConfig(ResolvedCaseTypeConfig config,
                                                List<PhaseDefinition> candidates) {
        Map<String, Integer> orderIndex = new HashMap<>();
        // ★ v2.1：叶子阶段顺序
        List<PhaseDefinition> leafPhases = config.getLeafPhases();
        if (leafPhases != null) {
            for (int i = 0; i < leafPhases.size(); i++) {
                PhaseDefinition p = leafPhases.get(i);
                if (p != null && p.getId() != null) {
                    orderIndex.put(p.getId(), i);
                }
            }
        }
        List<PhaseDefinition> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(
            p -> orderIndex.getOrDefault(p.getId(), Integer.MAX_VALUE)));
        return sorted;
    }

    /**
     * 计算候选集合的 id 集合（用于更新 activePhases）。
     *
     * <p>注意：传的是<b>全量</b> candidates（含被状态命中过滤掉的那些），
     * 因为 activePhases 的语义是"当前可感知的阶段集合"，包含所有候选。
     * 主阶段的确定使用的是 effective（经状态命中过滤 + requiresPhases 过滤）。</p>
     */
    private Set<String> computeActiveIds(Set<PhaseDefinition> candidates, String primary) {
        Set<String> ids = new LinkedHashSet<>();
        for (PhaseDefinition p : candidates) {
            if (p != null && p.getId() != null) {
                ids.add(p.getId());
            }
        }
        if (primary != null) {
            ids.add(primary);
        }
        return ids;
    }

    /**
     * 应用锁定：若新主阶段配置了 lock，则设置 lockedPhase + lockUntil。
     */
    private void applyLock(String newPrimary, PhaseRuntime runtime,
                           ResolvedCaseTypeConfig config, long now) {
        if (newPrimary == null) {
            return;
        }
        // ★ v2.1：递归查找阶段定义
        PhaseDefinition def = getPhaseByIdRecursive(config, newPrimary);
        if (def == null || !def.isLockEnabled()) {
            // 新主阶段无锁：清除旧锁
            runtime.clearLock();
            return;
        }
        PhaseDefinition.PhaseLockConfig lock = def.getLock();
        runtime.setLockedPhase(newPrimary);
        if (lock.hasDuration()) {
            runtime.setLockUntil(now + lock.getDurationMs());
        } else {
            // 无时长 → 永久锁定（直到显式解除）
            runtime.setLockUntil(Long.MAX_VALUE);
        }
        log.debug("🔒 Phase 锁定: phase={}, until={}", newPrimary, runtime.getLockUntil());
    }

    /**
     * 检查锁定状态是否需要更新（用于判断是否需要写回）。
     */
    private boolean checkLockChange(PhaseRuntime runtime,
                                    ResolvedCaseTypeConfig config,
                                    String newPrimary) {
        String currentLock = runtime.getLockedPhase();
        if (newPrimary == null) {
            return currentLock != null;
        }
        // ★ v2.1：递归查找阶段定义
        PhaseDefinition def = getPhaseByIdRecursive(config, newPrimary);
        boolean shouldBeLocked = def != null && def.isLockEnabled();
        if (shouldBeLocked) {
            return !newPrimary.equals(currentLock);
        }
        return currentLock != null;
    }

    private boolean containsId(Set<PhaseDefinition> candidates, String id) {
        if (id == null) {
            return false;
        }
        return candidates.stream().anyMatch(p -> id.equals(p.getId()));
    }

    private String pickHighestPriority(List<PhaseDefinition> phases) {
        if (phases == null || phases.isEmpty()) {
            return null;
        }
        return phases.stream()
            .filter(p -> p != null && p.getId() != null)
            .filter(p -> !p.hasChildren())   // ★ 只选叶子阶段
            .max(Comparator.comparingInt(
                p -> p.getPriority() != null ? p.getPriority() : 0))
            .map(PhaseDefinition::getId)
            .orElse(null);
    }

    // ============================================================
    // 内部：递归查找
    // ============================================================

    /**
     * ★ v2.1 新增：递归查找 phase 定义（含 children）。
     *
     * <p>先查叶子阶段，再 fallback 到顶层（兼容容器 id 被引用的情况）。</p>
     */
    private PhaseDefinition getPhaseByIdRecursive(ResolvedCaseTypeConfig config, String phaseId) {
        if (config == null || phaseId == null) {
            return null;
        }
        // 先查叶子
        for (PhaseDefinition p : config.getLeafPhases()) {
            if (phaseId.equals(p.getId())) {
                return p;
            }
        }
        // fallback：顶层（可能是容器 id）
        return config.getPhaseById(phaseId);
    }
}

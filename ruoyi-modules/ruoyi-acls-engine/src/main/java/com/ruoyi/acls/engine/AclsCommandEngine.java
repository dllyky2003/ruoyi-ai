package com.ruoyi.acls.engine;

import com.ruoyi.acls.arbiter.ArbiterResult;
import com.ruoyi.acls.arbiter.OperationArbiter;
import com.ruoyi.acls.branch.StudentResponseBranch;
import com.ruoyi.acls.branch.StudentResponseResolver;
import com.ruoyi.acls.guard.ConfidenceGate;
import com.ruoyi.acls.guard.GuardRail;
import com.ruoyi.acls.router.ActTypeRouter;
import com.ruoyi.acls.router.RouteDecision;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;

import com.ruoyi.ems.domain.ExceptionRule.ExceptionRule;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.domain.PhaseCheckResult;

import com.ruoyi.ems.domain.TeachingMode.TeachingMode;
import com.ruoyi.ems.engine.AttemptOutcome;
import com.ruoyi.ems.engine.PhaseManager;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.engine.StateTransitionResult;
import com.ruoyi.ems.engine.TutorBlockedException;
import com.ruoyi.ems.exception.ExceptionEngine;
import com.ruoyi.ems.npc.IVrClientService;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.rule.IRuleEvaluator;
import com.ruoyi.ems.service.SessionService;
import com.ruoyi.ems.spi.ExceptionTickProcessor;
import com.ruoyi.ems.tts.KokoroTtsClient;
import com.ruoyi.ems.websocket.WebSocketMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACLS 引擎 —— 核心执行器（重构版）
 *
 * <p>执行顺序（重构后）：
 * <ol>
 *   <li>前置验证（OperationArbiter）</li>
 *   <li>置信度闸门（ConfidenceGate）</li>
 *   <li>安全护栏（GuardRail）</li>
 *   <li>派生变量更新</li>
 *   <li>★ 执行动作（先执行）</li>
 *   <li>★ 异常层评估（横切）</li>
 *   <li>★ 根据执行结果驱动状态机（后转换）</li>
 *   <li>返回结果</li>
 * </ol>
 *
 * <p>核心原则：只有动作执行成功，状态机才转换到下一个状态。
 *
 * <p>★ 修复记录（P0-2 / P0-3）：
 * <ul>
 *   <li>P0-2：executeAction 不再写 caseContext 深拷贝副本，统一走
 *       {@link EmsContextManager#batchSetVarsWithVersion}。</li>
 *   <li>P0-3：executeAction 成功且变量持久化后，重新拉取最新 CaseContext，
 *       再驱动状态机，避免 Guard 读到旧值。</li>
 * </ul>
 *
 * <p>★ 方案 A 修复（技能完成事件同步驱动状态机导致的 branch 污染）：
 * <ul>
 *   <li>技能完成事件（{@code TaskCompletedEvent}）在 {@code executeAction} 内同步发布，
 *       监听器 {@code SkillCompletionStateDriver} 可能已经完成状态转移。</li>
 *   <li>若已转移，本引擎不应再用旧 {@code currentState} 走 {@code selectAndTransition}，
 *       否则会误判为"无匹配转移"，并把 {@code responseResolver} 污染成 ERROR。</li>
 *   <li>新增：freshContext 状态 ≠ currentState 时，直接返回成功，跳过重复驱动。</li>
 * </ul>
 *
 * <p>★ 批 1（螺旋 2 / 阶段 2.1）：
 * <ul>
 *   <li>第 8 节改用 {@link StateMachineFacade#selectAndTransitionWithOutcome}，
 *       保留 {@link AttemptOutcome} 的 {@code validation} / {@code failureKind}。</li>
 *   <li>TUTOR_BLOCK / NO_MATCH / 成功三条路径的 {@link CommandResult}
 *       都携带 {@code validation} / {@code failureKind}，供 CMD_RESULT 组装。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "acls.engine.mode", havingValue = "hybrid", matchIfMissing = true)
@RequiredArgsConstructor
public class AclsCommandEngine implements ExceptionTickProcessor {

    // ========== 核心依赖 ==========
    private final OperationArbiter operationArbiter;
    private final EmsContextManager contextManager;
    private final StateMachineFacade stateMachineFacade;
    private final YamlStateMachineLoader stateMachineLoader;
    private final WebSocketMessageSender messageSender;

    // ========== 路由与执行 ==========
    private final ActTypeRouter actTypeRouter;
    private final SkillRegistry skillRegistry;

    // ========== 安全与校验 ==========
    private final ConfidenceGate confidenceGate;
    private final GuardRail guardRail;

    // ========== ★ Phase SM ==========
    private final PhaseManager phaseManager;

    // ========== 基础服务 ==========
    private final TeamManager teamManager;
    private final KokoroTtsClient ttsService;
    private final IVrClientService vrClientService;
    private final AckManager ackManager;

    // ========== ★ 异常层 ==========
    private final ExceptionEngine exceptionEngine;
    private final IRuleEvaluator ruleEvaluator;
    private final SessionService sessionService;   // 取 TeachingMode

    // ========== ★ 房间通道（用于把消息真正发给 Unity） ==========
    private final RoomRegistry roomRegistry;
    private final RoomBroadcaster roomBroadcaster;


    private final StudentResponseResolver responseResolver;
    /**
     * ★ 定时评估去重：sessionId → (ruleId → 上次触发时间毫秒)。
     * 避免同一规则每秒重复触发。
     */
    private final Map<String, Map<String, Long>> scheduledTriggerHistory = new ConcurrentHashMap<>();

    /** 同规则重复触发的冷却窗口（毫秒） */
    private static final long SCHEDULED_TRIGGER_COOLDOWN_MS = 30_000L;

    /**
     * 处理学员指令
     */
    public CommandResult processCommand(IntentCommand command, String sessionId) {
        String intentId = command.getIntentCode();
        String actType = command.getActType();
        Map<String, Object> slots = command.getSlots();

        // ========== 0. 元语用短路：只回话，不转移状态机 ==========
        if (intentId != null && intentId.startsWith("META_")) {
            String reply = switch (actType == null ? "" : actType) {
                case "VETO"                 -> "收到，已取消。";
                case "STRATEGY_CORRECTION"  -> "收到，已按新策略调整。";
                case "CLOSED_LOOP"          -> "是的，已确认。";
                default                     -> "收到。";
            };
            log.info("💬 元语用命中: session={}, intent={}, actType={}, reply={}",
                sessionId, intentId, actType, reply);

            return CommandResult.builder()
                .success(true)
                .responseText(reply)
                .contextUpdates(java.util.Collections.emptyMap())
                .animations(java.util.Collections.emptyList())
                .build();
        }

        try {
            // ========== 1. 获取上下文 ==========
            CaseContext caseContext = contextManager.getSnapshot(sessionId);
            if (caseContext == null) {
                log.error("❌ 会话 {} 的上下文不存在", sessionId);
                return CommandResult.failure("会话上下文不存在");
            }

            // ★ 新增：context 已失效（被 endSession 清了 caseType）→ 拒绝处理
            String caseType = caseContext.getCaseType();
            if (caseType == null || caseType.isBlank()) {
                log.warn("⚠️ 会话上下文已失效（可能已 endSession），拒绝处理指令: session={}, intent={}",
                    sessionId, intentId);
                return CommandResult.builder()
                    .success(false)
                    .errorMessage("会话已结束")
                    .build();
            }

            String currentState = caseContext.getCurrentState();
            log.info("📌 session={}, caseType={}, state={}, intent={}, actType={}",
                sessionId, caseType, currentState, intentId, actType);

            // ========== 2. 置信度闸门 ==========
            if (intentId != null && intentId.startsWith("S-")) {
                log.debug("🟢 S 类案例专属意图 {} 豁免 ConfidenceGate（confidence={}）",
                    intentId, command.getConfidence());
            } else {
                CommandResult gateResult = confidenceGate.check(command);
                if (gateResult != null) {
                    return gateResult;
                }
            }

            // ========== 3. 安全护栏 ==========
            CommandResult guardResult = guardRail.check(command, caseContext);
            if (guardResult != null && !guardResult.isSuccess()) {
                log.warn("🛑 安全护栏拦截: session={}, intent={}, msg={}",
                    sessionId, intentId, guardResult.getErrorMessage());
                return guardResult;
            }

            // ========== 3.5 获取状态机配置 ==========
            ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseType);
            if (config == null) {
                log.error("❌ 案例配置不存在: {}", caseType);
                return CommandResult.failure("案例配置不存在: " + caseType);
            }

            // ========== 3.6 ★ Phase 一致性检查（软） ==========
            PhaseCheckResult phaseCheck = phaseManager.checkConsistency(
                sessionId, caseContext, command, config);

            if (phaseCheck.isBlocked()) {
                log.warn("🛑 Phase 硬拦截: session={}, intent={}, phase={}, msg={}",
                    sessionId, intentId, phaseCheck.getPrimaryPhase(), phaseCheck.getMessage());
                return CommandResult.failure(phaseCheck.getMessage());
            }

            if (phaseCheck.isWarned()) {
                String warnMsg = phaseCheck.getMessage();
                log.info("⚠️ Phase 软警告: session={}, intent={}, phase={}, active={}, msg={}",
                    sessionId, intentId, phaseCheck.getPrimaryPhase(),
                    phaseCheck.getActivePhasesView(), warnMsg);
            }

            // ========== 4. 前置验证（OperationArbiter） ==========
            ArbiterResult arbiterResult = operationArbiter.arbitrate(
                currentState, intentId, slots, caseContext
            );
            if (!arbiterResult.isAllowed()) {
                log.warn("⛔ 仲裁拦截: session={}, reason={}", sessionId, arbiterResult.getReason());
                return CommandResult.failure(arbiterResult.getUserFriendlyMessage());
            }

            // ========== 6. ★ 构建执行上下文 ==========
            CommandContext context = buildCommandContext(sessionId, caseContext, command);

            // ========== 6.5 ★ 异常层评估（先于 skill 执行） ==========
            String currentPhase = phaseCheck.getPrimaryPhase();
            CommandResult exceptionResult = tryHandleExceptions(
                sessionId, caseType, intentId, currentPhase, caseContext, config);
            if (exceptionResult != null) {
                log.info("⚡ 异常层命中，跳过正常 skill: session={}, intent={}",
                    sessionId, intentId);
                return exceptionResult;
            }

            // ========== 7. ★ 执行动作（状态机之前） ==========
            CommandResult result = executeAction(command, context, caseContext);

            if (!result.isSuccess()) {
                log.warn("动作执行失败，状态机不转换: session={}, intent={}", sessionId, intentId);
                responseResolver.record(sessionId, StudentResponseBranch.ERROR, intentId,
                    "处理器拒绝: " + result.getErrorMessage());
                return result;
            }

            // ========== 7.2 ★ 修复：ANNOUNCEMENT 不豁免"变量驱动重评估" ==========
            // 背景：ANNOUNCEMENT 是"不动状态机的意图"（纯认知操作）。
            //   但它写下的变量（如 intervention.rhythmConverted）可能触发纯条件出边。
            //   原实现直接在写完变量后 return，从不触发状态机评估 → 下游死锁。
            // 修复：写完变量后调用一次 reEvaluateOnContextChange。
            //   若命中纯条件出边则推进状态机；否则维持原状。
            // 语义：ANNOUNCEMENT 豁免"意图驱动"，不豁免"变量驱动"。
            if ("ANNOUNCEMENT".equalsIgnoreCase(actType)) {
                log.info("💭 [ANNOUNCEMENT] {} 已处理，尝试变量驱动: session={}", intentId, sessionId);

                // 1. 持久化本次 action 返回的 updates（保持原逻辑）
                if (result.getContextUpdates() != null && !result.getContextUpdates().isEmpty()) {
                    persistUpdates(sessionId, result.getContextUpdates(), caseContext,
                        "ANNOUNCEMENT_UPDATES");
                }

                // 2. 拉最新快照后触发一次变量驱动重评估
                String annoNewState = caseContext.getCurrentState();
                try {
                    StateTransitionResult annoTransition =
                        stateMachineFacade.reEvaluateOnContextChange(sessionId, intentId);

                    if (annoTransition != null && annoTransition.isSuccess()) {
                        String targetState = annoTransition.getToState();
                        if (targetState != null && !targetState.equals(caseContext.getCurrentState())) {
                            annoNewState = targetState;
                            log.info("🔁 [ANNOUNCEMENT] 变量驱动推进成功: {} -> {} (intent={})",
                                caseContext.getCurrentState(), annoNewState, intentId);

                            try {
                                phaseManager.updateAfterTransition(
                                    sessionId, config, annoNewState, intentId);
                            } catch (Exception e) {
                                log.warn("Phase 更新失败: session={}, err={}", sessionId, e.getMessage());
                            }

                            responseResolver.record(sessionId, StudentResponseBranch.NORMAL, intentId,
                                "ANNOUNCEMENT 变量驱动转移成功");
                        } else {
                            log.debug("🔁 [ANNOUNCEMENT] 变量驱动未产生状态变化: state={}", annoNewState);
                            responseResolver.record(sessionId, StudentResponseBranch.NORMAL, intentId,
                                "ANNOUNCEMENT 无纯条件出边命中");
                        }
                    } else {
                        log.debug("🔁 [ANNOUNCEMENT] 变量驱动无命中: reason={}",
                            annoTransition != null ? annoTransition.getReason() : "null");
                        responseResolver.record(sessionId, StudentResponseBranch.NORMAL, intentId,
                            "ANNOUNCEMENT 纯认知操作，豁免状态机");
                    }
                } catch (Exception e) {
                    log.warn("🔁 [ANNOUNCEMENT] 变量驱动重评估异常: session={}, err={}",
                        sessionId, e.getMessage());
                    responseResolver.record(sessionId, StudentResponseBranch.NORMAL, intentId,
                        "ANNOUNCEMENT 纯认知操作，豁免状态机");
                }

                Map<String, Object> annoUpdates = new HashMap<>();
                if (result.getContextUpdates() != null) {
                    annoUpdates.putAll(result.getContextUpdates());
                }
                annoUpdates.put("currentState", annoNewState);

                return CommandResult.builder()
                    .success(true)
                    .responseText(result.getResponseText())
                    .contextUpdates(annoUpdates)
                    .animations(result.getAnimations())
                    .build();
            }

            // ========== 7.5 ★ P0-2：持久化技能返回的 contextUpdates ==========
            if (result.getContextUpdates() != null && !result.getContextUpdates().isEmpty()) {
                persistUpdates(sessionId, result.getContextUpdates(), caseContext,
                    "SKILL_RESULT");
                Map<String, Object> timeUpdate = new HashMap<>();
                timeUpdate.put("process.lastInterventionTime", System.currentTimeMillis());
                persistUpdates(sessionId, timeUpdate, caseContext, "SKILL_TIME_STAMP");
            }

            // ========== 7.6 ★ P0-3：动作执行后重新读取最新快照 ==========
            CaseContext freshContext = contextManager.getSnapshot(sessionId);
            if (freshContext == null) {
                log.error("❌ 执行动作后无法获取最新上下文: session={}", sessionId);
                return CommandResult.failure("执行动作后上下文丢失");
            }
            log.debug("🔄 执行动作后刷新上下文: session={}, version={}",
                sessionId, freshContext.getVersion());

            // ★ 方案 A 修复（修正版）：检查【状态机本身】是否已被技能完成事件同步驱动
            String smState = stateMachineFacade.getCurrentState(sessionId, config);
            if (smState != null && !smState.equals(currentState)) {
                log.info("✅ 技能完成事件已驱动状态机: {} -> {}，跳过 AclsCommandEngine 的重复驱动",
                    currentState, smState);

                responseResolver.record(sessionId, StudentResponseBranch.NORMAL, intentId,
                    "技能完成事件已驱动状态机");

                try {
                    phaseManager.updateAfterTransition(sessionId, config, smState, intentId);
                } catch (Exception e) {
                    log.warn("Phase 主阶段更新失败: session={}, intent={}, err={}",
                        sessionId, intentId, e.getMessage());
                }

                Map<String, Object> updates = new HashMap<>();
                if (result.getContextUpdates() != null) {
                    updates.putAll(result.getContextUpdates());
                }
                updates.put("currentState", smState);

                return CommandResult.builder()
                    .success(true)
                    .responseText(result.getResponseText())
                    .contextUpdates(updates)
                    .animations(result.getAnimations())
                    .build();
            }

            // ============================================================
            // ========== 8. ★ 批 1：结构化状态机驱动 ==========
            // ============================================================
            //
            // 改用 selectAndTransitionWithOutcome：
            //   - TUTOR_BLOCK → 直接失败，不走变量驱动兜底
            //   - NO_MATCH   → 先走变量驱动兜底，兜不住再失败
            //   - NONE       → 成功
            AttemptOutcome outcome = stateMachineFacade.selectAndTransitionWithOutcome(
                sessionId, config, intentId, freshContext);
            String newState = outcome.getNewState();

            // ----- TUTOR_BLOCK：直接失败 -----
            if (outcome.getFailureKind() == AttemptOutcome.FailureKind.TUTOR_BLOCK) {
                String ruleId = outcome.getValidation() != null
                    ? outcome.getValidation().getPrimaryRuleId() : null;
                String severity = outcome.getValidation() != null
                    ? outcome.getValidation().getSeverity() : null;
                log.warn("⛔ tutor 阻断: session={}, intent={}, state={}, ruleId={}, severity={}, feedback={}",
                    sessionId, intentId, currentState, ruleId, severity, outcome.getFailureReason());
                responseResolver.record(sessionId, StudentResponseBranch.ERROR, intentId,
                    "Tutor 阻断: " + (ruleId != null ? ruleId : "unknown"));
                return CommandResult.builder()
                    .success(false)
                    .errorMessage(outcome.getFailureReason())
                    .validation(outcome.getValidation())
                    .failureKind(AttemptOutcome.FailureKind.TUTOR_BLOCK.name())
                    .build();
            }

            // ----- NO_MATCH：先走变量驱动兜底 -----
            if (newState == null) {
                log.info("学员意图 {} 无匹配转移，尝试变量驱动重评估: session={}, state={}",
                    intentId, sessionId, currentState);

                StateTransitionResult transition =
                    stateMachineFacade.reEvaluateOnContextChange(sessionId, intentId);

                if (transition != null && transition.isSuccess()) {
                    newState = transition.getToState();
                    log.info("🔁 变量驱动兜底成功: {} -> {} (reason=none)",
                        currentState, newState);
                } else {
                    log.debug("🔁 变量驱动兜底未成功: reason={}",
                        transition != null ? transition.getReason() : "null");
                }
            }

            if (newState == null) {
                log.warn("⛔ 状态机无匹配转移: session={}, intent={}, state={}",
                    sessionId, intentId, currentState);
                responseResolver.record(sessionId, StudentResponseBranch.ERROR, intentId,
                    "状态机无匹配转移: " + currentState + " --[" + intentId + "]--> 无候选");
                return CommandResult.builder()
                    .success(false)
                    .errorMessage("当前临床流程不允许此操作")
                    .validation(outcome.getValidation())
                    .failureKind(AttemptOutcome.FailureKind.NO_MATCH.name())
                    .build();
            }

            log.info("✅ 状态机转换成功: {} -> {}", currentState, newState);
            responseResolver.record(sessionId, StudentResponseBranch.NORMAL, intentId,
                "状态转移成功");

            // ========== 8.5 ★ Phase 主阶段更新（含锁定/滞回） ==========
            try {
                String newPhase = phaseManager.updateAfterTransition(
                    sessionId, config, newState, intentId);
                if (newPhase != null) {
                    log.debug("Phase 主阶段已更新: session={}, primary={}", sessionId, newPhase);
                }
            } catch (Exception e) {
                log.warn("Phase 主阶段更新失败: session={}, intent={}, err={}",
                    sessionId, intentId, e.getMessage());
            }

            // ========== 9. 重置跨域变量 ==========
            List<String> resetVars = config.getResetVarsOnArrival();
            if (resetVars != null && !resetVars.isEmpty()) {
                boolean resetOk = contextManager.resetVariables(sessionId, resetVars, "SYSTEM_RESET");
                if (!resetOk) {
                    log.warn("跨域变量重置失败");
                }
                caseContext = contextManager.getSnapshot(sessionId);
            }

            // ========== 11. 构建返回结果（成功路径带 validation） ==========
            Map<String, Object> updates = new HashMap<>();
            if (result.getContextUpdates() != null) {
                updates.putAll(result.getContextUpdates());
            }
            updates.put("currentState", newState);

            return CommandResult.builder()
                .success(true)
                .responseText(result.getResponseText())
                .contextUpdates(updates)
                .animations(result.getAnimations())
                .validation(outcome.getValidation())
                .failureKind(AttemptOutcome.FailureKind.NONE.name())
                .build();

        } catch (Exception e) {
            log.error("❌ 处理指令异常: session={}, intent={}", sessionId, intentId, e);
            return CommandResult.failure("处理指令异常: " + e.getMessage());
        }
    }

    /**
     * ★ 定时异常评估入口。
     *
     * <p>执行顺序（分层）：</p>
     * <ol>
     *   <li><b>第一级：异常规则</b>——命中即接管（成功/失败都返回），不再往下走。</li>
     *   <li><b>第二级：状态机纯条件出边</b>——异常未命中时，驱动一次变量重评估，
     *       让依赖时间变量的纯条件出边（如 {@code process.timeElapsedSinceLastIntervention > 30}）
     *       能够按时触发。</li>
     * </ol>
     *
     * <p><b>背景</b>：纯条件出边（intentCode=null）不会自己产生事件。
     * 由 {@code process.timeElapsed*} 等时间变量驱动的出边，必须由定时 tick
     * 主动调用 {@link StateMachineFacade#reEvaluateOnContextChange(String, String)} 才会被评估。
     * 修复前本方法只跑异常层，导致诸如
     * {@code F_101_DO_SECOND_LINE_DRUG → F_101_EXIT_TO_PARENT} 的 30s 窗口形同虚设，
     * 状态机在二线药物执行后卡死，直到被异常规则 forceTransition 强制接管。</p>
     *
     * <p><b>安全性</b>：</p>
     * <ul>
     *   <li>{@code reEvaluateOnContextChange} 内部先检查当前状态是否有纯条件出边，
     *       没有则立即返回 noChange，开销极小；</li>
     *   <li>内部持有 {@code sm-lock:<sessionId>}，与 processCommand 串行，不会并发冲突；</li>
     *   <li>自循环递归有 depth 保护，不会无限循环；</li>
     *   <li>Guard 未通过时不会推进，不会误转移。</li>
     * </ul>
     */
    @Override
    public void tickExceptionsForSession(String sessionId) {
        if (sessionId == null) return;

        try {
            CaseContext ctx = contextManager.getSnapshot(sessionId);
            if (ctx == null) return;

            String caseType = ctx.getCaseType();
            if (caseType == null || caseType.isBlank()) return;

            ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseType);
            if (config == null) return;

            String currentPhase = null;

            // ========== 第一级：异常规则（最高优先级） ==========
            CommandResult result = tryHandleExceptions(
                sessionId,
                caseType,
                null,
                currentPhase,
                ctx,
                config
            );

            if (result != null) {
                // 异常层已接管（成功或失败），不再驱动纯条件出边
                if (result.isSuccess()) {
                    log.info("⏰ [定时评估] 命中延时规则: session={}", sessionId);
                } else {
                    log.warn("⏰ [定时评估] 异常命中但处理失败: session={}, msg={}",
                        sessionId, result.getErrorMessage());
                }
                return;
            }

            // ========== 第二级：状态机纯条件出边 ==========
            // 异常未命中。检查当前状态是否有依赖时间变量的纯条件出边，
            // 命中则推进状态机。
            String currentState = ctx.getCurrentState();
            try {
                StateTransitionResult transition =
                    stateMachineFacade.reEvaluateOnContextChange(sessionId, null);

                if (transition != null && transition.isSuccess()) {
                    String newState = transition.getToState();
                    log.info("⏰ [定时评估] 纯条件出边驱动成功: session={}, {} -> {}",
                        sessionId, currentState, newState);

                    try {
                        phaseManager.updateAfterTransition(
                            sessionId, config, newState, null);
                    } catch (Exception e) {
                        log.warn("⏰ [定时评估] Phase 更新失败: session={}, err={}",
                            sessionId, e.getMessage());
                    }
                } else {
                    // 常见情况：当前状态无纯条件出边（内部已短路，开销极小）
                    if (log.isDebugEnabled()) {
                        log.debug("⏰ [定时评估] 无纯条件出边命中: session={}, state={}, reason={}",
                            sessionId, currentState,
                            transition != null ? transition.getReason() : "null");
                    }
                }
            } catch (Exception e) {
                log.warn("⏰ [定时评估] 纯条件出边驱动异常: session={}, err={}",
                    sessionId, e.getMessage());
            }

        } catch (Exception e) {
            log.warn("定时异常评估失败: session={}, err={}", sessionId, e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    // ====================================================================
    // ★ 异常层
    // ====================================================================

    private static final String ERROR_CHAIN_KEY = "evaluation.errorChain";

    private CommandResult tryHandleExceptions(String sessionId,
                                              String caseType,
                                              String intentId,
                                              String currentPhase,
                                              CaseContext freshContext,
                                              ResolvedCaseTypeConfig config) {
        List<ExceptionRule> hits;
        try {
            hits = exceptionEngine.evaluate(caseType, intentId, currentPhase, freshContext);
        } catch (Exception e) {
            log.error("异常评估失败，跳过: session={}, intent={}", sessionId, intentId, e);
            return null;
        }
        if (hits == null || hits.isEmpty()) {
            return null;
        }

        if (intentId == null || intentId.isBlank()) {
            hits = filterByCooldown(sessionId, hits);
            if (hits.isEmpty()) {
                return null;
            }
        }
        ExceptionRule top = hits.get(0);
        log.warn("⚡ 异常命中: session={}, intent={}, phase={}, count={}, top={} [{}]",
            sessionId, intentId, currentPhase, hits.size(), top.getId(), top.getSeverity());

        if (intentId == null || intentId.isBlank()) {
            responseResolver.record(sessionId, StudentResponseBranch.DELAYED, null,
                "异常命中: " + top.getId());
        } else {
            responseResolver.record(sessionId, StudentResponseBranch.ERROR, intentId,
                "异常命中: " + top.getId());
        }

        Map<String, Object> allEffects = new LinkedHashMap<>();
        for (ExceptionRule rule : hits) {
            if (rule.getEffects() == null) continue;
            for (ExceptionRule.EffectRule eff : rule.getEffects()) {
                if (eff == null || eff.getVar() == null) continue;
                Object v = resolveEffectValue(eff, freshContext);
                allEffects.put(eff.getVar(), v);
            }
        }
        if (!allEffects.isEmpty()) {
            persistUpdates(sessionId, allEffects, freshContext, "EXCEPTION_EFFECTS");
        }

        appendErrorChain(sessionId, hits, freshContext);

        TeachingMode mode = sessionService.getMode(sessionId);
        if (mode == null) mode = TeachingMode.EXAM;
        publishExceptionFeedback(sessionId, top, mode);

        boolean keepCurrent = Boolean.TRUE.equals(top.getKeepCurrent());
        String jumpTo = top.getJumpTo();

        sendUnityStateChange(sessionId, top.getUnityStateName());

        if (keepCurrent) {
            log.info("⚡ 异常命中（拦截类，不跳转）: session={}, rule={}, intent={}",
                sessionId, top.getId(), intentId);
        } else {
            if (jumpTo == null || jumpTo.isBlank()) {
                log.error("异常规则 jumpTo 为空且非 keepCurrent: rule={}", top.getId());
                return null;
            }
            String previousState = (freshContext != null) ? freshContext.getCurrentState() : null;
            if (previousState != null && !previousState.isBlank()) {
                Map<String, Object> anchor = new LinkedHashMap<>();
                anchor.put("process.deteriorationFrom", previousState);
                persistUpdates(sessionId, anchor, freshContext, "EXCEPTION_DETERIORATION");
            }
            boolean jumped = stateMachineFacade.forceTransition(sessionId, jumpTo, top.getId());
            if (!jumped) {
                log.error("异常 forceTransition 失败: session={}, target={}, rule={}",
                    sessionId, jumpTo, top.getId());
                return CommandResult.failure("异常跳转失败");
            }

            try {
                phaseManager.updateAfterTransition(sessionId, config, jumpTo, intentId);
            } catch (Exception e) {
                log.warn("异常跳转后 Phase 更新失败: session={}, err={}", sessionId, e.getMessage());
            }
        }

        String responseText = buildExceptionResponse(top, mode);
        Map<String, Object> updates = new LinkedHashMap<>(allEffects);
        if (!keepCurrent) {
            updates.put("currentState", jumpTo);
        }
        return CommandResult.builder()
            .success(true)
            .responseText(responseText)
            .contextUpdates(updates)
            .animations(java.util.Collections.emptyList())
            .build();
    }

    private List<ExceptionRule> filterByCooldown(String sessionId, List<ExceptionRule> hits) {
        long now = System.currentTimeMillis();
        Map<String, Long> sessionHistory =
            scheduledTriggerHistory.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());

        List<ExceptionRule> filtered = new ArrayList<>();
        for (ExceptionRule rule : hits) {
            Long last = sessionHistory.get(rule.getId());
            if (last != null && (now - last) < SCHEDULED_TRIGGER_COOLDOWN_MS) {
                log.debug("⏰ 规则 {} 在冷却窗口内（{}ms 前触发），跳过",
                    rule.getId(), now - last);
                continue;
            }
            sessionHistory.put(rule.getId(), now);
            filtered.add(rule);
        }
        return filtered;
    }

    private void sendUnityStateChange(String emsSessionId, String unityStateName) {
        sendUnityStateChange(emsSessionId, unityStateName, null);
    }

    private void sendUnityStateChange(String emsSessionId,
                                      String unityStateName,
                                      String operationId) {
        if (unityStateName == null || unityStateName.isBlank()) {
            log.warn("sendUnityStateChange: unityStateName 为空，跳过: ems={}", emsSessionId);
            return;
        }

        try {
            SessionRoom room = roomRegistry.getByEmsSessionId(emsSessionId).orElse(null);
            if (room == null) {
                log.warn("sendUnityStateChange: 找不到房间映射 emsSessionId={}", emsSessionId);
                return;
            }
            String roomSessionId = room.getSessionId();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stateName", unityStateName);
            if (operationId != null && !operationId.isBlank()) {
                data.put("operationId", operationId);
            }

            Map<String, Object> envelope =
                RoomBroadcaster.envelope("physiology_state_change", data);

            roomBroadcaster.sendToRoles(
                roomSessionId,
                java.util.Set.of(Role.ENGINE),
                envelope
            );

            log.info("📤 Unity 生理状态切换已下发: room={}, ems={}, stateName={}, op={}",
                roomSessionId, emsSessionId, unityStateName, operationId);
        } catch (Exception e) {
            log.error("Unity 生理状态切换下发失败: ems={}, stateName={}, op={}",
                emsSessionId, unityStateName, operationId, e);
        }
    }

    private Object resolveEffectValue(ExceptionRule.EffectRule eff, CaseContext ctx) {
        if (eff.getValue() != null) {
            return eff.getValue();
        }
        if (eff.getValueExpression() != null && !eff.getValueExpression().isBlank()) {
            try {
                return ruleEvaluator.evaluateValue(
                    eff.getValueExpression(), ctx, Object.class);
            } catch (Exception e) {
                log.warn("effect valueExpression 求值失败: var={}, expr={}",
                    eff.getVar(), eff.getValueExpression(), e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void appendErrorChain(String sessionId,
                                  List<ExceptionRule> hits,
                                  CaseContext ctx) {
        Object raw = ctx.getVariables() != null
            ? ctx.getVariables().get(ERROR_CHAIN_KEY)
            : null;

        List<Map<String, Object>> chain = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<Object>) raw) {
                if (o instanceof Map) chain.add((Map<String, Object>) o);
            }
        }

        for (ExceptionRule rule : hits) {
            long prev = chain.stream()
                .filter(e -> rule.getId().equals(e.get("ruleId")))
                .count();
            double decay = 1.0 / Math.pow(2, prev);
            int base = severityScore(rule.getSeverity());
            int finalScore = (int) Math.round(base * decay);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("ruleId", rule.getId());
            record.put("severity", rule.getSeverity());
            record.put("baseScore", base);
            record.put("decayFactor", decay);
            record.put("score", finalScore);
            record.put("patientSnapshot", takePatientSnapshot(ctx));
            record.put("timestamp", System.currentTimeMillis());
            chain.add(record);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(ERROR_CHAIN_KEY, chain);
        persistUpdates(sessionId, updates, ctx, "EXCEPTION_ERROR_CHAIN");

        log.info("🔗 errorChain 现长 {} 条，本轮新增 {} 条，最后一条: {}",
            chain.size(), hits.size(), chain.get(chain.size() - 1));
    }

    private int severityScore(String severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case "CRITICAL" -> 20;
            case "MINOR"    -> 10;
            case "TIMING"   -> 5;
            default         -> 0;
        };
    }

    private Map<String, Object> takePatientSnapshot(CaseContext ctx) {
        Map<String, Object> snap = new LinkedHashMap<>();
        if (ctx.getVariables() == null) return snap;
        for (String key : new String[]{
            "phys.rhythm", "phys.heartRate", "phys.systolicBp",
            "phys.diastolicBp", "phys.spo2", "phys.etco2",
            "phys.respiratoryRate", "phys.consciousness", "phys.hasPulse",
            "derived.stable"
        }) {
            if (ctx.getVariables().containsKey(key)) {
                snap.put(key, ctx.getVariables().get(key));
            }
        }
        return snap;
    }

    private void publishExceptionFeedback(String sessionId,
                                          ExceptionRule top,
                                          TeachingMode mode) {
        ExceptionRule.Feedback.ModeFeedback mf = pickModeFeedback(top, mode);
        if (mf == null || mf.getTts() == null || mf.getTts().isBlank()) {
            return;
        }
        try {
            ttsService.speak(sessionId, mf.getTts());
            log.debug("异常反馈 TTS: session={}, mode={}, interrupt={}",
                sessionId, mode, mf.getInterruptCurrentTts());
        } catch (Exception e) {
            log.warn("异常反馈 TTS 失败: session={}, err={}", sessionId, e.getMessage());
        }
    }

    private String buildExceptionResponse(ExceptionRule top, TeachingMode mode) {
        if (mode == TeachingMode.EXAM) {
            return "指令已记录";
        }
        ExceptionRule.Feedback.ModeFeedback mf = pickModeFeedback(top, mode);
        if (mf != null && mf.getTts() != null && !mf.getTts().isBlank()) {
            return mf.getTts();
        }
        return "指令已记录";
    }

    private ExceptionRule.Feedback.ModeFeedback pickModeFeedback(
        ExceptionRule rule, TeachingMode mode) {
        ExceptionRule.Feedback fb = rule.getFeedback();
        if (fb == null) return null;
        return switch (mode) {
            case TEACHING -> fb.getTeaching();
            case LEARNING -> fb.getLearning();
            case EXAM     -> fb.getExam();
        };
    }

    private CommandContext buildCommandContext(String sessionId, CaseContext caseContext,
                                               IntentCommand command) {
        CommandContext context = new CommandContext(
            sessionId,
            teamManager,
            caseContext,
            ttsService,
            vrClientService,
            ackManager
        );

        context.setVariable("actType", command.getActType());
        context.setVariable("confidence", command.getConfidence());
        context.setVariable("intentCode", command.getIntentCode());
        return context;
    }

    private CommandResult executeAction(IntentCommand command,
                                        CommandContext context,
                                        CaseContext caseContext) {
        String sessionId = context.getSessionId();
        String intentId = command.getIntentCode();
        Map<String, Object> slots = command.getSlots();

        RouteDecision decision = actTypeRouter.route(command, caseContext);

        switch (decision.getActionType()) {
            case QUERY_RESPOND:
                String queryResponse = decision.getResponseText();
                if (queryResponse != null) {
                    ttsService.speak(sessionId, queryResponse);
                }
                return CommandResult.success(queryResponse != null ? queryResponse : "查询完成");

            case STATE_UPDATE:
                persistUpdates(sessionId, decision.getContextUpdates(), caseContext,
                    "STATE_UPDATE");
                return CommandResult.success(decision.getResponseText());

            case TASK_CANCEL:
                return CommandResult.success("已取消当前操作");

            case CONFIRM_ACK:
                return CommandResult.success(decision.getResponseText());

            case OVERRIDE_JUMP:
                persistUpdates(sessionId, decision.getContextUpdates(), caseContext,
                    "OVERRIDE_JUMP");
                return CommandResult.success(decision.getResponseText());

            case BLOCKED:
                return CommandResult.failure(decision.getResponseText());

            case CLARIFY:
                return CommandResult.clarify(decision.getClarifyQuestion());

            case EXECUTE_SKILL:
                return actTypeRouter.executeSkill(
                    decision.getSkillId(),
                    context,
                    decision.getParams() != null ? decision.getParams() : slots
                );

            case NONE:
            default:
                log.info("意图 {} 无需执行动作（纯认知操作）", intentId);
                return CommandResult.success("指令已记录");
        }
    }

    private void persistUpdates(String sessionId,
                                Map<String, Object> updates,
                                CaseContext snapshot,
                                String source) {
        if (updates == null || updates.isEmpty()) {
            log.debug("persistUpdates: 无更新内容, session={}, source={}", sessionId, source);
            return;
        }

        long baseVersion = snapshot != null ? snapshot.getVersion() : 0L;

        boolean ok = contextManager.batchSetVarsWithVersion(
            sessionId, updates, source, baseVersion);

        if (!ok) {
            CaseContext latest = contextManager.getSnapshot(sessionId);
            if (latest != null) {
                ok = contextManager.batchSetVarsWithVersion(
                    sessionId, updates, source, latest.getVersion());
            }
        }

        if (ok) {
            log.info("✅ {} 变量已写入: session={}, keys={}",
                source, sessionId, updates.keySet());
        } else {
            log.warn("⚠️ {} 变量写入失败（重试后仍失败）: session={}, keys={}",
                source, sessionId, updates.keySet());
        }
    }
}

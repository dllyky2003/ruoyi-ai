package com.ruoyi.acls.engine;

import com.ruoyi.acls.arbiter.ArbiterResult;
import com.ruoyi.acls.arbiter.OperationArbiter;
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
import com.ruoyi.ems.engine.PhaseManager;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.exception.ExceptionEngine;
import com.ruoyi.ems.npc.IVrClientService;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.rule.IRuleEvaluator;
import com.ruoyi.ems.service.SessionService;
import com.ruoyi.ems.tts.KokoroTtsClient;
import com.ruoyi.ems.websocket.WebSocketMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

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
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "acls.engine.mode", havingValue = "hybrid", matchIfMissing = true)
@RequiredArgsConstructor
public class AclsCommandEngine {

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


    /**
     * 处理学员指令
     */
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
            // 避免下游 getConfig(null) 抛 NPE
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
                // ★ Phase 软警告只记日志，不播 TTS。
                //   原因：ttsService.speak() → speakStream，
                //   conn.getResponseCode() / line.write() / line.drain() 全同步阻塞，
                //   单条指令耗时 10-15s，25 条累计约 4 分钟。
                //   Phase 软警告属提示性反馈，非关键路径，不播报不影响业务流程。
                //   后续如恢复播报，改用异步方案（需先给 KokoroTtsClient 加 per-session 队列）。
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
            // 禁忌/拦截类意图必须在这里被捕获，不能落到 executeAction。
            // 传 caseContext（不是 freshContext，后者在 7.6 才生成）；
            // 异常层需要的 derived.stable、phys.hasPulse 在 caseContext 里都有。
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
                return result;
            }

            // ========== 7.5 ★ P0-2：持久化技能返回的 contextUpdates ==========
            if (result.getContextUpdates() != null && !result.getContextUpdates().isEmpty()) {
                persistUpdates(sessionId, result.getContextUpdates(), caseContext,
                    "SKILL_RESULT");
            }

            // ========== 7.6 ★ P0-3：动作执行后重新读取最新快照 ==========
            CaseContext freshContext = contextManager.getSnapshot(sessionId);
            if (freshContext == null) {
                log.error("❌ 执行动作后无法获取最新上下文: session={}", sessionId);
                return CommandResult.failure("执行动作后上下文丢失");
            }
            log.debug("🔄 执行动作后刷新上下文: session={}, version={}",
                sessionId, freshContext.getVersion());

            // （原 7.7 步「异常层评估」已前置到 6.5，此处删除）

            // ========== 8. ★ 根据执行结果驱动状态机（使用最新快照） ==========
            String newState = stateMachineFacade.selectAndTransition(
                sessionId, config, intentId, freshContext
            );

            // ★ 学员意图无匹配 → 回退到变量驱动重评估（走纯条件出边）
            if (newState == null) {
                log.info("学员意图 {} 无匹配转移，尝试变量驱动重评估: session={}, state={}",
                    intentId, sessionId, currentState);
                newState = stateMachineFacade.reEvaluateOnContextChange(sessionId, intentId);
            }

            if (newState == null) {
                log.warn("⛔ 状态机无匹配转移: session={}, intent={}, state={}",
                    sessionId, intentId, currentState);
                return CommandResult.builder()
                    .success(false)
                    .errorMessage("当前临床流程不允许此操作")
                    .build();
            }

            log.info("✅ 状态机转换成功: {} -> {}", currentState, newState);

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

            // ========== 11. 构建返回结果 ==========
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
                .build();

        } catch (Exception e) {
            log.error("❌ 处理指令异常: session={}, intent={}", sessionId, intentId, e);
            return CommandResult.failure("处理指令异常: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    // ====================================================================
    // ★ 异常层
    // ====================================================================

    private static final String ERROR_CHAIN_KEY = "evaluation.errorChain";

    /**
     * 异常层评估与执行。
     *
     * <p>未命中 → 返回 null，调用方继续正常流程。
     * <p>命中 → 执行 effects 全写 + errorChain 记录 + 按模式反馈
     *          + 下发 Unity state_change + forceTransition + Phase 更新，
     *          返回 CommandResult。
     */
    private CommandResult tryHandleExceptions(String sessionId,
                                              String caseType,
                                              String intentId,
                                              String currentPhase,
                                              CaseContext freshContext,
                                              ResolvedCaseTypeConfig config) {
        // 1. 评估
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

        ExceptionRule top = hits.get(0);
        log.warn("⚡ 异常命中: session={}, intent={}, phase={}, count={}, top={} [{}]",
            sessionId, intentId, currentPhase, hits.size(), top.getId(), top.getSeverity());

        // 2. 所有命中的 effects 全写（不取最高，全写）
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

        // 3. errorChain 记录（含 patientSnapshot + 扣分衰减）
        appendErrorChain(sessionId, hits, freshContext);

        // 4. 按模式发 TTS
        TeachingMode mode = sessionService.getMode(sessionId);
        if (mode == null) mode = TeachingMode.EXAM;   // 保守默认
        publishExceptionFeedback(sessionId, top, mode);

        // 5. 判断 keepCurrent / 取 jumpTo
        boolean keepCurrent = Boolean.TRUE.equals(top.getKeepCurrent());
        String jumpTo = top.getJumpTo();

// 6. 先发 Unity 生理状态切换（keepCurrent 也发——keepCurrent 只影响状态机，不影响生理）
        sendUnityStateChange(sessionId, top.getUnityStateName());

        if (keepCurrent) {
            // 6a. 拦截类：不跳转状态机，不更新 Phase
            log.info("⚡ 异常命中（拦截类，不跳转）: session={}, rule={}, intent={}",
                sessionId, top.getId(), intentId);
        } else {
            // 6b. 跳转类：必须 jumpTo
            if (jumpTo == null || jumpTo.isBlank()) {
                log.error("异常规则 jumpTo 为空且非 keepCurrent: rule={}", top.getId());
                return null;
            }

            // 7. forceTransition（服务器乐观先行）
            boolean jumped = stateMachineFacade.forceTransition(sessionId, jumpTo, top.getId());
            if (!jumped) {
                log.error("异常 forceTransition 失败: session={}, target={}, rule={}",
                    sessionId, jumpTo, top.getId());
                return CommandResult.failure("异常跳转失败");
            }

            // 8. Phase 更新
            try {
                phaseManager.updateAfterTransition(sessionId, config, jumpTo, intentId);
            } catch (Exception e) {
                log.warn("异常跳转后 Phase 更新失败: session={}, err={}", sessionId, e.getMessage());
            }
        }

// 9. 构建返回
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

    /**
     * 向 Unity 下发生理状态切换指令。
     *
     * <p>执行时序（交接文档 3.7）：
     * <ol>
     *   <li>effects 写入 EmsContextManager（tryHandleExceptions 第 2 步已完成）</li>
     *   <li><b>本方法</b>：发 state_change(stateName) 给 Unity</li>
     *   <li>forceTransition(jumpTo)</li>
     *   <li>返回"操作已收到"</li>
     * </ol>
     *
     * <p>服务器乐观先行：不等 Unity 2.5s 过渡，立即返回。Unity 的 vitals_update
     * 到达后，由 vitals 对比服务自行校验一致性。
     *
     * @param sessionId      会话 id
     * @param unityStateName Unity 有效状态名（来自 ExceptionRule.unityStateName）
     */
    /**
     * 向 Unity 下发生理状态切换指令。
     *
     * <p>★ 通道修正：消息通过 RoomBroadcaster 走 {@code /ws/session/{roomSessionId}} 通道，
     * 不再走 {@code WebSocketMessageSender}（那是 ASR 语音通道，Unity 收不到）。</p>
     *
     * <p>★ 结构修正：改为 {@code {type, data: {stateName}, timestamp}} 嵌套结构，
     * 与 Unity 侧 EngineJson.ParseEnvelope 的解析逻辑对齐。</p>
     *
     * @param emsSessionId   业务会话 id（AclsCommandEngine 上下文的 sessionId）
     * @param unityStateName Unity 有效状态名（中文 stateName，如 "室上速(SVT)"）
     */
    private void sendUnityStateChange(String emsSessionId, String unityStateName) {
        if (unityStateName == null || unityStateName.isBlank()) {
            log.warn("sendUnityStateChange: unityStateName 为空，跳过: ems={}", emsSessionId);
            return;
        }
        try {
            // 1. 反查房间（emsSessionId → roomSessionId）
            SessionRoom room = roomRegistry.getByEmsSessionId(emsSessionId).orElse(null);
            if (room == null) {
                log.warn("sendUnityStateChange: 找不到房间映射 emsSessionId={}", emsSessionId);
                return;
            }
            String roomSessionId = room.getSessionId();

            // 2. 嵌套 data 结构（与 RoomBroadcaster.envelope 对齐）
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stateName", unityStateName);
            Map<String, Object> envelope =
                RoomBroadcaster.envelope("physiology_state_change", data);

            // 3. 定向发给 Unity（ENGINE 角色）
            roomBroadcaster.sendToRoles(
                roomSessionId,
                java.util.Set.of(Role.ENGINE),
                envelope
            );

            log.info("📤 Unity 生理状态切换已下发: room={}, ems={}, stateName={}",
                roomSessionId, emsSessionId, unityStateName);
        } catch (Exception e) {
            // 下发失败不阻断异常处理：effects 已写、forceTransition 会执行，
            // 只是 Unity 侧生理状态没切。记 error 供人工核查。
            log.error("Unity 生理状态切换下发失败: ems={}, stateName={}",
                emsSessionId, unityStateName, e);
        }
    }

    /**
     * effect 取值：value 优先，valueExpression 次之。
     */
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

    /**
     * errorChain 追加记录。
     * 结构：List<Map>，每条含 ruleId / severity / score / decayFactor /
     *       patientSnapshot / timestamp。
     * 扣分衰减：同 ruleId 已出现 n 次 → 系数 1/2^n。
     */
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


        // ★ 临时验证日志（验证完删掉）
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

    /**
     * 按模式发 TTS。仅发 severity 最高的规则的 feedback
     * （多规则命中时，TTS 只播一条，避免叠加打架）。
     */
    private void publishExceptionFeedback(String sessionId,
                                          ExceptionRule top,
                                          TeachingMode mode) {
        ExceptionRule.Feedback.ModeFeedback mf = pickModeFeedback(top, mode);
        if (mf == null || mf.getTts() == null || mf.getTts().isBlank()) {
            return;
        }
        try {
            // TODO: KokoroTtsClient 若支持打断，则按 mf.getInterruptCurrentTts() 处理
            ttsService.speak(sessionId, mf.getTts());
            log.debug("异常反馈 TTS: session={}, mode={}, interrupt={}",
                sessionId, mode, mf.getInterruptCurrentTts());
        } catch (Exception e) {
            log.warn("异常反馈 TTS 失败: session={}, err={}", sessionId, e.getMessage());
        }
    }

    private String buildExceptionResponse(ExceptionRule top, TeachingMode mode) {
        if (mode == TeachingMode.EXAM) {
            return "指令已记录";   // 考核静默
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

    /**
     * 构建执行上下文
     */
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
        return context;
    }

    /**
     * ★ 执行动作（状态机之前）
     *
     * <p>根据 actType 分流到不同的执行路径。
     *
     * <p>★ P0-2 修复：STATE_UPDATE / OVERRIDE_JUMP 不再直接写 caseContext 副本，
     * 而是通过 {@link #persistUpdates} 写入 EmsContextManager。
     */
    private CommandResult executeAction(IntentCommand command,
                                        CommandContext context,
                                        CaseContext caseContext) {
        String sessionId = context.getSessionId();
        String intentId = command.getIntentCode();
        Map<String, Object> slots = command.getSlots();

        // ===== 1. 语用分流 =====
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

    /**
     * ★ P0-2：统一变量写入入口。
     *
     * <p>带版本冲突重试：若第一次写入因乐观锁版本过期失败，
     * 重新拉取最新快照后再试一次。
     *
     * @param sessionId   会话 ID
     * @param updates     待写入变量（支持 vars.xxx、process.xxx、device.xxx 等）
     * @param snapshot    写入前的上下文快照（用于取版本号）
     * @param source      来源标记，用于日志与审计（ACTION / STATE_UPDATE / SKILL_RESULT 等）
     */
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

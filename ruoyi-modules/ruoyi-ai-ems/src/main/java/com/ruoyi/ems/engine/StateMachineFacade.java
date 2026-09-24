package com.ruoyi.ems.engine;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;

import com.ruoyi.ems.config.loader.PhysiologyConfigLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.StatePhysiology;
import com.ruoyi.ems.rule.IRuleEvaluator;
import com.ruoyi.ems.spi.AttemptEventSink;
import com.ruoyi.ems.spi.ClinicalRuleService;
import com.ruoyi.ems.spi.StateTransitionHook;
import com.ruoyi.ems.spi.TemporalEventSink;
import com.ruoyi.ems.spi.dto.AttemptEventContext;
import com.ruoyi.ems.spi.dto.ClinicalRuleContext;
import com.ruoyi.ems.spi.dto.ExecutionStrategy;
import com.ruoyi.ems.spi.dto.TransitionContext;
import com.ruoyi.ems.spi.dto.ValidationResult;
import com.ruoyi.ems.websocket.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class StateMachineFacade {

    private final Map<String, StateMachine<String, String>> machineCache = new ConcurrentHashMap<>();
    private final Map<String, ResolvedCaseTypeConfig> configCache = new ConcurrentHashMap<>();
    private final StateMachineBuilderFactory builderFactory;
    private final RedisStatePersister persister;
    private final EmsContextManager contextManager;
    private final IRuleEvaluator ruleEvaluator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebSocketMessageSender messageSender;
    private final PhysiologyConfigLoader physiologyConfigLoader;

    /** ★ 第六步：tutor 阻断灰度开关，默认 false（关闭） */
    private final boolean tutorBlockEnabled;

    private final ObjectProvider<ClinicalRuleService> clinicalRuleServiceProvider;
    private final ObjectProvider<TemporalEventSink> temporalEventSinkProvider;
    private final ObjectProvider<StateTransitionHook> stateTransitionHookProvider;
    /** ★ 步骤3新增：迁移提交后回调 tutor */
    private final ObjectProvider<AttemptEventSink> attemptEventSinkProvider;

    private static final String SESSION_CONTEXT_PREFIX = "ems:session:context:";
    private static final long SESSION_TIMEOUT_HOURS = 24;

    public StateMachineFacade(StateMachineBuilderFactory builderFactory,
                              RedisStatePersister persister,
                              EmsContextManager contextManager,
                              IRuleEvaluator ruleEvaluator,
                              RedisTemplate<String, Object> redisTemplate,
                              WebSocketMessageSender messageSender,
                              PhysiologyConfigLoader physiologyConfigLoader,
                              ObjectProvider<ClinicalRuleService> clinicalRuleServiceProvider,
                              ObjectProvider<TemporalEventSink> temporalEventSinkProvider,
                              ObjectProvider<StateTransitionHook> stateTransitionHookProvider,
                              ObjectProvider<AttemptEventSink> attemptEventSinkProvider,
                              @Value("${acls.tutor.block-enabled:false}") boolean tutorBlockEnabled) {
        this.builderFactory = builderFactory;
        this.persister = persister;
        this.contextManager = contextManager;
        this.ruleEvaluator = ruleEvaluator;
        this.redisTemplate = redisTemplate;
        this.messageSender = messageSender;
        this.physiologyConfigLoader = physiologyConfigLoader;
        this.clinicalRuleServiceProvider = clinicalRuleServiceProvider;
        this.temporalEventSinkProvider = temporalEventSinkProvider;
        this.stateTransitionHookProvider = stateTransitionHookProvider;
        this.attemptEventSinkProvider = attemptEventSinkProvider;
        this.tutorBlockEnabled = tutorBlockEnabled;
        log.info("[StateMachineFacade] tutorBlockEnabled={}", tutorBlockEnabled);
    }

    /**
     * 获取或创建状态机实例（线程安全）。
     */
    @SuppressWarnings("deprecation")
    public StateMachine<String, String> getOrCreateMachine(String instanceId, ResolvedCaseTypeConfig config) {
        StateMachine<String, String> sm = machineCache.get(instanceId);
        if (sm == null) {
            synchronized (this) {
                sm = machineCache.get(instanceId);
                if (sm == null) {
                    StateMachineBuilder.Builder<String, String> builder = builderFactory.getOrCreateBuilder(config);
                    sm = builder.build();

                    sm.getExtendedState().getVariables().put("sessionId", instanceId);

                    sm.getStateMachineAccessor().doWithAllRegions(access -> {
                        access.resetStateMachine(
                            new DefaultStateMachineContext<>(
                                config.getInitialState() != null ? config.getInitialState() : "SCENE_START",
                                null, null, null
                            )
                        );
                    });
                    restoreState(sm, instanceId);

                    machineCache.put(instanceId, sm);
                    configCache.put(instanceId, config);
                }
            }
        }
        return sm;
    }

    public String getCurrentState(String instanceId, ResolvedCaseTypeConfig config) {
        StateMachine<String, String> sm = getOrCreateMachine(instanceId, config);
        return sm.getState().getId();
    }

    public StatePhysiology getCurrentPhysiology(String instanceId, ResolvedCaseTypeConfig config) {
        String currentState = getCurrentState(instanceId, config);
        if (currentState == null) {
            return null;
        }
        String caseId = config.getCode();
        try {
            return physiologyConfigLoader.getState(caseId, currentState);
        } catch (IllegalArgumentException e) {
            log.warn("当前状态 {} 在生理配置中未定义: {}", currentState, e.getMessage());
            return null;
        }
    }

    /**
     * ★ 核心方法：业务层主导的状态转移（带冲突消解、偏差标记、审计日志）。
     *
     * <p>第六步新增：读取 tutor 返回的 {@link ValidationResult}。
     * <ul>
     *   <li>灰度关闭（默认）→ 完全忽略返回值，行为不变</li>
     *   <li>灰度打开：
     *     <ul>
     *       <li>{@code BLOCK} → 不进入转移，返回 null</li>
     *       <li>{@code ALLOW_WITH_WARNING} → 记录警告后继续转移</li>
     *       <li>其他（含 {@code ALLOW}）→ 继续转移</li>
     *     </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * <p>★ 步骤3新增：迁移成功后回调 {@link AttemptEventSink#onTransitionCommitted}，
     * 供 tutor 侧推进 eventSequence 与 beat 选择。</p>
     */
    public String selectAndTransition(String instanceId,
                                      ResolvedCaseTypeConfig config,
                                      String intentCode,
                                      CaseContext ctx) {
        // ★ SPI-1: 临床规则校验
        ValidationResult vr = fireClinicalRuleValidate(instanceId, config, intentCode, ctx);

        // 记录迁移前状态（用于 AttemptEventSink 回调）
        String previousState = ctx != null ? ctx.getCurrentState() : null;

        // ★ 第六步：按 decision 处理（默认灰度关闭时短路，行为不变）
        if (tutorBlockEnabled && vr != null && vr.getDecision() != null) {
            ExecutionStrategy decision = vr.getDecision();

            if (decision == ExecutionStrategy.BLOCK) {
                log.warn("[Tutor][BLOCK] session={}, case={}, state={}, intent={}, "
                        + "ruleId={}, severity={}, feedbackKeys={}",
                    instanceId,
                    config != null ? config.getCode() : null,
                    ctx != null ? ctx.getCurrentState() : null,
                    intentCode,
                    vr.getPrimaryRuleId(),
                    vr.getSeverity(),
                    vr.getFeedbackKeys());
                recordTutorDecisionAudit(instanceId, "BLOCK", intentCode, ctx, vr);

                // ★ 抛异常而非 return null：
                //   - 让 AclsCommandEngine 区分"BLOCK"和"无匹配规则"
                //   - 避免被 fallback 到 reEvaluateOnContextChange 绕过
                String feedback = vr.getFeedbackText();

                // 回退 1：用 feedbackKey（文案未解析出时，至少让学员看到 key）
                if (feedback == null || feedback.isBlank()) {
                    if (vr.getFeedbackKeys() != null && !vr.getFeedbackKeys().isEmpty()) {
                        feedback = vr.getFeedbackKeys().get(0);
                    }
                }

                // 回退 2：通用兜底
                if (feedback == null || feedback.isBlank()) {
                    feedback = "当前临床流程不允许此操作";
                }

                throw new TutorBlockedException(vr.getPrimaryRuleId(), vr.getSeverity(), feedback);
            }

            if (decision == ExecutionStrategy.ALLOW_WITH_WARNING) {
                log.warn("[Tutor][WARN] session={}, case={}, state={}, intent={}, "
                        + "ruleId={}, severity={}, feedbackKeys={}",
                    instanceId,
                    config != null ? config.getCode() : null,
                    ctx != null ? ctx.getCurrentState() : null,
                    intentCode,
                    vr.getPrimaryRuleId(),
                    vr.getSeverity(),
                    vr.getFeedbackKeys());
                recordTutorDecisionAudit(instanceId, "ALLOW_WITH_WARNING", intentCode, ctx, vr);
                // 继续往下走
            }
        }

        String newState = doSelectAndTransition(instanceId, config, intentCode, ctx, 0);

        // ★ 步骤3新增：迁移提交后回调 tutor AttemptEventSink
        if (newState != null) {
            fireAttemptEventCommitted(instanceId, config, previousState, newState, intentCode, ctx, vr);
        }

        return newState;
    }

    /**
     * ★ 状态转移核心实现（带 depth 递归保护）。
     *
     * <p>depth 语义：
     * <ul>
     *   <li>0：外部调用入口（通过 public selectAndTransition 进入）</li>
     *   <li>≥1：自循环 effects 后的内部重评估（由本方法末尾触发，intentCode 传 null）</li>
     *   <li>depth >= 2 时不再触发自循环重评估，防止配置错误导致无限递归</li>
     * </ul>
     */
    @SuppressWarnings({"deprecation", "unchecked"})
    private String doSelectAndTransition(String instanceId,
                                         ResolvedCaseTypeConfig config,
                                         String intentCode,
                                         CaseContext ctx,
                                         int depth) {

        StateMachine<String, String> sm = getOrCreateMachine(instanceId, config);
        String currentState = sm.getState().getId();

        // 1. 获取当前状态下所有转移规则
        List<TransitionRule> candidates = config.getTransitionsBySource(currentState);
        if (candidates.isEmpty()) {
            log.warn("当前状态 {} 无任何转移规则", currentState);
            return null;
        }

        log.debug("状态 [{}] 共有 {} 条候选转移规则:", currentState, candidates.size());
        for (TransitionRule r : candidates) {
            log.debug("  候选: intent={}, event={}, condition={}, priority={}, target={}, deviation={}, enabled={}",
                r.getIntentCode(), r.getEvent(), r.getCondition(), r.getPriority(), r.getTarget(), r.isDeviation(), r.isEnabled());
        }

        // 2. 快照隔离：复制当前上下文变量（用于审计日志）
        Map<String, Object> snapshot = new HashMap<>(ctx.getVariables());

        boolean studentIntent = StringUtils.hasText(intentCode);

        // 3. 遍历求值，收集命中的候选
        List<TransitionRule> matched = new ArrayList<>();
        for (TransitionRule rule : candidates) {
            if (!rule.isEnabled()) {
                log.debug("  规则 [{}] 未启用 (enabled=false)", rule.getDisplayName());
                continue;
            }

            boolean intentMatches;
            if (studentIntent) {
                if (StringUtils.hasText(rule.getIntentCode())) {
                    intentMatches = rule.getIntentCode().equals(intentCode);
                } else if (StringUtils.hasText(rule.getEvent())) {
                    intentMatches = rule.getEvent().equals(intentCode);
                } else {
                    intentMatches = false;
                }
            } else {
                intentMatches = !StringUtils.hasText(rule.getIntentCode())
                    && !StringUtils.hasText(rule.getEvent());
            }

            if (!intentMatches) {
                continue;
            }

            boolean guardPassed = true;
            if (rule.hasCondition()) {
                try {
                    guardPassed = ruleEvaluator.evaluateCondition(rule.getCondition(), ctx);
                    log.info("  规则 [{}] Guard 求值结果: {} (condition={})",
                        rule.getDisplayName(), guardPassed, rule.getCondition());
                } catch (Exception e) {
                    log.warn("Guard 求值异常: rule={}, condition={}, error={}",
                        rule.getDisplayName(), rule.getCondition(), e.getMessage());
                    guardPassed = false;
                }
            } else {
                log.debug("  规则 [{}] 无 Guard，默认通过", rule.getDisplayName());
            }

            if (guardPassed) {
                matched.add(rule);
                log.debug("  规则 [{}] 已加入匹配列表", rule.getDisplayName());
            }
        }

        // 4. 无匹配
        if (matched.isEmpty()) {
            long intentMatchedCount = candidates.stream()
                .filter(TransitionRule::isEnabled)
                .filter(r -> {
                    if (studentIntent) {
                        if (StringUtils.hasText(r.getIntentCode())) {
                            return r.getIntentCode().equals(intentCode);
                        }
                        if (StringUtils.hasText(r.getEvent())) {
                            return r.getEvent().equals(intentCode);
                        }
                        return false;
                    } else {
                        return !StringUtils.hasText(r.getIntentCode())
                            && !StringUtils.hasText(r.getEvent());
                    }
                })
                .count();

            log.info("无匹配转移: state={}, intent={}, studentIntent={}",
                currentState, intentCode, studentIntent);
            log.warn("详细匹配失败原因: 状态 [{}] 共有 {} 条候选，其中意图匹配的规则数为 {}，但 Guard 均未通过或不存在。",
                currentState, candidates.size(), intentMatchedCount);

            boolean hasIntentEntry = intentMatchedCount > 0;

            if (studentIntent && hasIntentEntry) {
                log.info("学员意图 {} 在状态 {} 有入口但 Guard 未通过，拒绝执行（不走默认兜底）",
                    intentCode, currentState);
                return null;
            }

            log.info("学员意图 {} 在状态 {} 无入口，尝试默认转移兜底",
                intentCode, currentState);
            return handleDefaultTransition(config, intentCode, sm, instanceId);
        }

        // 5. 冲突消解：按 priority 降序排序
        matched.sort((a, b) -> Integer.compare(
            b.getPriority() != null ? b.getPriority() : 0,
            a.getPriority() != null ? a.getPriority() : 0
        ));

        int topPriority = matched.get(0).getPriority() != null ? matched.get(0).getPriority() : 0;
        long conflictCount = matched.stream()
            .filter(r -> (r.getPriority() != null ? r.getPriority() : 0) == topPriority)
            .count();

        if (conflictCount > 1) {
            String conflictDescs = matched.stream()
                .filter(r -> (r.getPriority() != null ? r.getPriority() : 0) == topPriority)
                .map(TransitionRule::getDisplayName)
                .collect(Collectors.joining(", "));
            throw new IllegalStateException(String.format(
                "配置错误：状态 %s 下 %d 条转移 priority 相同 (%d)。冲突规则: %s",
                currentState, conflictCount, topPriority, conflictDescs
            ));
        }

        TransitionRule selected = matched.get(0);

        // 6. 审计日志
        logAuditTrail(instanceId, currentState, matched, selected, snapshot);

        // ★ SPI-2: 转移前钩子（仅 depth=0，避免自循环递归重复触发）
        if (depth == 0) {
            fireBeforeTransition(instanceId, config, currentState, selected.getTarget(), intentCode, ctx);
        }

        // 7. 应用 transition effects（exitReason + effects）
        applyTransitionEffects(instanceId, selected, ctx);

        // 8. 驱动 Spring StateMachine 到目标状态
        final String targetState = selected.getTarget();

        try {
            sm.getStateMachineAccessor().doWithAllRegions(access -> {
                access.resetStateMachine(
                    new DefaultStateMachineContext<String, String>(
                        targetState,
                        null,
                        null,
                        null
                    )
                );
            });
        } catch (Exception e) {
            log.error("状态机重置失败，尝试 sendEvent 降级", e);
            boolean accepted = sm.sendEvent(intentCode);
            if (!accepted) {
                log.warn("sendEvent 降级也失败");
                return null;
            }
            String newState = sm.getState().getId();
            persistState(sm, instanceId);
            recordTransitionHistory(instanceId, currentState, newState, intentCode, selected.isDeviation());
            pushPhysiologyUpdate(instanceId, config, newState);
            return newState;
        }

        // 9. 持久化状态机
        persistState(sm, instanceId);

        // 10. 记录转移历史
        recordTransitionHistory(instanceId, currentState, targetState, intentCode, selected.isDeviation());

        // 11. 偏差警告
        if (selected.isDeviation()) {
            log.warn("⚠️ [DEVIATION] 学员选择非推荐路径: state={}, intent={}, target={}",
                currentState, intentCode, targetState);
        }

        log.info("转移成功: {} -> {} (intent={})", currentState, targetState, intentCode);
        pushStateChange(instanceId, currentState, targetState, intentCode);

        // ★ SPI-3: 转移后钩子 + 时间线事件（仅 depth=0）
        if (depth == 0) {
            fireAfterTransition(instanceId, config, currentState, targetState, intentCode, ctx);
        }

        // 12. onEnterRules 已废弃：派生变量由 DerivationService 自动重算，无需在状态进入时手动执行。
        //     如果未来有"状态进入副作用"，应通过 transition 的 effects 声明。

        // 13. 推送新状态的生理基线
        pushPhysiologyUpdate(instanceId, config, targetState);

        // 14. ★ 自循环 + effects 已写 → 立即重评估一次，避免依赖外部指令再次驱动
        if (Objects.equals(currentState, targetState) && depth < 2) {
            CaseContext latest = contextManager.getSnapshot(instanceId);
            if (latest != null) {
                log.info("🔁 [自循环重评估] state={}，effects 已写入，立即重评估条件出边 (depth={}, intent={})",
                    targetState, depth, intentCode);
                String next = doSelectAndTransition(instanceId, config, intentCode, latest, depth + 1);
                if (next != null && !Objects.equals(next, targetState)) {
                    return next;
                }
            }
        }

        return targetState;
    }

    /**
     * ★ 应用 transition 副作用（替代 SpEL Action）。
     */
    private void applyTransitionEffects(String instanceId, TransitionRule rule, CaseContext ctx) {
        if (rule == null) return;

        Map<String, Object> updates = new LinkedHashMap<>();

        if (StringUtils.hasText(rule.getExitReason())) {
            updates.put("process.returnToParent", rule.getExitReason());
        }

        if (rule.getEffects() != null && !rule.getEffects().isEmpty()) {
            for (TransitionRule.EffectRule effect : rule.getEffects()) {
                if (effect == null || !StringUtils.hasText(effect.getVar())) {
                    continue;
                }
                Object value = effect.getValue();
                if (StringUtils.hasText(effect.getValueExpression())) {
                    try {
                        value = ruleEvaluator.evaluateValue(
                            effect.getValueExpression(), ctx, Object.class);
                    } catch (Exception e) {
                        log.warn("effect valueExpression 求值失败: var={}, expr={}",
                            effect.getVar(), effect.getValueExpression(), e);
                        continue;
                    }
                }
                updates.put(effect.getVar(), value);
            }
        }

        if (updates.isEmpty()) {
            log.debug("transition 无 effects: rule={}", rule.getDisplayName());
            return;
        }

        long version = ctx.getVersion();
        boolean ok = contextManager.batchSetVarsWithVersion(
            instanceId, updates, "STATE_MACHINE", version);

        if (!ok) {
            CaseContext latest = contextManager.getSnapshot(instanceId);
            if (latest != null) {
                ok = contextManager.batchSetVarsWithVersion(
                    instanceId, updates, "STATE_MACHINE", latest.getVersion());
            }
        }

        if (ok) {
            log.info("✅ Transition effects 已应用: session={}, rule={}, updates={}",
                instanceId, rule.getDisplayName(), updates.keySet());
        } else {
            log.warn("⚠️ Transition effects 应用失败（重试后仍失败）: session={}, rule={}, updates={}",
                instanceId, rule.getDisplayName(), updates.keySet());
        }
    }

    /**
     * ★ 推送生理基线更新
     */
    private void pushPhysiologyUpdate(String instanceId, ResolvedCaseTypeConfig config, String stateId) {
        try {
            String caseId = config.getCode();
            if (caseId == null) {
                log.warn("无法获取 caseId，无法推送生理数据");
                return;
            }
            StatePhysiology physiology = physiologyConfigLoader.getState(caseId, stateId);
            if (physiology == null) {
                log.debug("状态 {} 无生理基线定义，跳过推送", stateId);
                return;
            }

            CaseContext ctx = contextManager.getSnapshot(instanceId);
            if (ctx != null) {
                long currentVersion = ctx.getVersion();
                boolean success = contextManager.updateCurrentPhysiology(instanceId, physiology, currentVersion);
                if (!success) {
                    log.warn("更新上下文生理基线失败（版本冲突），sessionId={}", instanceId);
                }
            } else {
                log.warn("上下文不存在，无法存储生理基线: sessionId={}", instanceId);
            }

            Map<String, Object> physData = new LinkedHashMap<>();
            physData.put("state", stateId);
            physData.put("heartRate", physiology.getHeartRate());
            physData.put("systolicBp", physiology.getSystolicBp());
            physData.put("diastolicBp", physiology.getDiastolicBp());
            physData.put("spo2", physiology.getSpo2());
            physData.put("respiratoryRate", physiology.getRespiratoryRate());
            physData.put("consciousness", physiology.getConsciousness());
            physData.put("transitionDuration", physiology.getTransitionDuration());

            Map<String, Object> payload = Map.of(
                "type", "physiology_update",
                "data", physData,
                "timestamp", System.currentTimeMillis()
            );
            messageSender.send(instanceId, payload);
            log.debug("📤 生理基线已推送并存储上下文: sessionId={}, state={}", instanceId, stateId);
        } catch (Exception e) {
            log.warn("推送生理基线失败: {}", e.getMessage());
        }
    }

    /**
     * 推送状态变更
     */
    public void pushStateChange(String sessionId, String from, String to, String event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from);
        data.put("to", to);
        data.put("event", event);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "state_change");
        payload.put("data", data);
        payload.put("timestamp", System.currentTimeMillis());

        messageSender.send(sessionId, payload);
        log.debug("📤 状态变更已推送: sessionId={}, {} -> {}", sessionId, from, to);
    }

    /**
     * 处理默认转移（兼容 allowUnlistedTransitions）
     */
    @SuppressWarnings({"deprecation", "unchecked"})
    private String handleDefaultTransition(ResolvedCaseTypeConfig config,
                                           String intentCode,
                                           StateMachine<String, String> sm,
                                           String instanceId) {
        log.debug("进入默认转移处理: allowUnlistedTransitions={}", config.isAllowUnlistedTransitions());
        if (config.isAllowUnlistedTransitions()) {
            String target = config.getDefaultTargetState(intentCode);
            log.debug("默认目标状态查找结果: intent={} -> target={}", intentCode, target);
            if (target != null) {
                final String fromState = sm.getState().getId();
                try {
                    final String finalTarget = target;
                    sm.getStateMachineAccessor().doWithAllRegions(access -> {
                        access.resetStateMachine(
                            new DefaultStateMachineContext<String, String>(
                                finalTarget,
                                null,
                                null,
                                null
                            )
                        );
                    });
                    persistState(sm, instanceId);
                    recordTransitionHistory(instanceId, fromState, target, intentCode, false);
                    log.info("默认转移成功: {} -> {} (intent={})", fromState, target, intentCode);
                    return target;
                } catch (Exception e) {
                    log.warn("默认转移失败: {} -> {} (intent={})",
                        fromState, target, intentCode, e);
                }
            } else {
                log.warn("默认转移未配置目标状态 (intent={})", intentCode);
            }
        } else {
            log.warn("未允许未列出的转移 (allowUnlistedTransitions=false)");
        }
        return null;
    }

    /**
     * 记录转移历史到 Redis List
     */
    private void recordTransitionHistory(String sessionId, String fromState, String toState, String intentCode, boolean deviation) {
        try {
            String key = SESSION_CONTEXT_PREFIX + sessionId + ":transitions";
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("from", fromState);
            record.put("to", toState);
            record.put("intent", intentCode);
            record.put("deviation", deviation);
            record.put("timestamp", System.currentTimeMillis());

            redisTemplate.opsForList().rightPush(key, record);
            redisTemplate.expire(key, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
            log.debug("转移历史已记录: {} -> {} (intent={})", fromState, toState, intentCode);
        } catch (Exception e) {
            log.warn("记录转移历史失败: {}", e.getMessage());
        }
    }

    /**
     * ★ 第六步新增：tutor 决策审计落 Redis。
     *
     * <p>key: ems:session:context:{sessionId}:tutor_decisions</p>
     *
     * <p>记录 BLOCK / ALLOW_WITH_WARNING 两类决策，供后续分析规则命中率与灰度过期评估。</p>
     */
    private void recordTutorDecisionAudit(String instanceId,
                                          String decisionType,
                                          String intentCode,
                                          CaseContext ctx,
                                          ValidationResult vr) {
        try {
            String key = SESSION_CONTEXT_PREFIX + instanceId + ":tutor_decisions";
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("type", decisionType);
            record.put("state", ctx != null ? ctx.getCurrentState() : null);
            record.put("intent", intentCode);
            record.put("ruleId", vr.getPrimaryRuleId());
            record.put("severity", vr.getSeverity());
            record.put("feedbackKeys", vr.getFeedbackKeys());
            record.put("revision", ctx != null ? ctx.getVersion() : 0L);
            record.put("timestamp", System.currentTimeMillis());

            redisTemplate.opsForList().rightPush(key, record);
            redisTemplate.expire(key, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[Tutor][{}] 审计写入失败: {}", decisionType, e.getMessage());
        }
    }

    /**
     * 审计日志
     */
    private void logAuditTrail(String instanceId, String currentState,
                               List<TransitionRule> matched, TransitionRule selected,
                               Map<String, Object> snapshot) {
        List<String> candidateIds = matched.stream()
            .map(r -> {
                String id = r.getIntentCode() != null ? r.getIntentCode() : r.getEvent();
                int prio = r.getPriority() != null ? r.getPriority() : 0;
                return id + "(prio:" + prio + ")" + (r.isDeviation() ? "[DEV]" : "");
            })
            .collect(Collectors.toList());

        Map<String, Object> guardValues = new HashMap<>();
        if (selected.hasCondition()) {
            Set<String> varNames = extractVariableNames(selected.getCondition());
            for (String varName : varNames) {
                if (snapshot.containsKey(varName)) {
                    guardValues.put(varName, snapshot.get(varName));
                }
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("ts", Instant.now().toString());
        audit.put("session", instanceId);
        audit.put("state", currentState);
        audit.put("candidates", candidateIds);
        audit.put("selected", selected.getIntentCode() != null ? selected.getIntentCode() : selected.getEvent());
        audit.put("selected_priority", selected.getPriority());
        audit.put("selected_target", selected.getTarget());
        audit.put("guard_values", guardValues);
        audit.put("deviation", selected.isDeviation());

        try {
            log.info("AUDIT|{}", cn.hutool.json.JSONUtil.toJsonStr(audit));
        } catch (Exception e) {
            log.warn("审计日志序列化失败: {}", audit);
        }
    }

    /**
     * 从 SpEL 表达式中提取 #变量名
     */
    private Set<String> extractVariableNames(String expression) {
        Set<String> names = new HashSet<>();
        if (expression == null) return names;
        Pattern pattern = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*)(\\.[a-zA-Z_][a-zA-Z0-9_]*)?");
        Matcher matcher = pattern.matcher(expression);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * @deprecated 请使用 {@link #selectAndTransition(String, ResolvedCaseTypeConfig, String, CaseContext)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public String transition(String instanceId, ResolvedCaseTypeConfig config, String eventCode) {
        CaseContext ctx = contextManager.getSnapshot(instanceId);
        if (ctx == null) {
            log.warn("transition 降级: 上下文不存在，回退到 Spring SM 原生 sendEvent");
            StateMachine<String, String> sm = getOrCreateMachine(instanceId, config);
            String fromState = sm.getState().getId();
            boolean accepted = sm.sendEvent(eventCode);
            if (!accepted) {
                log.warn("状态机拒绝事件: {}, 当前状态: {}", eventCode, fromState);
                return null;
            }
            String newState = sm.getState().getId();
            persistState(sm, instanceId);
            recordTransitionHistory(instanceId, fromState, newState, eventCode, false);
            return newState;
        }
        return selectAndTransition(instanceId, config, eventCode, ctx);
    }

    /**
     * 构建状态机 Builder（直接委托给 builderFactory）
     */
    @SuppressWarnings("unused")
    private StateMachineBuilder.Builder<String, String> buildStateMachineBuilder(ResolvedCaseTypeConfig config) {
        return builderFactory.getOrCreateBuilder(config);
    }

    private void restoreState(StateMachine<String, String> sm, String instanceId) {
        try {
            persister.restore(sm, instanceId);
        } catch (Exception e) {
            log.debug("状态恢复失败（首次启动正常）: {}", e.getMessage());
        }
    }

    private void persistState(StateMachine<String, String> sm, String instanceId) {
        try {
            persister.persist(sm, instanceId);
        } catch (Exception e) {
            log.warn("状态持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 清除缓存（状态机实例重建）
     */
    public void clearCache(String instanceId) {
        machineCache.remove(instanceId);
        configCache.remove(instanceId);
    }

    // =========================================================
    // ★ SPI 调用封装
    // =========================================================

    /**
     * SPI-1：临床规则校验。
     *
     * <p>本方法返回 {@link ValidationResult}，由调用方（selectAndTransition）决定是否阻断。
     * tutor 未部署时（No-Op）或调用异常时返回 null，调用方按 ALLOW 处理。</p>
     */
    private ValidationResult fireClinicalRuleValidate(String instanceId,
                                                      ResolvedCaseTypeConfig config,
                                                      String intentCode,
                                                      CaseContext ctx) {
        if (ctx == null || intentCode == null || intentCode.isBlank()) {
            return null;
        }
        try {
            ClinicalRuleService svc = clinicalRuleServiceProvider.getIfAvailable();
            if (svc == null) {
                return null;
            }
            ClinicalRuleContext spiCtx = new ClinicalRuleContext();
            spiCtx.setSessionId(instanceId);
            spiCtx.setCaseType(config != null ? config.getCode() : null);
            spiCtx.setStateRevision(ctx.getVersion());
            spiCtx.setIntentCode(intentCode);
            spiCtx.setCurrentState(ctx.getCurrentState());
            if (ctx.getVariables() != null) {
                spiCtx.setVariables(new HashMap<>(ctx.getVariables()));
            }

            ValidationResult vr = svc.validate(spiCtx);
            if (log.isDebugEnabled() && vr != null) {
                log.debug("[SPI][ClinicalRule] session={}, case={}, intent={}, decision={}, severity={}",
                    instanceId,
                    config != null ? config.getCode() : null,
                    intentCode,
                    vr.getDecision(),
                    vr.getSeverity());
            }
            return vr;
        } catch (Exception e) {
            log.warn("[SPI][ClinicalRule] 调用失败（不影响状态机）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * ★ 步骤3新增：SPI-4 迁移提交后回调 tutor。
     *
     * <p>由 {@link AttemptEventSink#onTransitionCommitted} 承载。
     * tutor 侧可据此推进 eventSequence、评估 ScenarioBeat、写评分证据。</p>
     *
     * <p>try/catch 兜底：任何异常只 warn，不影响状态机。</p>
     */
    private void fireAttemptEventCommitted(String instanceId,
                                           ResolvedCaseTypeConfig config,
                                           String fromState,
                                           String toState,
                                           String intentCode,
                                           CaseContext ctx,
                                           ValidationResult vr) {
        try {
            AttemptEventSink sink = attemptEventSinkProvider.getIfAvailable();
            if (sink == null) {
                return;
            }

            AttemptEventContext actx = new AttemptEventContext();
            actx.setSessionId(instanceId);
            actx.setCaseType(config != null ? config.getCode() : null);
            actx.setFromState(fromState);
            actx.setToState(toState);
            actx.setIntentCode(intentCode);
            actx.setStateRevision(ctx != null ? ctx.getVersion() : null);
            if (vr != null) {
                actx.setPrimaryRuleId(vr.getPrimaryRuleId());
                actx.setAllowedEffects(vr.getAllowedEffects());
            }
            if (ctx != null && ctx.getVariables() != null) {
                actx.setVariables(new HashMap<>(ctx.getVariables()));
            }

            sink.onTransitionCommitted(actx);

            if (log.isDebugEnabled()) {
                log.debug("[SPI][AttemptEventSink] {} -> {} (intent={})",
                    fromState, toState, intentCode);
            }
        } catch (Exception e) {
            log.warn("[SPI][AttemptEventSink] 调用失败（不影响状态机）: {}", e.getMessage());
        }
    }

    /**
     * SPI-2：转移前钩子。
     */
    private void fireBeforeTransition(String instanceId,
                                      ResolvedCaseTypeConfig config,
                                      String fromState,
                                      String toState,
                                      String intentCode,
                                      CaseContext ctx) {
        try {
            StateTransitionHook hook = stateTransitionHookProvider.getIfAvailable();
            if (hook == null) {
                return;
            }
            TransitionContext tc = new TransitionContext();
            tc.setSessionId(instanceId);
            tc.setCaseType(config != null ? config.getCode() : null);
            tc.setFromState(fromState);
            tc.setToState(toState);
            tc.setIntentCode(intentCode);
            tc.setStateRevisionBefore(ctx != null ? ctx.getVersion() : 0L);
            if (ctx != null && ctx.getVariables() != null) {
                tc.setVariables(new HashMap<>(ctx.getVariables()));
            }
            hook.beforeTransition(tc);
            if (log.isDebugEnabled()) {
                log.debug("[SPI][BeforeTransition] {} -> {} (intent={})", fromState, toState, intentCode);
            }
        } catch (Exception e) {
            log.warn("[SPI][BeforeTransition] 调用失败（不影响状态机）: {}", e.getMessage());
        }
    }

    /**
     * SPI-3：转移后钩子 + 时间线事件。
     */
    private void fireAfterTransition(String instanceId,
                                     ResolvedCaseTypeConfig config,
                                     String fromState,
                                     String toState,
                                     String intentCode,
                                     CaseContext ctx) {
        // SPI-3a: afterTransition
        try {
            StateTransitionHook hook = stateTransitionHookProvider.getIfAvailable();
            if (hook != null) {
                TransitionContext tc = new TransitionContext();
                tc.setSessionId(instanceId);
                tc.setCaseType(config != null ? config.getCode() : null);
                tc.setFromState(fromState);
                tc.setToState(toState);
                tc.setIntentCode(intentCode);
                tc.setStateRevisionAfter(ctx != null ? ctx.getVersion() : 0L);
                hook.afterTransition(tc);
            }
        } catch (Exception e) {
            log.warn("[SPI][AfterTransition] 调用失败（不影响状态机）: {}", e.getMessage());
        }

        // SPI-3b: onStateExited / onStateEntered
        try {
            TemporalEventSink sink = temporalEventSinkProvider.getIfAvailable();
            if (sink != null) {
                long rev = ctx != null ? ctx.getVersion() : 0L;
                if (fromState != null && !fromState.equals(toState)) {
                    sink.onStateExited(instanceId, fromState, rev);
                }
                sink.onStateEntered(instanceId, toState, rev);
            }
            if (log.isDebugEnabled()) {
                log.debug("[SPI][TemporalEvent] exit={}, enter={}, intent={}",
                    fromState, toState, intentCode);
            }
        } catch (Exception e) {
            log.warn("[SPI][TemporalEvent] 调用失败（不影响状态机）: {}", e.getMessage());
        }
    }

    /**
     * 变量驱动的状态重评估。
     *
     * <p>仅在 {@code intentCode == null} 路径下被调用。
     * 由于 fireClinicalRuleValidate 在 intentCode 为空时直接返回 null，
     * 本路径不受 tutor BLOCK 影响，行为与改动前一致。</p>
     */
    /**
     * 变量驱动的状态机重评估。
     *
     * <p>★ 2026-09-23 修改：返回类型从 {@code String} 改为 {@link StateTransitionResult}，
     * 让调用方能区分"成功转移" / "未转移" / "Tutor 拦截" 三种情况。
     * 修复 {@code SkillCompletionStateDriver} 因无法判断转移结果而导致的 Effect 泄漏。</p>
     *
     * @param instanceId 会话 ID
     * @param changedVar 触发重评估的变量名（仅用于日志）
     * @return 转移结果；config 缓存未命中时返回 noChange
     */
    public StateTransitionResult reEvaluateOnContextChange(String instanceId, String changedVar) {
        ResolvedCaseTypeConfig config = configCache.get(instanceId);
        if (config == null) {
            log.warn("[变量驱动] config 缓存未命中，跳过: session={}, var={}", instanceId, changedVar);
            return StateTransitionResult.noChange(null, "config 缓存未命中");
        }
        synchronized (("sm-lock:" + instanceId).intern()) {
            StateMachine<String, String> sm = getOrCreateMachine(instanceId, config);
            String currentState = sm.getState().getId();

            CaseContext ctx = contextManager.getSnapshot(instanceId);
            if (ctx == null) {
                log.warn("[变量驱动] 上下文不存在，跳过: session={}", instanceId);
                return StateTransitionResult.noChange(currentState, "上下文不存在");
            }

            List<TransitionRule> candidates = config.getTransitionsBySource(currentState);
            if (candidates == null || candidates.isEmpty()) {
                log.debug("[变量驱动] 状态 {} 无出边，跳过", currentState);
                return StateTransitionResult.noChange(currentState, "无出边");
            }

            boolean hasConditionOnly = candidates.stream()
                .filter(TransitionRule::isEnabled)
                .anyMatch(r -> !StringUtils.hasText(r.getIntentCode())
                    && !StringUtils.hasText(r.getEvent())
                    && r.hasCondition());

            if (!hasConditionOnly) {
                log.debug("[变量驱动] 状态 {} 无纯条件出边，跳过 (var={})", currentState, changedVar);
                return StateTransitionResult.noChange(currentState, "无纯条件出边");
            }

            log.info("🔁 [变量驱动] 变量 {} 变更，尝试重评估状态 {} 的条件出边", changedVar, currentState);

            // ★ 捕获 Tutor 拦截异常，转为 blocked 结果
            String resultState;
            try {
                resultState = selectAndTransition(instanceId, config, null, ctx);
            } catch (TutorBlockedException e) {
                log.info("⛔ [变量驱动] Tutor 拦截状态转移: session={}, from={}, reason={}",
                    instanceId, currentState, e.getMessage());
                return StateTransitionResult.blocked(currentState, "Tutor 拦截: " + e.getMessage());
            } catch (Exception e) {
                log.warn("[变量驱动] 状态转移异常: session={}, from={}, err={}",
                    instanceId, currentState, e.getMessage());
                return StateTransitionResult.noChange(currentState, "转移异常: " + e.getMessage());
            }

            // ★ selectAndTransition 返回 String 时，用"状态是否变化"判定是否成功
            if (resultState == null) {
                return StateTransitionResult.noChange(currentState, "selectAndTransition 返回 null");
            }
            if (resultState.equals(currentState)) {
                log.info("⛔ 状态机无匹配转移: session={}, state={}", instanceId, currentState);
                return StateTransitionResult.noChange(currentState, "无匹配转移");
            }
            return StateTransitionResult.success(currentState, resultState);
        }
    }

    /**
     * 强制状态跳转（异常层专用）。无 reason 版本，默认 reason="FORCE_TRANSITION"。
     */
    public boolean forceTransition(String sessionId, String targetState) {
        return forceTransition(sessionId, targetState, "FORCE_TRANSITION");
    }

    /**
     * 强制状态跳转（异常层专用，带原因）。
     *
     * <p>绕过所有 guard，直接把状态机推到目标状态。不走 selectAndTransition，
     * 因此也不触发 tutor 的 BLOCK 判断（异常层优先级最高）。</p>
     */
    @SuppressWarnings("deprecation")
    public boolean forceTransition(String sessionId, String targetState, String reason) {
        if (sessionId == null || targetState == null) {
            log.warn("forceTransition: sessionId 或 targetState 为空");
            return false;
        }
        if (reason == null || reason.isBlank()) {
            reason = "FORCE_TRANSITION";
        }

        StateMachine<String, String> sm = machineCache.get(sessionId);
        if (sm == null) {
            log.warn("forceTransition: 会话状态机不存在: sessionId={}", sessionId);
            return false;
        }

        ResolvedCaseTypeConfig config = configCache.get(sessionId);
        if (config == null) {
            log.warn("forceTransition: config 缓存未命中: sessionId={}", sessionId);
            return false;
        }

        synchronized (("sm-lock:" + sessionId).intern()) {

            String previous = sm.getState() != null ? sm.getState().getId() : null;
            if (targetState.equals(previous)) {
                log.debug("forceTransition: 已在目标状态，跳过: session={}, state={}",
                    sessionId, targetState);
                return true;
            }

            List<String> allStates = config.getStates();
            if (allStates != null && !allStates.isEmpty()
                && !allStates.contains(targetState)) {
                log.warn("forceTransition: targetState 不在配置状态列表中（继续执行）: "
                        + "session={}, targetState={}, reason={}",
                    sessionId, targetState, reason);
            }

            try {
                final String target = targetState;
                sm.getStateMachineAccessor().doWithAllRegions(access -> {
                    access.resetStateMachine(
                        new DefaultStateMachineContext<String, String>(
                            target,
                            null,
                            null,
                            null
                        )
                    );
                });

                persistState(sm, sessionId);
                recordTransitionHistory(sessionId, previous, targetState, reason, false);

                log.info("⚡ forceTransition: session={}, {} → {} (reason={})",
                    sessionId, previous, targetState, reason);
                return true;

            } catch (Exception e) {
                log.error("forceTransition 失败: session={}, {} → {} (reason={})",
                    sessionId, previous, targetState, reason, e);
                return false;
            }
        }
    }
}

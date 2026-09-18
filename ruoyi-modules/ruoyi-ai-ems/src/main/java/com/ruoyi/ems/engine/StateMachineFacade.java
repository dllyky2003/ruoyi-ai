package com.ruoyi.ems.engine;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.PhysiologyConfigService;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.StatePhysiology;
import com.ruoyi.ems.rule.IRuleEvaluator;
import com.ruoyi.ems.websocket.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
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
    private final PhysiologyConfigService physiologyConfigService;

    private static final String SESSION_CONTEXT_PREFIX = "ems:session:context:";
    private static final long SESSION_TIMEOUT_HOURS = 24;

    public StateMachineFacade(StateMachineBuilderFactory builderFactory,
                              RedisStatePersister persister,
                              EmsContextManager contextManager,
                              IRuleEvaluator ruleEvaluator,
                              RedisTemplate<String, Object> redisTemplate,
                              WebSocketMessageSender messageSender,
                              PhysiologyConfigService physiologyConfigService) {
        this.builderFactory = builderFactory;
        this.persister = persister;
        this.contextManager = contextManager;
        this.ruleEvaluator = ruleEvaluator;
        this.redisTemplate = redisTemplate;
        this.messageSender = messageSender;
        this.physiologyConfigService = physiologyConfigService;
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
            return physiologyConfigService.getState(caseId, currentState);
        } catch (IllegalArgumentException e) {
            log.warn("当前状态 {} 在生理配置中未定义: {}", currentState, e.getMessage());
            return null;
        }
    }

    /**
     * ★ 核心方法：业务层主导的状态转移（带冲突消解、偏差标记、审计日志）
     *
     * <p>匹配策略（v2，修复 fall-through 吃掉具体意图）：
     * <ul>
     *   <li>{@code intentCode} 非空（学员意图）：只匹配显式声明了 intentCode/event 的规则；
     *       无 intentCode/event 的条件路由规则<b>不参与</b>。</li>
     *   <li>{@code intentCode} 为空（系统触发 / 变量驱动）：只匹配无 intentCode/event 的纯条件规则。</li>
     * </ul>
     *
     * <p>无匹配处理：
     * <ul>
     *   <li><b>有意图入口但 Guard 未通过</b> → 直接返回 null，不兜底默认转移（防止越权跳转）。</li>
     *   <li><b>完全无意图入口</b>（例如当前状态只有 condition-only 转移）→ 走
     *       handleDefaultTransition（allowUnlistedTransitions）。这是恢复旧行为的关键分支：
     *       {@code F-000-BLS_PRIMARY_SURVEY → F-000-INITIAL_ASSESSMENT} 等仅靠
     *       condition 驱动的转移，学员意图到达时没有 intentCode 入口，必须靠兜底推进。</li>
     * </ul>
     */
    @SuppressWarnings({"deprecation", "unchecked"})
    public String selectAndTransition(String instanceId,
                                      ResolvedCaseTypeConfig config,
                                      String intentCode,
                                      CaseContext ctx) {

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
                // 学员意图：只匹配显式声明 intentCode/event 的规则
                if (StringUtils.hasText(rule.getIntentCode())) {
                    intentMatches = rule.getIntentCode().equals(intentCode);
                } else if (StringUtils.hasText(rule.getEvent())) {
                    intentMatches = rule.getEvent().equals(intentCode);
                } else {
                    // ★ 关键修复：无 intentCode/event 的条件路由，不参与学员意图匹配
                    intentMatches = false;
                }
            } else {
                // 系统触发 / 变量驱动：只匹配无 intentCode/event 的纯条件规则
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
            // 4.1 统计意图匹配数（与主循环口径一致，避免日志误导）
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

            // 4.2 ★ 修正：区分"有意图入口但 Guard 失败"与"完全无意图入口"
            //  - 有意图入口 → 拒绝（P0-1 修复本意：防止 fall-through 越权跳转）
            //  - 完全无入口 → 走兜底（恢复旧行为，例如 condition-only 转移的场景）
            boolean hasIntentEntry = intentMatchedCount > 0;

            if (studentIntent && hasIntentEntry) {
                log.info("学员意图 {} 在状态 {} 有入口但 Guard 未通过，拒绝执行（不走默认兜底）",
                    intentCode, currentState);
                return null;
            }

            // 4.3 完全无入口，或系统触发无匹配 → 走默认转移（allowUnlistedTransitions）
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

        // 12. onEnterRules 已废弃：派生变量由 DerivationService 自动重算，无需在状态进入时手动执行。
        //     如果未来有"状态进入副作用"，应通过 transition 的 effects 声明。

        // 13. 推送新状态的生理基线
        pushPhysiologyUpdate(instanceId, config, targetState);

        return targetState;
    }

    /**
     * ★ 应用 transition 副作用（替代 SpEL Action）。
     *
     * <p>处理两类：
     * <ul>
     *   <li>{@code exitReason} → 自动写入 {@code process.returnToParent}</li>
     *   <li>{@code effects} 列表 → 逐个变量写入（支持字面量和 valueExpression）</li>
     * </ul>
     *
     * <p>走 {@code batchSetVarsWithVersion}，writer="STATE_MACHINE"，不经过 SpEL 写入路径。
     */
    private void applyTransitionEffects(String instanceId, TransitionRule rule, CaseContext ctx) {
        if (rule == null) return;

        Map<String, Object> updates = new LinkedHashMap<>();

        // 1. exitReason → process.returnToParent
        if (StringUtils.hasText(rule.getExitReason())) {
            updates.put("process.returnToParent", rule.getExitReason());
        }

        // 2. effects 列表
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
            // 版本冲突重试一次
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
     * ★ 对比执行前后变量差异，通过语义化入口批量写入。
     *
     * @deprecated 保留供兼容，新代码不应依赖。transition effects 走 applyTransitionEffects。
     */
    @Deprecated
    private void persistCtxChanges(String instanceId,
                                   Map<String, Object> beforeVars,
                                   CaseContext ctx) {
        if (ctx == null) return;

        Map<String, Object> afterVars = ctx.getVariables();
        Map<String, Object> changes = new LinkedHashMap<>();

        for (Map.Entry<String, Object> e : afterVars.entrySet()) {
            Object before = beforeVars.get(e.getKey());
            if (!Objects.equals(before, e.getValue())) {
                changes.put(e.getKey(), e.getValue());
            }
        }
        for (String k : beforeVars.keySet()) {
            if (!afterVars.containsKey(k)) {
                changes.put(k, null);
            }
        }

        if (changes.isEmpty()) {
            log.trace("上下文无实际变更: session={}", instanceId);
            return;
        }

        long version = ctx.getVersion();
        boolean ok = contextManager.batchSetVarsWithVersion(
            instanceId, changes, "SYSTEM", version);

        if (!ok) {
            CaseContext latest = contextManager.getSnapshot(instanceId);
            if (latest != null) {
                ok = contextManager.batchSetVarsWithVersion(
                    instanceId, changes, "SYSTEM", latest.getVersion());
            }
        }

        if (ok) {
            log.debug("上下文变更已写入: session={}, changes={}",
                instanceId, changes.keySet());
        } else {
            log.warn("上下文变更写入失败（重试后仍失败）: session={}, changes={}",
                instanceId, changes.keySet());
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
            StatePhysiology physiology = physiologyConfigService.getState(caseId, stateId);
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
        data.put("event", event);   // 允许 null

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "state_change");
        payload.put("data", data);
        payload.put("timestamp", System.currentTimeMillis());

        messageSender.send(sessionId, payload);
        log.debug("📤 状态变更已推送: sessionId={}, {} -> {}", sessionId, from, to);
    }

    /**
     * 处理默认转移（兼容 allowUnlistedTransitions）
     *
     * <p>★ 修复 P2-4：先保存 fromState，再 resetStateMachine，避免 from 被记成 target。
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
                // ★ 先记录 from，再 reset
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
            // ★ 先记录 from
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

    /**
     * 变量驱动的状态重评估。
     *
     * <p>仅在 {@code intentCode == null} 路径下被调用（例如 EmsContextManager 变量变更事件监听器）。
     * 由于 selectAndTransition 的 null 分支只匹配纯条件规则，不会与学员意图冲突。
     */
    public String reEvaluateOnContextChange(String instanceId, String changedVar) {
        ResolvedCaseTypeConfig config = configCache.get(instanceId);
        if (config == null) {
            log.warn("[变量驱动] config 缓存未命中，跳过: session={}, var={}", instanceId, changedVar);
            return null;
        }
        synchronized (("sm-lock:" + instanceId).intern()) {
            StateMachine<String, String> sm = getOrCreateMachine(instanceId, config);
            String currentState = sm.getState().getId();

            CaseContext ctx = contextManager.getSnapshot(instanceId);
            if (ctx == null) {
                log.warn("[变量驱动] 上下文不存在，跳过: session={}", instanceId);
                return currentState;
            }

            List<TransitionRule> candidates = config.getTransitionsBySource(currentState);
            if (candidates == null || candidates.isEmpty()) {
                log.debug("[变量驱动] 状态 {} 无出边，跳过", currentState);
                return currentState;
            }

            boolean hasConditionOnly = candidates.stream()
                .filter(TransitionRule::isEnabled)
                .anyMatch(r -> !StringUtils.hasText(r.getIntentCode())
                    && !StringUtils.hasText(r.getEvent())
                    && r.hasCondition());

            if (!hasConditionOnly) {
                log.debug("[变量驱动] 状态 {} 无纯条件出边，跳过 (var={})", currentState, changedVar);
                return currentState;
            }

            log.info("🔁 [变量驱动] 变量 {} 变更，尝试重评估状态 {} 的条件出边", changedVar, currentState);

            return selectAndTransition(instanceId, config, null, ctx);
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
     * <p>绕过所有 guard，直接把状态机推到目标状态。不走 selectAndTransition。
     *
     * <p>与 selectAndTransition 的区别：
     * <ul>
     *   <li>selectAndTransition：按 intent 找 event，走正常转移，guard 生效</li>
     *   <li>forceTransition：按 stateId 直接 reset，guard 不生效</li>
     * </ul>
     *
     * <p>调用时机：AclsCommandEngine 第 7.5 步，异常命中后。
     * 调用方负责：写 effects 到 EmsContextManager、发 state_change(stateName) 给 Unity。
     * 本方法只做状态机内部转换 + 持久化 + 历史记录，<b>不发 WebSocket 消息</b>。
     *
     * @param sessionId   会话 id
     * @param targetState 目标状态 id（异常规则的 jumpTo）
     * @param reason      触发原因（异常规则 id，或 "FORCE_TRANSITION"）
     * @return true=跳转成功（含已在目标状态）；false=失败
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

        // 与 reEvaluateOnContextChange 保持同一把锁，避免与正常转移并发
        synchronized (("sm-lock:" + sessionId).intern()) {

            String previous = sm.getState() != null ? sm.getState().getId() : null;
            if (targetState.equals(previous)) {
                log.debug("forceTransition: 已在目标状态，跳过: session={}, state={}",
                    sessionId, targetState);
                return true;
            }

            // 状态列表校验：仅 warn，不阻断
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
                            null,   // 无 event
                            null,   // 无 eventHeaders
                            null    // 保留 ExtendedState（与 selectAndTransition 一致）
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

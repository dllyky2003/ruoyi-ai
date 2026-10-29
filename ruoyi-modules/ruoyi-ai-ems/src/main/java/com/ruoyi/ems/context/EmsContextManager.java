package com.ruoyi.ems.context;

import com.ruoyi.ems.config.loader.VariableMetaLoader;
import com.ruoyi.ems.domain.*;

import com.ruoyi.ems.exception.VariableAccessDeniedException;
import com.ruoyi.ems.exception.VariableValidationException;
import com.ruoyi.ems.service.DerivationService;
import com.ruoyi.ems.websocket.WebSocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文管理器。
 *
 * <p>★ 2026-09-22 改造点（P0-B 会话初始态隔离）：</p>
 * <ul>
 *   <li>新增 {@link #FALLBACK_INITIAL_STATE} 兜底初始状态；</li>
 *   <li>{@link #getOrCreate(String)} 新会话创建时显式写入 currentState，
 *       避免 CaseContext.currentState 为 null 或继承上一局脏值；</li>
 *   <li>新增 {@link #seedInitialStateIfAbsent(String, String)}，
 *       供 SessionService 在案例配置加载后用真实初始状态覆盖 fallback 值；</li>
 *   <li>{@link #applyResetDefaults(String, String, String)} 在写入案例变量之后，
 *       再次确保 currentState 非空（防止 resetDefaults 里意外清空它）。</li>
 * </ul>
 *
 * <p><b>背景</b>：改造前，新会话的 CaseContext.currentState 在创建时为 null，
 * 后续第一次向 Unity 推送 currentState 时可能带上上一局的脏值
 * （日志中观测到 F_101_UNSTABLE_CARDIOVERSION 被推给新局的 Unity），
 * 导致 Unity 侧状态显示错误。</p>
 *
 * <p>★ 2026-09-25 改造点（派生刷新不 bump stateRevision）：</p>
 * <ul>
 *   <li>{@link #doUpdateWithVersion} 对 {@code caller="SYSTEM_DERIVED"} 不再 bump version；</li>
 *   <li>{@link #doRefreshDerivedLocked} 派生变量变化通过 {@code batch_variable_update} 单独广播。</li>
 * </ul>
 */
@Component
@Slf4j
public class EmsContextManager {

    /**
     * ★ 2026-09-22 新增：兜底初始状态。
     *
     * <p>语义：当外部未通过 {@link #seedInitialStateIfAbsent(String, String)}
     * 显式指定初始状态时，新会话默认落到这个状态，与 GLOBAL/_flow.yaml 的入口一致。</p>
     *
     * <p>注意：这是保守兜底值。真实场景下 SessionService 应该在案例配置加载后
     * 用该案例真实的初始状态覆盖它。</p>
     */
    public static final String FALLBACK_INITIAL_STATE = "F-000-SCENE_START";

    /**
     * 案例类型 → 变量默认值表。懒加载，第一次访问某 caseType 时构建。
     * reload 时需要清空。
     */
    private final Map<String, Map<String, Object>> defaultCache = new ConcurrentHashMap<>();
    private final Map<String, CaseContext> cache = new ConcurrentHashMap<>();

    /**
     * ★ 派生计算重入深度保护。
     *
     * 用于检测并截断：
     *   EmsContextManager → DerivationService → SpELExpressionEvaluator
     *     → EmsContextManager → DerivationService → ...
     * 的运行期递归调用。
     *
     * 允许有限层嵌套（派生链可能合法地触发多轮变量更新），超过上限直接截断，
     * 防止 StackOverflowError。
     */
    private static final ThreadLocal<Integer> DERIVATION_DEPTH =
        ThreadLocal.withInitial(() -> 0);
    private static final int MAX_DERIVATION_DEPTH = 5;

    /**
     * ★ 栈深度探针阈值。当线程栈深度超过该值时打印告警，帮助定位深层递归。
     * 仅用于诊断，不影响业务行为。
     */
    private static final int STACK_DEPTH_WARN_THRESHOLD = 200;

    @Autowired
    private VariableMetaLoader metaManager;

    @Autowired
    private WebSocketMessageSender messageSender;

    /**
     * ★ 加 @Lazy 打破 Spring 启动期的 Bean 循环依赖：
     * EmsContextManager → DerivationService → SpELExpressionEvaluator → EmsContextManager
     *
     * 注意：@Lazy 只解决 Bean 初始化期依赖，运行期递归仍需 DERIVATION_DEPTH 保护。
     */
    @Autowired
    @Lazy
    private DerivationService derivationService;

    @Autowired
    private com.ruoyi.ems.room.ws.WsSessionRegistry wsRegistry;

    /**
     * 过滤出可广播的变量。返回 null 表示"无可广播内容"。
     *
     * <p>★ 内部变量白名单见 {@link AclsInternalVars}。写入 Context 并触发派生刷新，
     * 但**不下发**给 WebSocket 客户端，也**不进入** case_config 的 initialVitals。</p>
     */
    private Map<String, Object> filterBroadcastVars(Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) {
            return null;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            if (e.getKey() != null && !AclsInternalVars.isInternal(e.getKey())) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return filtered.isEmpty() ? null : filtered;
    }

    // =========================================================
    // 1. 获取或创建上下文
    // =========================================================

    /**
     * 获取或创建上下文。
     *
     * <p>★ 2026-09-22 改造：新创建时显式写入 currentState。</p>
     *
     * <p>原实现中 CaseContext 是无参构造，currentState 为 null。
     * 后续某个路径把 currentState 推给 Unity 时，如果此前没有正确初始化，
     * 就可能把上一局的脏值推给新局。这里在创建瞬间就写入 fallback 初始状态，
     * 使新会话的第一条下行消息永远是合法初始态。</p>
     *
     * <p>真实初始状态由 SessionService 在加载案例配置后通过
     * {@link #seedInitialStateIfAbsent(String, String)} 覆盖。</p>
     */
    public CaseContext getOrCreate(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        return cache.computeIfAbsent(sessionId, id -> {
            CaseContext ctx = new CaseContext();
            ctx.setSessionId(id);
            ctx.setSessionStartTime(LocalDateTime.now());
            ctx.setLastUpdateTime(LocalDateTime.now());
            ctx.setVersion(0L);
            ctx.setModifiedVariables(new LinkedHashSet<>());
            ctx.setContextManager(this);
            // ★ Phase SM：确保 phaseRuntime 非空（无参构造器不会初始化）
            ctx.ensurePhaseRuntime();

            // ★ 2026-09-22 新增：显式初始状态，避免 null / 脏值
            ctx.setCurrentState(FALLBACK_INITIAL_STATE);

            log.info("[EmsContextManager] 新建上下文: sessionId={}, currentState={}",
                id, FALLBACK_INITIAL_STATE);
            return ctx;
        });
    }

    /**
     * ★ 2026-09-22 新增：为新会话设置真实初始状态。
     *
     * <p>调用方：SessionService.createSession。在案例配置加载完成后调用，
     * 用从 _flow.yaml 读到的真实初始状态覆盖 {@link #FALLBACK_INITIAL_STATE}。</p>
     *
     * <p>幂等语义：若当前 currentState 已经不是 fallback 值，说明已经被外部更新过，
     * 此时不再覆盖，只记录 WARN。这样避免覆盖了正常运行中产生的合法状态变更。</p>
     *
     * @param sessionId      会话 ID
     * @param initialState   真实初始状态（来自状态机配置）
     * @return true=已写入或已存在；false=上下文不存在
     */
    public boolean seedInitialStateIfAbsent(String sessionId, String initialState) {
        if (sessionId == null || initialState == null || initialState.isEmpty()) {
            return false;
        }

        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) {
                log.warn("[EmsContextManager] seedInitialStateIfAbsent 失败：上下文不存在, sessionId={}",
                    sessionId);
                return false;
            }

            String existing = current.getCurrentState();

            // 已设置成非 fallback 的值（外部已主动设置），保留现状
            if (existing != null && !FALLBACK_INITIAL_STATE.equals(existing)) {
                if (!existing.equals(initialState)) {
                    log.info("[EmsContextManager] currentState 已为 {}，不覆盖为 {}: sessionId={}",
                        existing, initialState, sessionId);
                }
                return true;
            }

            // existing == null 或 existing == fallback → 覆盖为真实初始值
            CaseContext draft = current.copy();
            draft.setCurrentState(initialState);
            draft.setLastUpdateTime(LocalDateTime.now());
            draft.setVersion(current.getVersion() + 1);
            cache.put(sessionId, draft);

            log.info("[EmsContextManager] 初始状态已设为真实值: sessionId={}, {} -> {}",
                sessionId, existing, initialState);
            return true;
        }
    }

    // =========================================================
    // 2. ★ 获取快照：返回深拷贝副本
    // =========================================================
    public CaseContext getSnapshot(String sessionId) {
        if (sessionId == null) {
            log.warn("尝试获取 null sessionId 的上下文快照");
            return null;
        }
        CaseContext current = cache.get(sessionId);
        if (current == null) {
            return null;
        }
        return current.copy();
    }

    /**
     * 只读快照：不拷贝。调用方必须保证不修改返回对象。
     */
    public CaseContext getReadOnlySnapshot(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return cache.get(sessionId);
    }

    public void clearDefaultCache() {
        defaultCache.clear();
        log.info("默认值缓存已清空");
    }

    // =========================================================
    // 3. 获取变量默认值
    // =========================================================
    public Object getVariableDefault(String caseType, String varName) {
        if (varName == null || caseType == null) {
            return null;
        }
        Map<String, Object> defaults = defaultCache.computeIfAbsent(caseType, ct -> {
            Map<String, Object> m = new HashMap<>();
            Map<String, VariableMeta> allVars = metaManager.getVariablesForCase(ct);
            if (allVars != null) {
                for (String v : allVars.keySet()) {
                    m.put(v, resolveDefault(ct, v));
                }
            }
            return m;
        });
        return defaults.get(varName);
    }

    private Object resolveDefault(String caseType, String varName) {
        Object resetDefault = metaManager.getResetDefault(caseType, varName);
        if (resetDefault != null) return resetDefault;
        VariableMeta meta = metaManager.getMeta(caseType, varName);
        return meta != null ? meta.getDefaultValue() : null;
    }

    // =========================================================
    // 4. ★ 核心写入：乐观锁 + 权限校验 + derived 自动刷新
    // =========================================================
    private boolean updateWithVersion(String sessionId, Map<String, Object> updates,
                                      long expectedVersion, String caller) {
        return doUpdateWithVersion(sessionId, updates, expectedVersion, caller, true);
    }

    /**
     * ★ 核心写入：乐观锁 + 权限校验 + derived 自动刷新。
     *
     * <p><b>修复 A（2026-09-25）</b>：派生刷新（caller="SYSTEM_DERIVED"）不再 bump stateRevision。</p>
     *
     * <p>背景：derived.* 变量（如 derived.timeElapsedSinceLastIntervention、
     * derived.stable 等）会随定时心跳每秒变化。此前每次派生刷新都执行
     * {@code version + 1}，导致：</p>
     * <ul>
     *   <li>stateRevision 每秒 +1，所有外部 CMD 立即 STALE_REVISION</li>
     *   <li>触发 SNAPSHOT 推送风暴（若后续接了推送）</li>
     * </ul>
     *
     * <p>派生变量本身不属于"权威状态变更"，不应占用 stateRevision。
     * 派生变量的变化通过 {@code batch_variable_update} 单独广播
     * （见 {@link #doRefreshDerivedLocked}）。</p>
     *
     * <p><b>其他 caller</b>（STATE_MACHINE / PARAM_EXTRACTOR / PhaseManager /
     * SYSTEM / 手动写入等）仍然正常 bump version。</p>
     */
    private boolean doUpdateWithVersion(String sessionId, Map<String, Object> updates,
                                        long expectedVersion, String caller,
                                        boolean allowDerivation) {
        CaseContext newCtx;
        Set<String> changedKeys;

        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) {
                log.warn("上下文不存在, sessionId={}, caller={}", sessionId, caller);
                return false;
            }
            if (current.getVersion() != expectedVersion) {
                log.warn("版本冲突: 期望 {}, 实际 {}, sessionId={}, caller={}",
                    expectedVersion, current.getVersion(), sessionId, caller);
                return false;
            }

            CaseContext draft = current.copy();
            boolean hasChanged = false;
            Set<String> localChangedKeys = new LinkedHashSet<>();

            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (key == null || key.isEmpty()) {
                    log.warn("[updateWithVersion] 非法 key（null/空）: sessionId={}, caller={}, value={}",
                        sessionId, caller, value);
                    continue;
                }

                if ("caseType".equals(key)) {
                    if (!Objects.equals(current.getCaseType(), value)) {
                        draft.setCaseType((String) value);
                        hasChanged = true;
                        localChangedKeys.add(key);
                    }
                    continue;
                }
                if ("currentState".equals(key)) {
                    if (!Objects.equals(current.getCurrentState(), value)) {
                        draft.setCurrentState((String) value);
                        hasChanged = true;
                        localChangedKeys.add(key);
                    }
                    continue;
                }
                if ("currentPhysiology".equals(key)) {
                    if (!Objects.equals(current.getCurrentPhysiology(), value)) {
                        draft.setCurrentPhysiology((StatePhysiology) value);
                        hasChanged = true;
                        localChangedKeys.add(key);
                    }
                    continue;
                }
                // ★ Phase SM：phaseRuntime 顶层字段
                if ("phaseRuntime".equals(key)) {
                    if (!Objects.equals(current.getPhaseRuntime(), value)) {
                        draft.setPhaseRuntime((PhaseRuntime) value);
                        hasChanged = true;
                        localChangedKeys.add(key);
                    }
                    continue;
                }

                if (key.startsWith("vars.")) {
                    String varName = key.substring("vars.".length());
                    if (varName.isEmpty()) {
                        log.warn("[updateWithVersion] vars. 后变量名为空: sessionId={}, caller={}, key={}",
                            sessionId, caller, key);
                        continue;
                    }
                    if (!isRecognizedVarPrefix(varName)) {
                        log.warn("[updateWithVersion] 变量前缀未识别: sessionId={}, caller={}, var={}, " +
                                "期望前缀: phys. / process. / derived. / __",
                            sessionId, caller, varName);
                    }

                    // ★ 方案 C：嵌套路径合并
                    // 若 vars 里父路径（如 intervention.vagal）是 Map，则将叶子键合并进 Map。
                    // 目的：避免"平铺 key intervention.vagal.count" 与 "Map key intervention.vagal" 并存，
                    //      导致 SpEL 读 #intervention.vagal.count 时读到 Map 中未更新的旧值。
                    // 注意：合并读 draft 而不是 current，保证同一个 batch 内多次合并能累积。
                    if (varName.contains(".")) {
                        int lastDot = varName.lastIndexOf('.');
                        String parentPath = varName.substring(0, lastDot);
                        String leafKey = varName.substring(lastDot + 1);

                        Object parent = draft.getVariables().get(parentPath);
                        if (parent instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> parentMap = (Map<String, Object>) parent;
                            Map<String, Object> merged = new LinkedHashMap<>(parentMap);
                            merged.put(leafKey, value);

                            if (!Objects.equals(parentMap, merged)) {
                                draft.setVar(parentPath, merged, caller);
                                hasChanged = true;
                                localChangedKeys.add(parentPath);
                                log.debug("[updateWithVersion] 嵌套合并: {} <- {}.{}={}",
                                    parentPath, parentPath, leafKey, value);
                            }
                            continue;
                        }
                        // 父路径不是 Map（未初始化或旧格式），退化为平铺写入
                    }

                    Object old = current.getVariables().get(varName);
                    if (!Objects.equals(old, value)) {
                        draft.setVar(varName, value, caller);
                        hasChanged = true;
                        localChangedKeys.add(varName);
                    }
                    continue;
                }

                log.warn("[updateWithVersion] 未识别的更新 key: sessionId={}, caller={}, key={}, value={}",
                    sessionId, caller, key, value);
            }

            if (!hasChanged) {
                log.info("⚠️ 更新无实际变化（版本号不递增）: sessionId={}, caller={}, keys={}",
                    sessionId, caller, updates.keySet());
                return true;
            }

            draft.setLastUpdateTime(LocalDateTime.now());

            // ★ 修复 A（2026-09-25）：派生刷新不 bump stateRevision
            //
            // 派生变量（derived.*）随定时心跳每秒变化，若每次刷新都 bump，
            // 会导致 stateRevision 每秒 +1，所有外部 CMD 立即 STALE_REVISION。
            // 派生变量不属于"权威状态变更"，其变化通过 batch_variable_update
            // 单独广播给 Unity（见 doRefreshDerivedLocked）。
            boolean isSystemDerived = "SYSTEM_DERIVED".equals(caller);
            if (isSystemDerived) {
                log.debug("[updateWithVersion] 派生刷新不 bump version: sessionId={}, " +
                        "version={}, changedKeys={}",
                    sessionId, current.getVersion(), localChangedKeys);
                // draft.version 保持与 current 一致，不递增
            } else {
                long newVersion = current.getVersion() + 1;
                draft.setVersion(newVersion);
            }

            cache.put(sessionId, draft);

            newCtx = draft;
            changedKeys = localChangedKeys;

            log.debug("上下文更新成功: sessionId={}, caller={}, version={}, changedKeys={}",
                sessionId, caller, draft.getVersion(), changedKeys);
        }

        // ★ 锁外执行派生刷新：缩短持锁时间 + 让 DERIVATION_DEPTH 独立栈帧生效
        if (allowDerivation && shouldTriggerDerivation(changedKeys)) {
            refreshDerivedLocked(sessionId, newCtx);
        }

        return true;
    }

    // =========================================================
    // 5. ★ derived 自动刷新（带深度保护）
    // =========================================================
    private boolean shouldTriggerDerivation(Set<String> changedKeys) {
        if (changedKeys == null || changedKeys.isEmpty()) {
            return false;
        }
        for (String k : changedKeys) {
            // ★ Phase SM：phaseRuntime 不是派生输入，跳过
            if ("phaseRuntime".equals(k)) {
                continue;
            }
            if (!k.startsWith("derived.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ 派生刷新外层包装：重入深度保护 + 异常隔离。
     */
    private void refreshDerivedLocked(String sessionId, CaseContext ctx) {
        int depth = DERIVATION_DEPTH.get();
        if (depth >= MAX_DERIVATION_DEPTH) {
            log.error("🚨 派生计算递归超限({}), 可能存在 SpEL 回调写变量, sessionId={}, caseType={}",
                depth, sessionId, ctx != null ? ctx.getCaseType() : null);
            return;
        }
        DERIVATION_DEPTH.set(depth + 1);
        try {
            doRefreshDerivedLocked(sessionId, ctx);
        } finally {
            int newDepth = DERIVATION_DEPTH.get() - 1;
            if (newDepth <= 0) {
                DERIVATION_DEPTH.remove();
            } else {
                DERIVATION_DEPTH.set(newDepth);
            }
        }
    }

    /**
     * ★ 派生刷新实际实现。
     *
     * <p><b>修复 B-2（2026-09-25）</b>：派生变量写回后，通过
     * {@code batch_variable_update} 单独广播给 Unity。</p>
     *
     * <p>背景：修复 A 让派生刷新不再 bump stateRevision，因此不会触发
     * SNAPSHOT 推送。若不广播，Unity 端的 {@code derived.timeElapsed*}
     * 等变量会卡住不动。</p>
     */
    private void doRefreshDerivedLocked(String sessionId, CaseContext ctx) {
        if (ctx == null) return;

        String caseType = ctx.getCaseType();
        if (caseType == null || caseType.isEmpty()) {
            log.debug("caseType 为空，跳过 derived 刷新: sessionId={}", sessionId);
            return;
        }

        Map<String, Object> newDerived;
        try {
            newDerived = derivationService.computeDerivedVariables(ctx);
        } catch (Exception e) {
            log.warn("计算派生变量失败: sessionId={}, err={}", sessionId, e.getMessage());
            return;
        }
        if (newDerived == null || newDerived.isEmpty()) {
            log.debug("派生规则为空，跳过写回: sessionId={}", sessionId);
            return;
        }

        // ★ 只比较真正变化的字段（覆盖 derived.* 和 process.* 等所有派生输出）
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : newDerived.entrySet()) {
            Object oldVal = ctx.getVariables().get(e.getKey());
            if (!Objects.equals(oldVal, e.getValue())) {
                changes.put(e.getKey(), e.getValue());
            }
        }
        if (changes.isEmpty()) {
            log.debug("派生变量无变化，跳过写回: sessionId={}", sessionId);
            return;
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            updates.put("vars." + e.getKey(), e.getValue());
        }

        // ★ allowDerivation=false：截断由派生写回引发的递归
        boolean ok = doUpdateWithVersion(
            sessionId, updates, ctx.getVersion(), "SYSTEM_DERIVED", false);

        if (ok) {
            log.debug("♻️ 派生变量已刷新: sessionId={}, changes={}", sessionId, changes);

            // ★ 修复 B-2（2026-09-25）：派生变量变更广播给 Unity
            //
            // 修复 A 后派生刷新不 bump version，不会触发 SNAPSHOT；
            // 需要单独广播 batch_variable_update，让 Unity 端能感知
            // derived.timeElapsedSinceLastIntervention 等变量的变化。
            Map<String, Object> broadcastVars = filterBroadcastVars(changes);
            if (broadcastVars != null
                && wsRegistry.resolveToRoomSessionId(sessionId) != null) {

                Map<String, Object> payload = Map.of(
                    "type", "batch_variable_update",
                    "data", Map.of("changes", broadcastVars),
                    "timestamp", System.currentTimeMillis()
                );
                messageSender.send(sessionId, payload);

                log.debug("📤 派生变量已广播: sessionId={}, keys={}",
                    sessionId, broadcastVars.keySet());
            }
        } else {
            log.warn("派生变量写回失败（版本冲突）: sessionId={}", sessionId);
        }
    }

    private boolean isRecognizedVarPrefix(String varName) {
        if (varName == null || varName.isEmpty()) return false;
        return varName.startsWith("phys.")
            || varName.startsWith("process.")
            || varName.startsWith("derived.")
            || varName.startsWith("intervention.")
            || varName.startsWith("assert.")
            || varName.startsWith("report.")
            || varName.startsWith("device.")
            || varName.startsWith("clinical.")
            || varName.startsWith("case.")
            || varName.startsWith("evaluation.")
            || varName.startsWith("__");   // 内部变量：__extraction_memory
    }

    // =========================================================
    // 6. 语义化写入入口
    // =========================================================

    /**
     * 单变量写入。
     *
     * <p>★ 跳过 {@code derived.*} 和 {@link AclsInternalVars} 白名单内部变量的权限校验：
     * 前者由系统计算，后者是心跳/评分链内部数据；两者都不走 {@code VariableMetaLoader}，
     * 避免触发"变量未定义，允许写入"的 WARN 刷屏。</p>
     *
     * <p>★ 广播前判断：</p>
     * <ul>
     *   <li>目标变量属于 {@link AclsInternalVars} 白名单 → 只写 Context，不广播</li>
     *   <li>别名未注册（`start_case` 之前）→ 静默跳过广播，避免创建期 WARN 刷屏</li>
     * </ul>
     */
    public boolean setVarWithVersion(String sessionId, String varName, Object value,
                                     String writer, long expectedVersion) {
        CaseContext ctx = getSnapshot(sessionId);
        if (ctx == null) {
            ctx = getOrCreate(sessionId);
        }
        String caseType = ctx.getCaseType();

        // ★ 跳过 derived.* 和内部变量：前者由系统计算，后者是内部心跳/评分链。
        //   两者都不经过 VariableMetaLoader 的权限/类型校验，避免刷屏 WARN。
        if (!varName.startsWith("derived.") && !AclsInternalVars.isInternal(varName)) {
            if (!metaManager.checkWritePermission(caseType, varName, writer)) {
                throw new VariableAccessDeniedException("变量 " + varName + " 不允许 " + writer + " 写入");
            }
            if (!metaManager.validateValue(caseType, varName, value)) {
                throw new VariableValidationException("变量 " + varName + " 的值 " + value + " 不合法");
            }
        }

        Map<String, Object> updates = Map.of("vars." + varName, value);
        boolean success = updateWithVersion(sessionId, updates, expectedVersion, writer);

        if (success) {
            // ★ 内部变量不广播
            if (AclsInternalVars.isInternal(varName)) {
                log.debug("单变量写入仅含内部变量，跳过广播: sessionId={}, varName={}",
                    sessionId, varName);
            }
            // ★ 别名未注册（start_case 尚未触发）：静默跳过广播
            else if (wsRegistry.resolveToRoomSessionId(sessionId) == null) {
                log.debug("别名未注册，静默跳过广播: sessionId={}, varName={}",
                    sessionId, varName);
            }
            else {
                Map<String, Object> payload = Map.of(
                    "type", "variable_update",
                    "data", Map.of("varName", varName, "newValue", value),
                    "timestamp", System.currentTimeMillis()
                );
                messageSender.send(sessionId, payload);
            }
        }
        return success;
    }

    /**
     * 批量变量写入。
     *
     * <p>★ 跳过 {@code derived.*} 和 {@link AclsInternalVars} 白名单内部变量的权限校验：
     * 前者由系统计算，后者是心跳/评分链内部数据；两者都不走 {@code VariableMetaLoader}，
     * 避免触发"变量未定义，允许写入"的 WARN 刷屏。</p>
     *
     * <p>★ 广播前用 {@link #filterBroadcastVars} 过滤内部变量。
     * 过滤后为空或别名未注册则不发送任何 WebSocket 消息。</p>
     */
    public boolean batchSetVarsWithVersion(String sessionId, Map<String, Object> varMap,
                                           String writer, long expectedVersion) {

        CaseContext ctx = getSnapshot(sessionId);
        if (ctx == null) {
            ctx = getOrCreate(sessionId);
        }
        String caseType = ctx.getCaseType();

        Map<String, Object> updates = new LinkedHashMap<>();
        Map<String, Object> changedVars = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : varMap.entrySet()) {
            String varName = entry.getKey();
            Object value = entry.getValue();

            // ★ 1. 顶层字段：直接放行，跳过权限与校验
            if ("currentState".equals(varName)
                || "caseType".equals(varName)
                || "currentPhysiology".equals(varName)) {
                updates.put(varName, value);
                changedVars.put(varName, value);
                continue;
            }

            // ★ 2. derived.* 由系统计算，跳过权限与校验
            if (varName != null && varName.startsWith("derived.")) {
                updates.put("vars." + varName, value);
                changedVars.put(varName, value);
                continue;
            }

            // ★ 2.5 内部变量（process.clockTickAt / process.lastInterventionTime /
            //        evaluation.errorChain 等）跳过权限与校验，避免 VariableMetaLoader
            //        每次 tick 打一条"变量未定义，允许写入"WARN。
            //        变量字典外的写入由 AclsInternalVars 白名单统一放行。
            if (AclsInternalVars.isInternal(varName)) {
                updates.put("vars." + varName, value);
                changedVars.put(varName, value);
                continue;
            }

            // ★ 3. caseType 未初始化（初始化阶段）：跳过权限校验，只做写入
            if (caseType == null) {
                log.warn("caseType 未初始化，跳过权限校验: varName={}, writer={}", varName, writer);
                updates.put("vars." + varName, value);
                changedVars.put(varName, value);
                continue;
            }

            if (!metaManager.checkWritePermission(caseType, varName, writer)) {
                log.warn("跳过无权限变量: {}", varName);
                continue;
            }
            if (!metaManager.validateValue(caseType, varName, value)) {
                log.warn("跳过非法变量值: {}={}", varName, value);
                continue;
            }

            updates.put("vars." + varName, value);
            changedVars.put(varName, value);
        }

        if (updates.isEmpty()) {
            return true;
        }

        boolean success = updateWithVersion(sessionId, updates, expectedVersion, writer);

        if (success) {
            // ★ 过滤内部变量，避免每秒把 process.clockTickAt 等广播给 Unity
            Map<String, Object> broadcastVars = filterBroadcastVars(changedVars);
            if (broadcastVars == null) {
                log.debug("batch 写入仅含内部变量，跳过广播: sessionId={}, writer={}, keys={}",
                    sessionId, writer, changedVars.keySet());
            }
            // ★ 别名未注册（start_case 尚未触发）：静默跳过广播
            else if (wsRegistry.resolveToRoomSessionId(sessionId) == null) {
                log.debug("别名未注册，静默跳过广播: sessionId={}, writer={}, keys={}",
                    sessionId, writer, broadcastVars.keySet());
            }
            else {
                Map<String, Object> payload = Map.of(
                    "type", "batch_variable_update",
                    "data", Map.of("changes", broadcastVars),
                    "timestamp", System.currentTimeMillis()
                );
                messageSender.send(sessionId, payload);
            }
        }
        return success;
    }

    public boolean updateCurrentPhysiology(String sessionId, StatePhysiology physiology,
                                           long expectedVersion) {
        if (physiology == null) {
            log.warn("尝试将生理基线设为 null, sessionId={}", sessionId);
            return false;
        }
        Map<String, Object> updates = Map.of("currentPhysiology", physiology);
        return updateWithVersion(sessionId, updates, expectedVersion, "updateCurrentPhysiology");
    }

    public StatePhysiology getCurrentPhysiology(String sessionId) {
        CaseContext ctx = getSnapshot(sessionId);
        return ctx != null ? ctx.getCurrentPhysiology() : null;
    }
    // =========================================================
    // 6.6 ★ 修复 1.1.B（2026-09-25）：事件序列推进
    // =========================================================

    /**
     * ★ 修复 1.1.B（2026-09-25）：推进 eventSequence 并写回。
     *
     * <p><b>背景</b>：原 {@code AttemptEventProcessor.process(CaseContext, SelectionInput)}
     * 接收的是 {@code getSnapshot(...)} 返回的深拷贝副本。副本上调用
     * {@code advanceEventSequence()} 只改副本，不写回 cache，导致：</p>
     * <ul>
     *   <li>{@code CaseContext.eventSequence} 永远停在 0；</li>
     *   <li>AttemptManifest 冻结锚点失效（P0-9）；</li>
     *   <li>日志中 {@code eventSequence=-1}。</li>
     * </ul>
     *
     * <p><b>语义</b>：与 {@link #advanceClock(String)}、
     * {@link #setInternalVarNoVersion(String, String, Object)} 一致：</p>
     * <ul>
     *   <li>只推进 {@code eventSequence}，不改 {@code version}；</li>
     *   <li>不触发派生刷新；</li>
     *   <li>每事件无条件递增，只增不减。</li>
     * </ul>
     *
     * <p>{@code version} 与 {@code eventSequence} 严格分离：前者用于乐观锁与权威状态版本，
     * 后者用于事件溯源与 AttemptManifest 冻结锚点。</p>
     *
     * @param sessionId 会话 ID
     * @return 推进后的新 eventSequence；上下文不存在或 sessionId 为 null 时返回 -1
     */
    public long advanceEventSequence(String sessionId) {
        if (sessionId == null) {
            return -1L;
        }

        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) {
                log.warn("[EmsContextManager] advanceEventSequence 失败：上下文不存在, sessionId={}",
                    sessionId);
                return -1L;
            }

            long seq = current.advanceEventSequence();
            // 直接 put 回，不需要 copy：advanceEventSequence 内部已改 lastUpdateTime
            cache.put(sessionId, current);

            log.debug("[EmsContextManager] eventSequence 推进: sessionId={}, seq={}",
                sessionId, seq);
            return seq;
        }
    }
    /**
     * ★ 写入内部变量，不递增版本号。
     *
     * <p>用途：{@code process.clockTickAt} 等心跳/计时类变量。它们：</p>
     * <ul>
     *   <li>只改 CaseContext.variables 里的对应字段</li>
     *   <li>不递增版本号（避免每秒无意义的乐观锁更新）</li>
     *   <li>不更新 lastUpdateTime</li>
     *   <li>但仍然触发派生刷新（因为 process.timeElapsedSinceLastIntervention 依赖它）</li>
     * </ul>
     *
     * <p>语义与 {@link #advanceClock(String)} 一致：系统内部状态变化不占用业务版本号。</p>
     *
     * @param sessionId 会话 ID
     * @param varName   变量名（必须是 {@link AclsInternalVars} 白名单内的内部变量）
     * @param value     值
     * @return 是否成功；值为 null 或与旧值相同返回 true（幂等）
     */
    public boolean setInternalVarNoVersion(String sessionId, String varName, Object value) {
        if (sessionId == null || varName == null || value == null) {
            return false;
        }

        CaseContext newCtx;
        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) {
                return false;
            }

            Object old = current.getVariables().get(varName);
            if (Objects.equals(old, value)) {
                return true;  // 值相同，不写
            }

            CaseContext draft = current.copy();
            draft.setVar(varName, value, "SYSTEM_TICK");
            // ★ 关键：不递增版本号、不更新 lastUpdateTime
            cache.put(sessionId, draft);
            newCtx = draft;
        }

        // ★ 锁外触发派生刷新
        //   process.timeElapsedSinceLastIntervention 依赖 process.clockTickAt，
        //   必须在心跳写入后立刻重算，否则延时规则不会触发。
        refreshDerivedLocked(sessionId, newCtx);

        return true;
    }

    // =========================================================
    // 6.5 ★ Phase SM：PhaseRuntime 读写入口
    // =========================================================

    /**
     * 更新 PhaseRuntime。
     *
     * <p>与 {@link #updateCurrentPhysiology} 同模式：</p>
     * <ul>
     *   <li>走 {@link #updateWithVersion} 的乐观锁路径</li>
     *   <li>会占用 {@code CaseContext.version}（Phase 变化与变量变化共享同一版本号）</li>
     *   <li>入参先 copy 一份，避免调用方后续修改影响 cache 中的对象</li>
     * </ul>
     *
     * <p><b>调用约定</b>：PhaseManager 应先 {@code getSnapshot(...)} 拿到副本，
     * 在副本上修改 {@code phaseRuntime}，然后把修改后的 PhaseRuntime 传进来。</p>
     *
     * @param sessionId       会话 ID
     * @param phaseRuntime    新的 PhaseRuntime（非 null）
     * @param expectedVersion 期望的上下文版本号
     * @return true=成功，false=失败（版本冲突或上下文不存在）
     */
    public boolean updatePhaseRuntime(String sessionId, PhaseRuntime phaseRuntime,
                                      long expectedVersion) {
        if (phaseRuntime == null) {
            log.warn("尝试将 PhaseRuntime 设为 null, sessionId={}", sessionId);
            return false;
        }
        // 防御性拷贝：调用方传入的对象可能与 cache 中的对象共享引用
        Map<String, Object> updates = Map.of("phaseRuntime", phaseRuntime.copy());
        return updateWithVersion(sessionId, updates, expectedVersion, "PhaseManager");
    }

    /**
     * 读取 PhaseRuntime。
     *
     * <p>返回的是深拷贝副本，调用方可以自由修改，不会影响 cache。
     * 修改后需通过 {@link #updatePhaseRuntime} 写回。</p>
     *
     * @return PhaseRuntime 副本；上下文不存在时返回 null
     */
    public PhaseRuntime getPhaseRuntime(String sessionId) {
        CaseContext ctx = getSnapshot(sessionId);
        if (ctx == null) {
            return null;
        }
        return ctx.getPhaseRuntime();
    }

    // =========================================================
    // 7. 对话状态写入
    // =========================================================
    public boolean updateConversationState(String sessionId,
                                           IntentCommand lastIntentResult,
                                           String appendHistoryIntentCode,
                                           ClassificationContext.DialogTurn appendTurn) {
        if (sessionId == null) {
            log.warn("updateConversationState: sessionId 为 null");
            return false;
        }
        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) {
                log.warn("上下文不存在, sessionId={}", sessionId);
                return false;
            }

            CaseContext newCtx = current.copy();
            boolean changed = false;

            if (lastIntentResult != null
                && !Objects.equals(current.getLastIntentResult(), lastIntentResult)) {
                newCtx.setLastIntentResult(lastIntentResult);
                changed = true;
            }
            if (appendHistoryIntentCode != null) {
                newCtx.addIntentHistory(appendHistoryIntentCode);
                changed = true;
            }
            if (appendTurn != null) {
                newCtx.addDialogTurn(appendTurn);
                changed = true;
            }

            if (!changed) {
                log.debug("对话状态无变化, sessionId={}", sessionId);
                return true;
            }

            newCtx.setLastUpdateTime(LocalDateTime.now());
            newCtx.setVersion(current.getVersion() + 1);
            cache.put(sessionId, newCtx);

            log.info("对话状态更新成功: sessionId={}, newVersion={}, lastIntent={}, historyAppended={}, turnAppended={}",
                sessionId,
                newCtx.getVersion(),
                lastIntentResult != null ? lastIntentResult.getIntentCode() : null,
                appendHistoryIntentCode != null,
                appendTurn != null);

            return true;
        }
    }

    // =========================================================
    // 7.5 时钟推进：系统心跳专用，不走版本号、不触发 derived
    // =========================================================
    public boolean advanceClock(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) {
                return false;
            }
            current.tick();
            cache.put(sessionId, current);
            return true;
        }
    }

    // =========================================================
    // 8. 重置变量（跨域清理）
    // =========================================================
    public boolean resetVariables(String sessionId, List<String> varNames, String writer) {
        if (varNames == null || varNames.isEmpty()) {
            return true;
        }
        CaseContext ctx = getSnapshot(sessionId);
        if (ctx == null) {
            log.warn("上下文不存在, sessionId={}", sessionId);
            return false;
        }
        long currentVersion = ctx.getVersion();

        Map<String, Object> resetMap = new LinkedHashMap<>();
        for (String varName : varNames) {
            if (varName.startsWith("derived.")) {
                continue;
            }
            resetMap.put(varName, null);
        }
        if (resetMap.isEmpty()) {
            return true;
        }
        return batchSetVarsWithVersion(sessionId, resetMap, writer, currentVersion);
    }

    // =========================================================
    // 9. 应用 resetDefaults
    // =========================================================
    /**
     * ★ 修复要点：
     * 1. 去掉显式的二次 refreshDerivedLocked 调用 —— batchSetVarsWithVersion
     *    内部已通过 doUpdateWithVersion 触发过一次派生刷新，重复调用会导致
     *    Reactor subscriber chain 叠加。
     * 2. 补齐 getSnapshot 为 null 后 getOrCreate 的二次判空。
     *
     * <p>★ 2026-09-22 新增：写完后确保 currentState 非空。防止某些案例的
     * resetDefaults 意外把 currentState 清空，或者创建路径绕过 getOrCreate 的
     * 初始状态设置。防御性兜底，仅在 currentState 为空时才写入 fallback。</p>
     */
    public boolean applyResetDefaults(String sessionId, String caseType, String writer) {
        Map<String, Object> defaults = metaManager.getResetDefaults(caseType);
        if (defaults == null || defaults.isEmpty()) {
            log.info("案例 {} 没有配置 resetDefaults，跳过初始化", caseType);
            ensureInitialState(sessionId);
            return true;
        }

        CaseContext ctx = getSnapshot(sessionId);
        if (ctx == null) {
            ctx = getOrCreate(sessionId);
        }

        // ★ 无论 ctx 是刚创建还是已存在，只要 caseType 未设置就补上
        String existing = ctx.getCaseType();
        if (existing == null || existing.isEmpty()) {
            Map<String, Object> initUpdates = Map.of("caseType", caseType);
            updateWithVersion(sessionId, initUpdates, ctx.getVersion(), "applyResetDefaults");
            // 重新取快照拿递增后的版本号
            ctx = getSnapshot(sessionId);
        } else if (!caseType.equals(existing)) {
            log.warn("上下文 caseType={} 与传入 {} 不一致，忽略覆盖",
                existing, caseType);
        }

        long currentVersion = ctx.getVersion();
        Map<String, Object> varMap = new LinkedHashMap<>(defaults);
        boolean ok = batchSetVarsWithVersion(sessionId, varMap, writer, currentVersion);

        // ★ 2026-09-22 新增：写完后兜底 currentState
        ensureInitialState(sessionId);
        return ok;
    }

    public boolean applyResetDefaults(String sessionId, String caseType) {
        return applyResetDefaults(sessionId, caseType, "SYSTEM");
    }

    /**
     * ★ 2026-09-22 新增：兜底 currentState 非空。
     *
     * <p>只在 currentState 为 null 时写入 {@link #FALLBACK_INITIAL_STATE}；
     * 已有值（包括 fallback 之外的真实状态）绝不覆盖，避免误改运行中的状态。</p>
     */
    private void ensureInitialState(String sessionId) {
        if (sessionId == null) return;

        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) return;
            if (current.getCurrentState() != null) return;

            CaseContext draft = current.copy();
            draft.setCurrentState(FALLBACK_INITIAL_STATE);
            draft.setLastUpdateTime(LocalDateTime.now());
            draft.setVersion(current.getVersion() + 1);
            cache.put(sessionId, draft);

            log.info("[EmsContextManager] ensureInitialState 兜底写入: sessionId={}, currentState={}",
                sessionId, FALLBACK_INITIAL_STATE);
        }
    }

    // =========================================================
    // 10. 提取记忆
    // =========================================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExtractionMemory(String sessionId) {
        CaseContext ctx = getSnapshot(sessionId);
        if (ctx == null) return null;
        Object mem = ctx.getVariables().get("__extraction_memory");
        return mem instanceof Map ? (Map<String, Object>) mem : null;
    }

    public boolean updateExtractionMemory(String sessionId, Map<String, Object> memory,
                                          long expectedVersion) {
        if (memory == null) memory = new HashMap<>();
        return updateWithVersion(sessionId,
            Map.of("vars.__extraction_memory", memory),
            expectedVersion, "updateExtractionMemory");
    }

    // =========================================================
    // 11. 查询 / 清理
    // =========================================================
    public Set<String> getModifiedVariables(String sessionId) {
        CaseContext ctx = getSnapshot(sessionId);
        return ctx != null ? ctx.getModifiedVariables() : Collections.emptySet();
    }

    public void clearModifiedVariables(String sessionId) {
        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) return;
            CaseContext newCtx = current.copy();
            newCtx.getModifiedVariables().clear();
            newCtx.setLastUpdateTime(LocalDateTime.now());
            newCtx.setVersion(current.getVersion() + 1);
            cache.put(sessionId, newCtx);
        }
    }

    public void clearContext(String sessionId) {
        cache.remove(sessionId);
        log.debug("上下文已清除: sessionId={}", sessionId);
    }

    /**
     * 设置会话时钟运行状态。
     * <p>
     * true  → CaseContext.resumeClock()，tick() 会累加 elapsedSeconds
     * false → CaseContext.pauseClock()，tick() 跳过累加
     * </p>
     * <p>
     * 与 advanceClock 一致，不走版本号：clockStatus 是系统内部状态，
     * 变化不需要触发 derived 刷新，也不需要让外部缓存的 version 失效。
     * </p>
     *
     * @param sessionId 业务会话 ID
     * @param running   true=运行，false=暂停
     * @return 是否成功
     */
    public boolean setClockRunning(String sessionId, boolean running) {
        if (sessionId == null) return false;

        synchronized (cache) {
            CaseContext current = cache.get(sessionId);
            if (current == null) {
                log.warn("setClockRunning 失败：上下文不存在 session={}", sessionId);
                return false;
            }

            boolean currentlyRunning = current.isClockRunning();
            if (running == currentlyRunning) {
                return true;   // 幂等
            }

            if (running) {
                current.resumeClock();
            } else {
                current.pauseClock();
            }
            cache.put(sessionId, current);

            log.info("⏱️ 时钟状态变更: session={}, running={}, elapsed={}s",
                sessionId, running, current.getElapsedSeconds());
            return true;
        }
    }
}

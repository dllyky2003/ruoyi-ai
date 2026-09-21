package com.ruoyi.ems.domain;

import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.context.EmsContextManager;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * 运行时上下文容器 - 动态变量存储 + 意图历史 + 系统时间管理 + 对话历史
 * 所有变量以键值对形式存放，支持类型安全的存取和变更追踪
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaseContext {
    // === 固定标识字段 ===
    private String sessionId;
    private String caseType;          // 案例类型编码
    private String currentState;      // 当前状态机状态（便于快速访问）
    private LocalDateTime lastUpdateTime;
    private LocalDateTime sessionStartTime;
    private StatePhysiology currentPhysiology;

    // ★ Phase SM 新增：阶段运行时状态（独立字段，不进入 variables）
    private PhaseRuntime phaseRuntime;

    private long version = 0L;

    /**
     * ★ 阶段 3 新增：事件序列。
     *
     * <p>与 {@link #version} 分离：{@code version} 用于变量乐观锁与权威状态版本，
     * {@code eventSequence} 每处理一个事件递增一次，用于事件溯源与 AttemptManifest
     * 冻结锚点（P0-9）。</p>
     *
     * <p>语义：只增不减、单调递增；不参与 copy 后版本比较；copy() 时按值复制。</p>
     */
    private long eventSequence = 0L;

    // === 动态变量存储 ===
    private Map<String, Object> variables = new ConcurrentHashMap<>();

    // === ★ 记录被修改过的变量名集合（去重、线程安全） ===
    private Set<String> modifiedVariables = ConcurrentHashMap.newKeySet();

    // === 变量变更日志（事件溯源） ===
    private List<VariableChange> changeLog = new ArrayList<>();

    // === 意图历史记录（最近 N 条） ===
    private List<String> intentHistory = new ArrayList<>();

    // === 最近一次意图分类结果 ===
    private IntentCommand lastIntentResult;

    // === ★ 默认值解析器（由 EmsContextManager 创建时注入，transient 避免序列化） ===
    private transient EmsContextManager contextManager;

    // ============================================================
    // 系统时间管理
    // ============================================================
    private int elapsedSeconds = 0;
    private String clockStatus = "RUNNING";

    // ============================================================
    // 对话历史（滑动窗口）
    // ============================================================
    private List<ClassificationContext.DialogTurn> recentDialogs = new ArrayList<>();

    // ============================================================
    // 构造器（确保集合非空）
    // ============================================================
    public CaseContext(String sessionId) {
        this.sessionId = sessionId;
        this.sessionStartTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
        this.variables = new ConcurrentHashMap<>();
        this.modifiedVariables = ConcurrentHashMap.newKeySet();
        this.changeLog = new ArrayList<>();
        this.intentHistory = new ArrayList<>();
        this.recentDialogs = new ArrayList<>();
        this.clockStatus = "RUNNING";
        this.version = 0L;
        // ★ 阶段 3：构造时事件序列归零
        this.eventSequence = 0L;
        // ★ Phase SM：构造时初始化空 PhaseRuntime，避免调用方到处判空
        this.phaseRuntime = new PhaseRuntime();
    }

    // ============================================================
    // ★ 复制方法：用于版本更新时创建新的上下文副本
    // ============================================================
    /**
     * 深拷贝。variables 是配置驱动的动态 Map，必须新建实例；
     * 其余元字段直接按值复制。
     */
    /**
     * 深拷贝。
     *
     * <p>variables 是配置驱动的动态 Map（phys.* / process.* / derived.* 等都是它的 key），
     * 只新建外层 Map；值多为不可变对象，共享引用即可。</p>
     *
     * <p>其余集合类字段（changeLog / intentHistory / recentDialogs / modifiedVariables）
     * 都新建实例，避免副本与原对象共享内部集合。</p>
     */
    public CaseContext copy() {
        CaseContext c = new CaseContext();

        // ---------- 元字段 ----------
        c.setSessionId(this.sessionId);
        c.setCaseType(this.caseType);
        c.setCurrentState(this.currentState);
        c.setCurrentPhysiology(this.currentPhysiology);
        c.setSessionStartTime(this.sessionStartTime);
        c.setLastUpdateTime(this.lastUpdateTime);
        c.setVersion(this.version);

        // ★ 阶段 3：eventSequence 按值复制，保证冻结锚点一致
        c.setEventSequence(this.eventSequence);

        // ★ Phase SM：phaseRuntime 深拷贝（集合字段新建实例）
        c.setPhaseRuntime(this.phaseRuntime != null ? this.phaseRuntime.copy() : null);

        // ---------- 系统时间 ----------
        c.setElapsedSeconds(this.elapsedSeconds);
        c.setClockStatus(this.clockStatus);

        // ---------- 动态变量 Map ----------
        c.setVariables(this.variables != null
            ? new ConcurrentHashMap<>(this.variables)
            : new ConcurrentHashMap<>());

        // ---------- 修改记录 Set ----------
        c.setModifiedVariables(this.modifiedVariables != null
            ? ConcurrentHashMap.newKeySet()   // 保持线程安全语义
            : ConcurrentHashMap.newKeySet());
        if (this.modifiedVariables != null) {
            c.getModifiedVariables().addAll(this.modifiedVariables);
        }

        // ---------- 变更日志 ----------
        c.setChangeLog(this.changeLog != null
            ? new ArrayList<>(this.changeLog)
            : new ArrayList<>());

        // ---------- 意图历史 ----------
        c.setIntentHistory(this.intentHistory != null
            ? new ArrayList<>(this.intentHistory)
            : new ArrayList<>());

        // ---------- 对话历史 ----------
        c.setRecentDialogs(this.recentDialogs != null
            ? new ArrayList<>(this.recentDialogs)
            : new ArrayList<>());

        // ---------- 最近意图结果：POJO，只读引用即可 ----------
        c.setLastIntentResult(this.lastIntentResult);

        // ---------- 自引用（transient） ----------
        c.setContextManager(this.contextManager);

        return c;
    }
    // ============================================================
    // 时间便捷方法（保持不变）
    // ============================================================
    public void tick() {
        if ("RUNNING".equals(clockStatus)) {
            this.elapsedSeconds++;
            this.lastUpdateTime = LocalDateTime.now();
        }
    }

    public void resetTimer() {
        this.elapsedSeconds = 0;
        this.clockStatus = "RUNNING";
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void pauseClock() {
        this.clockStatus = "PAUSED";
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void resumeClock() {
        this.clockStatus = "RUNNING";
        this.lastUpdateTime = LocalDateTime.now();
    }

    public boolean isClockRunning() {
        return "RUNNING".equals(clockStatus);
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    // ============================================================
    // ★ 阶段 3：事件序列便捷方法
    // ============================================================

    /**
     * ★ 阶段 3：推进一次事件序列。
     *
     * <p>每个事件处理入口调用一次，返回推进后的新序号。
     * 与 {@link #version} 严格分离：version 只在权威状态变更时调整，
     * eventSequence 每事件无条件递增。</p>
     *
     * @return 递增后的 eventSequence
     */
    public long advanceEventSequence() {
        this.eventSequence++;
        this.lastUpdateTime = LocalDateTime.now();
        return this.eventSequence;
    }

    /**
     * ★ 阶段 3：读取当前事件序列（只读）。
     *
     * <p>注意：{@code @Data} 会为 {@code eventSequence} 生成默认 getter，
     * 此处显式覆盖以固定语义；如需写值请使用 {@link #advanceEventSequence()}
     * 或 setter（仅供 copy / 反序列化使用）。</p>
     */
    public long getEventSequence() {
        return eventSequence;
    }

    // ============================================================
    // 对话历史便捷方法（保持不变）
    // ============================================================
    public void addDialogTurn(ClassificationContext.DialogTurn turn) {
        if (recentDialogs == null) {
            recentDialogs = new ArrayList<>();
        }
        recentDialogs.add(turn);
        if (recentDialogs.size() > 20) {
            recentDialogs = new ArrayList<>(recentDialogs.subList(recentDialogs.size() - 20, recentDialogs.size()));
        }
        this.lastUpdateTime = LocalDateTime.now();
    }

    public List<ClassificationContext.DialogTurn> getRecentDialogs(int n) {
        if (recentDialogs == null || recentDialogs.isEmpty()) {
            return new ArrayList<>();
        }
        int size = recentDialogs.size();
        int start = Math.max(0, size - n);
        return new ArrayList<>(recentDialogs.subList(start, size));
    }

    public void clearDialogs() {
        if (recentDialogs != null) {
            recentDialogs.clear();
        }
    }

    // ============================================================
    // 变量操作方法（保留原有，但 modify 已由 ContextManager 统一管理）
    // ============================================================

    /**
     * 获取变量值。
     * <p>如果变量值为 {@code null}，则通过创建本上下文的 {@link EmsContextManager}
     * 回退获取默认值，解析顺序：</p>
     * <ol>
     *     <li>resetDefaults（案例专属合并通用后的默认值）</li>
     *     <li>{@code VariableMeta.defaultValue}（schema 定义的默认值）</li>
     * </ol>
     * <p><b>注意：</b>回退返回的默认值不会写回 {@code variables}，因此不会触发
     * {@link #modifiedVariables} 变更追踪，也不影响版本比较。</p>
     *
     * @param key  变量名，如 {@code "phys.heartRate"}
     * @param type 期望返回类型
     * @return 变量值；若无值且无默认值，返回 {@code null}
     */
    public <T> T getVar(String key, Class<T> type) {
        if (key == null) {
            return null;
        }
        Object value = variables.get(key);

        // ★ 值为 null 时，通过注入的 contextManager 回退获取默认值
        if (value == null) {
            value = resolveDefaultValue(key);
        }

        if (value == null) {
            return null;
        }

        // 原有类型转换逻辑保持不变
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (type == Integer.class && value instanceof Number) {
            return type.cast(((Number) value).intValue());
        }
        if (type == Long.class && value instanceof Number) {
            return type.cast(((Number) value).longValue());
        }
        if (type == Boolean.class && value instanceof String) {
            return type.cast(Boolean.valueOf((String) value));
        }
        if (type == String.class) {
            return type.cast(value.toString());
        }
        return null;
    }

    /**
     * ★ 通过创建本上下文的 EmsContextManager 解析变量默认值。
     * <p>若 contextManager 为 null（例如手动 new 的 CaseContext 或反序列化恢复的对象），
     * 则返回 null，不影响原有逻辑。</p>
     */
    private Object resolveDefaultValue(String key) {
        if (contextManager == null) {
            return null;
        }
        return contextManager.getVariableDefault(this.caseType, key);
    }

    @Deprecated
    public void setVar(String key, Object value, String writer) {
        Object oldValue = variables.get(key);
        if (oldValue != null && oldValue.equals(value)) {
            variables.put(key, value);
            return;
        }
        variables.put(key, value);
        // 同步更新 modifiedVariables（尽管不推荐直接调用此方法，但为兼容保留）
        this.modifiedVariables.add(key);
        changeLog.add(new VariableChange(key, oldValue, value, writer, LocalDateTime.now()));
        this.lastUpdateTime = LocalDateTime.now();
    }

    @Deprecated
    public void setVar(String key, Object value) {
        setVar(key, value, "SYSTEM");
    }

    @Deprecated
    public void setVars(Map<String, Object> varMap, String writer) {
        for (Map.Entry<String, Object> entry : varMap.entrySet()) {
            setVar(entry.getKey(), entry.getValue(), writer);
        }
    }

    public Map<String, Object> getVariablesSnapshot() {
        return new ConcurrentHashMap<>(variables);
    }

    public boolean hasVar(String key) {
        return variables.containsKey(key);
    }

    public void removeVar(String key) {
        variables.remove(key);
        // 注意：移除变量时，modifiedVariables 应当保留该变量名（因为它是被修改过的）
        // 但我们不清除，因为 remove 是一种修改，保留记录更合理。
        // 如果希望移除后不再记录，可调用 clearModifiedVariables() 单独处理。
    }

    public void clearVars() {
        variables.clear();
        changeLog.clear();
    }

    // ============================================================
    // ★ Phase SM 便捷方法
    // ============================================================

    /**
     * 获取当前主阶段。
     *
     * @return 主阶段 ID；未初始化返回 null
     */
    public String getPrimaryPhase() {
        return phaseRuntime != null ? phaseRuntime.getPrimaryPhase() : null;
    }

    /**
     * 是否已初始化 PhaseRuntime。
     * <p>{@link #CaseContext(String)} 构造器总会初始化，因此正常路径下返回 true。
     * 反序列化恢复的对象可能为 null。</p>
     */
    public boolean hasPhaseRuntime() {
        return phaseRuntime != null;
    }

    /**
     * 惰性初始化 PhaseRuntime。
     *
     * <p>用于反序列化恢复等场景：若 phaseRuntime 为 null，则创建一个空的。
     * 返回实际的 PhaseRuntime 实例，调用方可直接修改。</p>
     */
    public PhaseRuntime ensurePhaseRuntime() {
        if (this.phaseRuntime == null) {
            this.phaseRuntime = new PhaseRuntime();
        }
        return this.phaseRuntime;
    }

    // ============================================================
    // 意图历史方法（保持不变）
    // ============================================================
    public void addIntentHistory(String intentCode) {
        if (intentHistory == null) {
            intentHistory = new ArrayList<>();
        }
        intentHistory.add(intentCode);
        if (intentHistory.size() > 20) {
            intentHistory = new ArrayList<>(intentHistory.subList(intentHistory.size() - 20, intentHistory.size()));
        }
        this.lastUpdateTime = LocalDateTime.now();
    }

    public List<String> getRecentIntentHistory(int n) {
        if (intentHistory == null || intentHistory.isEmpty()) {
            return new ArrayList<>();
        }
        int size = intentHistory.size();
        int start = Math.max(0, size - n);
        return new ArrayList<>(intentHistory.subList(start, size));
    }

    public void clearIntentHistory() {
        if (intentHistory != null) {
            intentHistory.clear();
        }
    }

    /**
     * 获取所有被修改过的变量及其完整信息（返回扁平化 Map，便于序列化）
     *
     * @return Map<String, Object> key为变量名，value为包含完整信息的Map
     */
    public Map<String, Object> getModifiedVariablesAsMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (modifiedVariables == null || modifiedVariables.isEmpty()) {
            return result;
        }
        for (String varName : modifiedVariables) {
            Map<String, Object> info = new LinkedHashMap<>();
            Object currentValue = variables.get(varName);
            info.put("varName", varName);
            info.put("currentValue", currentValue);
            info.put("valueType", currentValue != null ? currentValue.getClass().getSimpleName() : "null");

            // 获取变更历史
            List<VariableChange> varHistory = changeLog.stream()
                .filter(c -> varName.equals(c.getKey()))
                .collect(Collectors.toList());
            VariableChange lastChange = varHistory.isEmpty() ? null : varHistory.get(varHistory.size() - 1);

            info.put("lastChangeTime", lastChange != null ? lastChange.getTimestamp() : null);
            info.put("lastWriter", lastChange != null ? lastChange.getWriter() : "UNKNOWN");
            info.put("changeCount", varHistory.size());
            info.put("changeHistory", varHistory); // 直接放入对象列表，序列化工具通常能处理

            result.put(varName, info);
        }
        return result;
    }

    /**
     * 获取所有被修改过的变量名列表（便捷方法）
     *
     * @return 被修改过的变量名集合
     */
    public Set<String> getModifiedVariableNames() {
        return modifiedVariables != null ? new HashSet<>(modifiedVariables) : new HashSet<>();
    }

    /**
     * 清空修改标记（通常在保存快照后调用）
     */
    public void clearModifiedFlags() {
        if (modifiedVariables != null) {
            modifiedVariables.clear();
        }
    }

    // ============================================================
    // 变量变更记录内部类（保持不变）
    // ============================================================
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VariableChange {
        private String key;
        private Object oldValue;
        private Object newValue;
        private String writer;
        private LocalDateTime timestamp;
    }
}

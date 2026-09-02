package com.ruoyi.ems.domain;

import com.ruoyi.ems.context.ClassificationContext;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时上下文容器 - 动态变量存储 + 意图历史 + 系统时间管理 + 对话历史
 * 所有变量以键值对形式存放，支持类型安全的存取和变更追踪
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaseContext {
    // === 固定标识字段 ===
    private String caseInstanceId;
    private String caseType;          // 案例类型编码
    private String currentState;      // 当前状态机状态（便于快速访问）
    private LocalDateTime lastUpdateTime;
    private LocalDateTime sessionStartTime;

    // === 动态变量存储 ===
    private Map<String, Object> variables = new ConcurrentHashMap<>();

    // === 变量变更日志（事件溯源） ===
    private List<VariableChange> changeLog = new ArrayList<>();

    // === 意图历史记录（最近 N 条） ===
    private List<String> intentHistory = new ArrayList<>();

    /**
     * 最近一次意图分类结果（包含意图码、参数、置信度等）
     */
    private IntentCommand lastIntentResult;

    // ============================================================
    // ★ 新增：系统时间管理（独立于 variables）
    // ============================================================

    /**
     * 系统累计时间（秒），由 ClockService 每秒递增
     * 默认从 0 开始
     */
    private int elapsedSeconds = 0;

    /**
     * 时钟状态：RUNNING / PAUSED / RESET
     * 默认 RUNNING
     */
    private String clockStatus = "RUNNING";

    // ============================================================
    // ★ 新增：对话历史（滑动窗口）
    // ============================================================

    /**
     * 最近对话历史（用于上下文连贯性）
     * 最多保留 20 轮对话
     */
    private List<ClassificationContext.DialogTurn> recentDialogs = new ArrayList<>();

    // ============================================================
    // ★ 时间便捷方法
    // ============================================================

    /**
     * 时钟前进一秒（由 ClockService 定时调用）
     * 仅在状态为 RUNNING 时递增
     */
    public void tick() {
        if ("RUNNING".equals(clockStatus)) {
            this.elapsedSeconds++;
            this.lastUpdateTime = LocalDateTime.now();
        }
    }

    /**
     * 重置计时器（归零并设为 RUNNING）
     */
    public void resetTimer() {
        this.elapsedSeconds = 0;
        this.clockStatus = "RUNNING";
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 暂停时钟（累计时间暂停增长）
     */
    public void pauseClock() {
        this.clockStatus = "PAUSED";
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 恢复时钟（累计时间继续增长）
     */
    public void resumeClock() {
        this.clockStatus = "RUNNING";
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 检查时钟是否正在运行
     */
    public boolean isClockRunning() {
        return "RUNNING".equals(clockStatus);
    }

    /**
     * 获取当前累计秒数（语义化方法）
     */
    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    // ============================================================
    // ★ 对话历史便捷方法
    // ============================================================

    /**
     * 添加对话轮次（滑动窗口，最多保留 20 轮）
     */
    public void addDialogTurn(ClassificationContext.DialogTurn turn) {
        if (recentDialogs == null) {
            recentDialogs = new ArrayList<>();
        }
        recentDialogs.add(turn);
        // 滑动窗口：最多保留 20 轮
        if (recentDialogs.size() > 20) {
            recentDialogs = new ArrayList<>(recentDialogs.subList(recentDialogs.size() - 20, recentDialogs.size()));
        }
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 获取最近 N 条对话
     */
    public List<ClassificationContext.DialogTurn> getRecentDialogs(int n) {
        if (recentDialogs == null || recentDialogs.isEmpty()) {
            return new ArrayList<>();
        }
        int size = recentDialogs.size();
        int start = Math.max(0, size - n);
        return new ArrayList<>(recentDialogs.subList(start, size));
    }

    /**
     * 清空对话历史
     */
    public void clearDialogs() {
        if (recentDialogs != null) {
            recentDialogs.clear();
        }
    }

    // ============================================================
    // 原有变量操作方法（保持不变）
    // ============================================================

    /**
     * 获取变量值，带类型转换
     */
    @SuppressWarnings("unchecked")
    public <T> T getVar(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        // 尝试类型转换（如 Integer -> String 等）
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (type == Boolean.class && value instanceof String) {
            return (T) Boolean.valueOf((String) value);
        }
        if (type == String.class) {
            return (T) value.toString();
        }
        // 无法转换则返回 null
        return null;
    }

    /**
     * 设置变量值，并记录变更日志（需外部传入写入者标识）
     */
    public void setVar(String key, Object value, String writer) {
        Object oldValue = variables.get(key);
        // 如果新旧值相同且不为null，不记录变更
        if (oldValue != null && oldValue.equals(value)) {
            variables.put(key, value); // 仍覆盖，但不记录日志
            return;
        }
        variables.put(key, value);
        changeLog.add(new VariableChange(key, oldValue, value, writer, LocalDateTime.now()));
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 简化设置，默认写入者为 "SYSTEM"
     */
    public void setVar(String key, Object value) {
        setVar(key, value, "SYSTEM");
    }

    /**
     * 批量设置变量（用于槽位映射）
     */
    public void setVars(Map<String, Object> varMap, String writer) {
        for (Map.Entry<String, Object> entry : varMap.entrySet()) {
            setVar(entry.getKey(), entry.getValue(), writer);
        }
    }

    /**
     * 获取所有变量快照（只读）
     */
    public Map<String, Object> getVariablesSnapshot() {
        return new ConcurrentHashMap<>(variables);
    }

    /**
     * 检查是否存在某个变量
     */
    public boolean hasVar(String key) {
        return variables.containsKey(key);
    }

    /**
     * 移除变量（谨慎使用）
     */
    public void removeVar(String key) {
        variables.remove(key);
    }

    /**
     * 清空变量（慎用）
     */
    public void clearVars() {
        variables.clear();
        changeLog.clear();
    }

    // ========== 意图历史相关方法 ==========

    /**
     * 添加意图历史记录，并自动限制最大长度（默认 20 条）
     */
    public void addIntentHistory(String intentCode) {
        if (intentHistory == null) {
            intentHistory = new ArrayList<>();
        }
        intentHistory.add(intentCode);
        // 限制长度，防止无限增长
        if (intentHistory.size() > 20) {
            intentHistory = new ArrayList<>(intentHistory.subList(intentHistory.size() - 20, intentHistory.size()));
        }
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 获取最近 N 条意图历史
     */
    public List<String> getRecentIntentHistory(int n) {
        if (intentHistory == null || intentHistory.isEmpty()) {
            return new ArrayList<>();
        }
        int size = intentHistory.size();
        int start = Math.max(0, size - n);
        return new ArrayList<>(intentHistory.subList(start, size));
    }

    /**
     * 清空意图历史
     */
    public void clearIntentHistory() {
        if (intentHistory != null) {
            intentHistory.clear();
        }
    }

    // ========== 变量变更记录内部类 ==========

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VariableChange {
        private String key;
        private Object oldValue;
        private Object newValue;
        private String writer;    // SIMULATOR, CLASSIFIER, STATE_MACHINE, TIMER, SYSTEM
        private LocalDateTime timestamp;
    }
}

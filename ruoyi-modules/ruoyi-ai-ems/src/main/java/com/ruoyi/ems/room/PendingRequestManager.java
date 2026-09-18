package com.ruoyi.ems.room;

import com.ruoyi.ems.room.event.EngineOfflineEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 待处理请求管理器。
 * <p>
 * 服务端下发查询/动作到 Unity 后，注册一个 requestId → CompletableFuture 的映射。
 * Unity 回执到达时，按 requestId 完成 future。
 * 超时或引擎断线时，future 失败。
 * </p>
 * <p>
 * 生命周期：
 * <ol>
 *   <li>register：创建 future，调度超时任务</li>
 *   <li>complete / completeExceptionally：完成 future，取消超时任务</li>
 *   <li>超时触发：future 以 TimeoutException 完成</li>
 *   <li>EngineOfflineEvent：该会话所有 pending 立即失败</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class PendingRequestManager {

    /**
     * 待处理请求条目。
     */
    private record PendingEntry(
        String sessionId,
        String type,
        CompletableFuture<Object> future,
        ScheduledFuture<?> timeoutTask
    ) {}

    /** requestId → PendingEntry */
    private final Map<String, PendingEntry> pending = new ConcurrentHashMap<>();

    /** 超时调度器，daemon 线程，不阻止 JVM 退出 */
    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "pending-timeout");
            t.setDaemon(true);
            return t;
        });

    // ==================== 统计 ====================

    private final AtomicLong completedCount = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();

    // ==================== 注册 ====================

    /**
     * 注册一个待处理的请求。返回的 future 在回执到达时完成。
     *
     * @param requestId 请求 ID，不能为空
     * @param sessionId 房间 ID，用于断线时批量失败
     * @param type      请求类型（vitals_query / npc_action / intervention 等），仅用于日志
     * @param timeout   超时时间，null 或 <=0 时用默认 5 秒
     * @return 等待 Unity 回执的 future
     */
    public CompletableFuture<Object> register(
        String requestId, String sessionId, String type, Duration timeout) {

        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        Duration effectiveTimeout =
            (timeout == null || timeout.isZero() || timeout.isNegative())
                ? Duration.ofSeconds(5)
                : timeout;

        CompletableFuture<Object> future = new CompletableFuture<>();

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            PendingEntry entry = pending.remove(requestId);
            if (entry != null) {
                timeoutCount.incrementAndGet();
                TimeoutException ex = new TimeoutException(
                    "引擎响应超时: requestId=" + requestId
                        + ", type=" + type
                        + ", timeout=" + effectiveTimeout);
                entry.future().completeExceptionally(ex);
                log.warn("引擎响应超时: requestId={}, sessionId={}, type={}, timeout={}",
                    requestId, sessionId, type, effectiveTimeout);
            }
        }, effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

        // 若已存在同名 requestId，先失败旧的
        PendingEntry existing = pending.put(requestId,
            new PendingEntry(sessionId, type, future, timeoutTask));
        if (existing != null) {
            existing.timeoutTask().cancel(false);
            existing.future().completeExceptionally(
                new IllegalStateException("requestId 重复: " + requestId));
            log.warn("requestId 重复注册，旧请求已失败: {}", requestId);
        }

        return future;
    }

    // ==================== 完成 ====================

    /**
     * 完成请求。
     *
     * @return true 表示匹配到 pending 并完成；false 表示 requestId 不存在或已超时
     */
    public boolean complete(String requestId, Object result) {
        if (requestId == null) return false;

        PendingEntry entry = pending.remove(requestId);
        if (entry == null) {
            log.debug("complete 忽略：requestId 不存在或已超时: {}", requestId);
            return false;
        }
        entry.timeoutTask().cancel(false);
        entry.future().complete(result);
        completedCount.incrementAndGet();
        return true;
    }

    /**
     * 以异常完成请求。
     *
     * @return true 表示匹配到 pending
     */
    public boolean completeExceptionally(String requestId, Throwable ex) {
        if (requestId == null) return false;

        PendingEntry entry = pending.remove(requestId);
        if (entry == null) return false;

        entry.timeoutTask().cancel(false);
        entry.future().completeExceptionally(ex);
        failedCount.incrementAndGet();
        return true;
    }

    // ==================== 查询 ====================

    public boolean hasPending(String requestId) {
        return requestId != null && pending.containsKey(requestId);
    }

    public int countForSession(String sessionId) {
        if (sessionId == null) return 0;
        return (int) pending.values().stream()
            .filter(e -> sessionId.equals(e.sessionId()))
            .count();
    }

    public int totalPending() {
        return pending.size();
    }

    public Map<String, Object> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("pending", pending.size());
        s.put("completed", completedCount.get());
        s.put("timeout", timeoutCount.get());
        s.put("failed", failedCount.get());
        return s;
    }

    // ==================== 失败批量 ====================

    /**
     * 引擎断线时，该会话所有 pending 请求立即失败。
     */
    @EventListener
    public void onEngineOffline(EngineOfflineEvent evt) {
        if (evt == null) return;
        failAllForSession(evt.sessionId(),
            new IllegalStateException("Unity 引擎断线: " + evt.sessionId()));
    }

    public void failAllForSession(String sessionId, Throwable ex) {
        if (sessionId == null) return;

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, PendingEntry> e : pending.entrySet()) {
            if (sessionId.equals(e.getValue().sessionId())) {
                toRemove.add(e.getKey());
            }
        }

        for (String requestId : toRemove) {
            completeExceptionally(requestId, ex);
        }

        if (!toRemove.isEmpty()) {
            log.info("会话 {} 的 {} 个待处理请求已失败: {}",
                sessionId, toRemove.size(), ex.getMessage());
        }
    }

    /**
     * 关闭调度器。Spring 容器销毁时调用。
     */
    public void shutdown() {
        scheduler.shutdownNow();
        log.info("PendingRequestManager 调度器已关闭");
    }
}

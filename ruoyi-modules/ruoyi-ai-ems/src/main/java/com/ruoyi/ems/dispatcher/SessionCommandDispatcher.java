package com.ruoyi.ems.dispatcher;


import com.ruoyi.ems.domain.dto.CommandTask;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@Component
public class SessionCommandDispatcher {

    // 每个会话绑定一个单线程执行器（使用传统线程池，兼容 JDK 17）
    private final Map<String, ExecutorService> sessionExecutors = new ConcurrentHashMap<>();
    // 存储每个会话正在运行的 Future，用于强制中断
    private final Map<String, Future<?>> runningFutures = new ConcurrentHashMap<>();

    // 关闭钩子，优雅停机
    @PreDestroy
    public void destroy() {
        sessionExecutors.values().forEach(ExecutorService::shutdownNow);
        sessionExecutors.clear();
        log.info("会话调度器已销毁，所有队列已清空");
    }

    /**
     * 提交指令到对应会话的队列
     * @param task 命令任务
     * @param processor 真正的业务执行函数（SessionService的核心方法）
     */
    public void submit(CommandTask task, Function<CommandTask, String> processor) {
        String sessionId = task.getSessionId();

        // 1. 获取或创建该会话专属的单线程执行器（传统线程池，JDK 17 兼容）
        ExecutorService executor = sessionExecutors.computeIfAbsent(sessionId, id ->
            Executors.newSingleThreadExecutor(
                // 自定义线程工厂，便于调试
                r -> {
                    Thread t = new Thread(r, "cmd-" + id + "-" + System.currentTimeMillis());
                    t.setDaemon(true); // 守护线程，防止阻止 JVM 退出
                    return t;
                }
            )
        );

        // 2. ★★★ 防积压策略（LIFO + 丢弃陈旧） ★★★
        // 如果是 ThreadPoolExecutor，直接清空阻塞队列中所有等待的旧任务（只保留最新）
        if (executor instanceof ThreadPoolExecutor tpe) {
            BlockingQueue<Runnable> queue = tpe.getQueue();
            if (!queue.isEmpty()) {
                // 新任务来了，旧任务全部作废（因为学员已经说了新的话）
                int discarded = queue.size();
                queue.clear();
                log.warn("⚡ 会话 {} 清空积压指令 {} 条，仅处理最新指令", sessionId, discarded);
            }
        }

        // 3. 包装任务，注入执行逻辑
        Runnable taskRunner = () -> {
            // ★ 执行前检查：如果当前线程已被中断（来自上一次任务的取消），重置中断状态
            if (Thread.interrupted()) {
                log.info("⏹️ 会话 {} 任务已被中断，放弃执行", sessionId);
                return;
            }

            try {
                // 调用真正的业务逻辑（包含LLM调用）
                String result = processor.apply(task);
                log.debug("✅ 会话 {} 指令执行完成: {}", sessionId, result);
            } catch (Exception e) {
                // 如果是中断异常，不记录为错误
                if (e instanceof InterruptedException || (e.getCause() instanceof InterruptedException)) {
                    log.info("⏹️ 会话 {} 任务因中断而终止", sessionId);
                } else {
                    log.error("❌ 会话 {} 任务执行异常", sessionId, e);
                }
            } finally {
                // 任务执行完成后，移除 Future 引用（防止内存泄漏）
                runningFutures.remove(sessionId);
            }
        };

        // 4. 提交任务到执行器，并存储 Future 以便后续中断
        Future<?> future = executor.submit(taskRunner);
        runningFutures.put(sessionId, future);
    }

    /**
     * 强制中断正在运行的 LLM 请求（用于语音打断场景）
     */
    public boolean interruptSession(String sessionId) {
        Future<?> future = runningFutures.get(sessionId);
        if (future != null && !future.isDone()) {
            // mayInterruptIfRunning = true，会触发线程中断标志
            boolean cancelled = future.cancel(true);
            log.info("🛑 中断会话 {} 正在执行的任务，结果: {}", sessionId, cancelled);
            return cancelled;
        }
        log.warn("⚠️ 会话 {} 没有正在执行的任务可中断", sessionId);
        return false;
    }

    /**
     * 清空会话所有资源（结束会话时调用）
     */
    public void evictSession(String sessionId) {
        ExecutorService executor = sessionExecutors.remove(sessionId);
        if (executor != null) {
            executor.shutdownNow(); // 立即中断队列中所有等待的任务
            log.info("🗑️ 已清除会话 {} 的队列资源", sessionId);
        }
        runningFutures.remove(sessionId);
    }

    /**
     * ★ 获取指定会话的队列大小（用于前端展示积压情况）
     */
    public int getQueueSize(String sessionId) {
        ExecutorService executor = sessionExecutors.get(sessionId);
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe.getQueue().size();
        }
        return 0;
    }

    /**
     * （可选）获取当前活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessionExecutors.size();
    }
}

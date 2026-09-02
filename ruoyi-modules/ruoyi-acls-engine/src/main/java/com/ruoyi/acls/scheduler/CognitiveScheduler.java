package com.ruoyi.acls.scheduler;

import com.ruoyi.acls.config.AclsEngineProperties;
import com.ruoyi.ems.intent.client.OllamaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 认知调度器 —— 单线程消费 Ollama 请求，5个组员共享
 * <p>
 * 特点：
 * <ul>
 *   <li>使用 {@link PriorityBlockingQueue} 实现优先级排队（CRITICAL 优先）</li>
 *   <li>单线程消费者，避免 Ollama 并发带来的 GPU 显存竞争</li>
 *   <li>支持超时控制，防止单个请求卡死</li>
 *   <li>异步回调，不阻塞组员的主循环</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CognitiveScheduler {

    private final OllamaClient ollamaClient;
    private final AclsEngineProperties properties;

    // 优先级队列（CRITICAL 优先）
    private final PriorityBlockingQueue<ThinkTask> pendingQueue = new PriorityBlockingQueue<>(
        20,
        (t1, t2) -> t2.getPriority().compareTo(t1.getPriority())  // 高优先级在前
    );

    // 单线程消费者
    private final ExecutorService consumer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Cognitive-Consumer");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    public void start() {
        consumer.submit(this::consumeLoop);
        log.info("🧠 认知调度器已启动，队列容量: 无界优先级队列");
    }

    /**
     * 提交思考任务（非阻塞）
     *
     * @param memberId     组员ID
     * @param systemPrompt 系统提示词（角色身份）
     * @param userPrompt   用户提示词（当前感知数据）
     * @param priority     任务优先级
     * @param callback     完成回调
     */
    public void submitThinkTask(String memberId,
                                String systemPrompt,
                                String userPrompt,
                                ThinkPriority priority,
                                ThinkCallback callback) {
        if (!properties.getCognitive().isEnabled()) {
            log.debug("认知调度已禁用，忽略 {} 的思考请求", memberId);
            return;
        }

        ThinkTask task = new ThinkTask(
            memberId,
            systemPrompt,
            userPrompt,
            priority,
            callback,
            System.currentTimeMillis()
        );
        pendingQueue.offer(task);
        log.info("📥 [{}] 提交思考任务 (优先级: {})，当前队列长度: {}",
            memberId, priority, pendingQueue.size());
    }

    /**
     * 消费者主循环（串行处理）
     */
    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ThinkTask task = pendingQueue.poll(
                    properties.getCognitive().getQueueTimeout(),
                    TimeUnit.MILLISECONDS
                );
                if (task == null) {
                    continue; // 超时未取到任务，继续等待
                }

                log.info("🧠 开始处理 [{}] 的思考请求 (优先级: {})",
                    task.getMemberId(), task.getPriority());

                // 调用 Ollama（同步阻塞，但只影响消费者线程）
                long start = System.currentTimeMillis();
                String response = ollamaClient.chat(task.getSystemPrompt(), task.getUserPrompt());
                long elapsed = System.currentTimeMillis() - start;

                log.info("✅ [{}] 思考完成，耗时 {}ms，剩余队列: {}",
                    task.getMemberId(), elapsed, pendingQueue.size());

                // 回调组员
                task.getCallback().onComplete(task.getMemberId(), response);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("认知消费者线程被中断");
                break;
            } catch (Exception e) {
                log.error("思考任务执行异常", e);
                // 异常时通知回调（如果有）
                if (!pendingQueue.isEmpty()) {
                    ThinkTask failed = pendingQueue.peek();
                    if (failed != null && failed.getCallback() != null) {
                        failed.getCallback().onError(failed.getMemberId(), e);
                    }
                }
            }
        }
    }

    /**
     * 获取当前队列长度（用于监控）
     */
    public int getQueueSize() {
        return pendingQueue.size();
    }

    /**
     * 清空队列（用于紧急清理）
     */
    public void clearQueue() {
        pendingQueue.clear();
        log.warn("🧹 认知队列已清空");
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        consumer.shutdownNow();
        log.info("🧠 认知调度器已关闭");
    }

    // ==================== 内部类 ====================

    /**
     * 思考任务实体
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ThinkTask {
        private String memberId;
        private String systemPrompt;
        private String userPrompt;
        private ThinkPriority priority;
        private ThinkCallback callback;
        private long submitTime;   // 用于超时监控
    }
}

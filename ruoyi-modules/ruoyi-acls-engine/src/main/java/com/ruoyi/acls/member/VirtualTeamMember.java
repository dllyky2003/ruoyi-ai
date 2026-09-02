package com.ruoyi.acls.member;

import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.event.TaskProgressUpdatedEvent;
import com.ruoyi.ems.event.TaskStartedEvent;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.Status;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.VrClientService;
import com.ruoyi.ems.tts.TtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 虚拟组员 —— 包装 TeamMember，赋予异步自主执行能力
 * 这是实现并发的核心：每个组员拥有独立的执行线程
 */
@Slf4j
public class VirtualTeamMember {

    // 被包装的现有 TeamMember 对象
    private final TeamMember member;
    private final ApplicationEventPublisher eventPublisher;
    private final TtsService ttsService;
    private final VrClientService vrClientService;

    // 使用虚拟线程（JDK 21+）或缓存线程池（兼容低版本）
    private final ExecutorService taskExecutor = createExecutor();

    // 当前执行的任务 Future（用于取消）
    private final AtomicReference<CompletableFuture<Void>> currentFuture = new AtomicReference<>();

    // 任务计时
    private volatile Instant taskStartInstant;
    private volatile int durationSeconds;
    private volatile String currentTaskDesc;

    public VirtualTeamMember(TeamMember member,
                             ApplicationEventPublisher eventPublisher,
                             TtsService ttsService,
                             VrClientService vrClientService) {
        this.member = member;
        this.eventPublisher = eventPublisher;
        this.ttsService = ttsService;
        this.vrClientService = vrClientService;
    }

    private static ExecutorService createExecutor() {
        try {
            // 尝试使用虚拟线程（Java 21+）
            return (ExecutorService) Executors.class
                .getMethod("newVirtualThreadPerTaskExecutor")
                .invoke(null);
        } catch (Exception e) {
            // 降级为缓存线程池
            return Executors.newCachedThreadPool();
        }
    }

    /**
     * 异步执行任务（核心方法）
     * @return CompletableFuture<Boolean>，任务完成时自动完成
     */
    public CompletableFuture<Boolean> executeTaskAsync(String taskDesc,
                                                       int durationSeconds,
                                                       CaseContext context) {
        // 1. 如果当前正在执行，先取消旧任务
        cancelCurrentTask("新任务抢占");

        // 2. 标记状态（修改底层 TeamMember）
        this.currentTaskDesc = taskDesc;
        this.durationSeconds = durationSeconds;
        member.startTask(taskDesc, durationSeconds);
        this.taskStartInstant = Instant.now();

        // 3. 发布任务开始事件
        eventPublisher.publishEvent(new TaskStartedEvent(
            this, null, member, taskDesc, durationSeconds
        ));

        // 4. 提交异步任务
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                while (true) {
                    // 检查中断标志（外部取消）
                    if (Thread.currentThread().isInterrupted()) {
                        member.failTask();
                        eventPublisher.publishEvent(new TaskCompletedEvent(
                            null, member, false, "任务被中断"
                        ));
                        return false;
                    }

                    // 计算进度
                    double progress = getCurrentProgress();
                    member.setTaskProgress(progress); // 需 TeamMember 有 setter

                    // 发布进度事件（每 500ms 或 10% 变化时）
                    eventPublisher.publishEvent(new TaskProgressUpdatedEvent(
                        this, null, member, progress
                    ));

                    // 检查是否完成
                    if (progress >= 1.0) {
                        member.completeTask();
                        String report = generateReport();
                        ttsService.synthesizeAndSend(null, report);
                        eventPublisher.publishEvent(new TaskCompletedEvent(
                            null, member, true, report
                        ));
                        return true;
                    }

                    // 检查是否超时（预计时长 + 缓冲）
                    if (isTaskTimeout()) {
                        member.failTask();
                        eventPublisher.publishEvent(new TaskCompletedEvent(
                            null, member, false, "⏰ 超时未完成"
                        ));
                        return false;
                    }

                    // 睡眠 100ms（可配置）
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                member.failTask();
                eventPublisher.publishEvent(new TaskCompletedEvent(
                    null, member, false, "被中断: " + e.getMessage()
                ));
                return false;
            } catch (Exception e) {
                log.error("任务执行异常", e);
                member.failTask();
                return false;
            }
        }, taskExecutor);

        currentFuture.set(future);
        return future;
    }

    /**
     * 取消当前任务（抢占）
     */
    public boolean cancelCurrentTask(String reason) {
        CompletableFuture<Void> old = currentFuture.getAndSet(null);
        if (old != null && !old.isDone()) {
            boolean cancelled = old.cancel(true);
            if (cancelled) {
                log.info("⛔ {} 任务被取消: {}", member.getFullName(), reason);
                member.failTask();
                // 语音提示（如果必要）
                ttsService.synthesizeAndSend(null, member.getFullName() + " 任务暂停");
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否空闲（可接受新任务）
     */
    public boolean isAvailable() {
        return member.isIdle() || member.getStatus() == Status.COMPLETED
            || member.getStatus() == Status.FAILED;
    }

    // ========== 私有辅助 ==========

    private double getCurrentProgress() {
        if (durationSeconds <= 0) return 1.0;
        long elapsed = Duration.between(taskStartInstant, Instant.now()).getSeconds();
        return Math.min(1.0, (double) elapsed / durationSeconds);
    }

    private boolean isTaskTimeout() {
        long elapsed = Duration.between(taskStartInstant, Instant.now()).getSeconds();
        return elapsed > durationSeconds + 10;  // 10秒缓冲
    }

    private String generateReport() {
        return String.format("✅ %s（%s）报告：%s 已完成",
            member.getTitle(), member.getName(),
            currentTaskDesc != null ? currentTaskDesc : "任务"
        );
    }

    // ========== 代理方法（委托给底层 TeamMember） ==========

    public String getId() { return member.getId(); }
    public String getName() { return member.getName(); }
    public String getTitle() { return member.getTitle(); }
    public String getFullName() { return member.getFullName(); }
    public Status getStatus() { return member.getStatus(); }
    public boolean hasDuty(Duty duty) { return member.hasDuty(duty); }
    public boolean isExecuting() { return member.isExecuting(); }
    public boolean isIdle() { return member.isIdle(); }
    public String getCurrentTask() { return member.getCurrentTask(); }

    // 注意：此处需给 TeamMember 增加 setTaskProgress 方法，如没有可临时用反射
    // 或修改 TeamMember 增加 transient 进度字段
}

package com.ruoyi.acls.member;

import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.VrClientService;
import com.ruoyi.ems.tts.KokoroTtsClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Getter
public abstract class VirtualTeamMember {

    protected final String id;
    protected final String sessionId;
    protected final TeamMember member;
    protected final ApplicationEventPublisher eventPublisher;
    protected final KokoroTtsClient ttsService;
    protected final VrClientService vrClientService;

    // 状态
    protected String currentTask;
    protected boolean executing = false;
    protected CompletableFuture<Void> taskFuture;

    // ★ 移除 actionRegistry 字段
    // protected final AtomicActionRegistry actionRegistry;

    /**
     * 构造器（移除 actionRegistry 参数）
     */
    public VirtualTeamMember(String sessionId,
                             TeamMember member,
                             ApplicationEventPublisher eventPublisher,
                             KokoroTtsClient ttsService,
                             VrClientService vrClientService) {
        this.id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.member = member;
        this.eventPublisher = eventPublisher;
        this.ttsService = ttsService;
        this.vrClientService = vrClientService;
    }

    // ==================== 原有方法（保留，不依赖 actionRegistry） ====================

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getName() { return member.getName(); }
    public String getTitle() { return member.getTitle(); }
    public String getFullName() { return member.getTitle() + " " + member.getName(); }

    public boolean isIdle() {
        return !executing;
    }

    public boolean isExecuting() {
        return executing;
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public boolean hasDuty(Duty duty) {
        return member.getDuties() != null && member.getDuties().contains(duty);
    }

    /**
     * 分配任务（不再依赖 actionRegistry 获取 duration）
     * duration 由调用方传入
     */
    public boolean assignTask(String task, int durationSeconds) {
        if (executing) {
            log.warn("⚠️ [{}] {} 正在执行任务: {}", sessionId, getFullName(), currentTask);
            return false;
        }
        this.currentTask = task;
        this.executing = true;
        // 模拟异步执行
        this.taskFuture = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(durationSeconds * 1000L);
                onTaskCompleted(true, task + " 完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onTaskCompleted(false, task + " 被中断");
            }
        });
        log.info("📋 [{}] {} 开始执行: {} ({}s)", sessionId, getFullName(), task, durationSeconds);
        return true;
    }

    protected void onTaskCompleted(boolean success, String report) {
        this.executing = false;
        this.currentTask = null;
        this.taskFuture = null;
        // 可以在此触发事件
        log.info("✅ [{}] {} 完成任务: {}", sessionId, getFullName(), report);
    }

    public void interrupt() {
        if (taskFuture != null && !taskFuture.isDone()) {
            taskFuture.cancel(true);
            log.info("⏹️ [{}] {} 任务被中断", sessionId, getFullName());
        }
        this.executing = false;
        this.currentTask = null;
        this.taskFuture = null;
    }

    // ==================== TTS 方法 ====================

    public void speak(String text, String voice, float speed) {
        if (ttsService != null) {
            ttsService.synthesizeAndSend(sessionId, text, voice, speed);
        }
    }

    public void speak(String text) {
        speak(text, "zf_005", 1.2f);
    }

    // ==================== VR 方法 ====================

    public void sendVrAction(String action, Map<String, Object> params, String speech, int duration) {
        if (vrClientService != null) {
            vrClientService.sendAction(sessionId, action, params, speech, duration);
        }
    }

    public boolean startTask(String task, int durationSeconds) {
        return assignTask(task, durationSeconds);
    }

    public void completeTask() {
        // 标记任务成功完成
        this.executing = false;
        this.currentTask = null;
        if (this.taskFuture != null && !this.taskFuture.isDone()) {
            this.taskFuture.cancel(true);
        }
        this.taskFuture = null;
        log.info("✅ [{}] {} 任务完成", sessionId, getFullName());
    }

    public void failTask() {
        // 标记任务失败
        this.executing = false;
        this.currentTask = null;
        if (this.taskFuture != null && !this.taskFuture.isDone()) {
            this.taskFuture.cancel(true);
        }
        this.taskFuture = null;
        log.warn("❌ [{}] {} 任务失败", sessionId, getFullName());
    }

    public void cancelCurrentTask(String reason) {
        if (executing) {
            log.info("⏹️ [{}] {} 任务被取消: {}", sessionId, getFullName(), reason);
            interrupt();
        }
    }

}

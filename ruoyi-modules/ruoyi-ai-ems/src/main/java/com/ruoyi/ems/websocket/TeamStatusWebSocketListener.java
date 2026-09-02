package com.ruoyi.ems.websocket;

import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.event.TaskProgressUpdatedEvent;
import com.ruoyi.ems.event.TaskStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamStatusWebSocketListener {

    // 缓存已处理的事件指纹，key: 指纹, value: 处理时间戳
    private final Map<String, Long> processedEventCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRE_MS = 3000; // 3秒内重复忽略

    // 生成事件指纹
    private String buildFingerprint(String sessionId, String memberName, String task, String eventType) {
        return sessionId + ":" + memberName + ":" + (task != null ? task : "null") + ":" + eventType;
    }

    // 检查是否重复
    private boolean isDuplicate(String fingerprint) {
        Long lastTime = processedEventCache.get(fingerprint);
        if (lastTime != null && System.currentTimeMillis() - lastTime < CACHE_EXPIRE_MS) {
            return true;
        }
        // 清理过期缓存（可选）
        if (processedEventCache.size() > 1000) {
            processedEventCache.entrySet().removeIf(entry ->
                System.currentTimeMillis() - entry.getValue() > CACHE_EXPIRE_MS);
        }
        return false;
    }

    @EventListener
    public void onTaskStarted(TaskStartedEvent event) {
        String sessionId = event.getSessionId();
        String memberName = event.getMember().getName();
        String task = event.getTask();
        String fingerprint = buildFingerprint(sessionId, memberName, task, "started");

        if (isDuplicate(fingerprint)) {
            log.debug("⏭️ 忽略重复 TaskStartedEvent: {}", fingerprint);
            return;
        }

        log.info("📨 [Event] onTaskStarted: sessionId={}, member={}, task={}, duration={}",
            sessionId, memberName, task, event.getDurationSeconds());

        // 检查 WebSocket 会话
        if (!AsrWebSocketHandler.isSessionOpen(sessionId)) {
            log.warn("⚠️ WebSocket 会话不存在或已关闭: sessionId={}", sessionId);
        }

        String message = String.format("📋 %s（%s）开始 %s（预计 %d 秒）",
            event.getMember().getTitle(), memberName, task, event.getDurationSeconds());

        AsrWebSocketHandler.pushTeamStatus(sessionId, "task_started", Map.of(
            "memberName", memberName,
            "title", event.getMember().getTitle(),
            "task", task,
            "duration", event.getDurationSeconds(),
            "progress", 0.0
        ));
        log.info("✅ [pushTeamStatus] task_started sent to sessionId={}", sessionId);

        AsrWebSocketHandler.pushHint(sessionId, message);
        log.info("✅ [pushHint] hint sent to sessionId={}", sessionId);

        // 记录处理时间
        processedEventCache.put(fingerprint, System.currentTimeMillis());
    }

    @EventListener
    public void onTaskProgress(TaskProgressUpdatedEvent event) {
        String sessionId = event.getSessionId();
        String memberName = event.getMember().getName();
        String task = event.getMember().getCurrentTask();
        double progress = event.getProgress();
        // 进度事件使用更细的指纹（包含进度百分比整数部分）
        String fingerprint = buildFingerprint(sessionId, memberName, task, "progress_" + (int)(progress * 100));

        if (isDuplicate(fingerprint)) {
            log.debug("⏭️ 忽略重复 TaskProgressUpdatedEvent: {}", fingerprint);
            return;
        }

        log.info("📨 [Event] onTaskProgress: sessionId={}, member={}, progress={}",
            sessionId, memberName, progress);

        if (!AsrWebSocketHandler.isSessionOpen(sessionId)) {
            log.warn("⚠️ WebSocket 会话不存在或已关闭: sessionId={}", sessionId);
        }

        AsrWebSocketHandler.pushTeamStatus(sessionId, "task_progress", Map.of(
            "memberName", memberName,
            "progress", progress,
            "task", task
        ));
        log.info("✅ [pushTeamStatus] task_progress sent to sessionId={}, progress={}", sessionId, progress);

        processedEventCache.put(fingerprint, System.currentTimeMillis());
    }

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        String sessionId = event.getSessionId();
        String memberName = event.getMember().getName();
        String task = event.getMember().getCurrentTask(); // 可能是已完成的任务
        String fingerprint = buildFingerprint(sessionId, memberName, task, "completed");

        if (isDuplicate(fingerprint)) {
            log.debug("⏭️ 忽略重复 TaskCompletedEvent: {}", fingerprint);
            return;
        }

        log.info("📨 [Event] onTaskCompleted: sessionId={}, member={}, success={}, report={}",
            sessionId, memberName, event.isSuccess(), event.getReport());

        if (!AsrWebSocketHandler.isSessionOpen(sessionId)) {
            log.warn("⚠️ WebSocket 会话不存在或已关闭: sessionId={}", sessionId);
        }

        AsrWebSocketHandler.pushTeamStatus(sessionId, "task_completed", Map.of(
            "memberName", memberName,
            "success", event.isSuccess(),
            "report", event.getReport()
        ));
        log.info("✅ [pushTeamStatus] task_completed sent to sessionId={}", sessionId);

        AsrWebSocketHandler.pushHint(sessionId, event.getReport());
        log.info("✅ [pushHint] hint sent to sessionId={}", sessionId);

        processedEventCache.put(fingerprint, System.currentTimeMillis());
    }
}

package com.ruoyi.ems.orchestrator.service;

import com.ruoyi.ems.orchestrator.model.ContinuousTaskConfig;
import com.ruoyi.ems.orchestrator.model.FatigueRotationConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ContinuousTaskScheduler {

    private final ThreadPoolTaskExecutor virtualThreadExecutor;
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> stopSignals = new ConcurrentHashMap<>();

    public ContinuousTaskScheduler() {
        this.virtualThreadExecutor = new ThreadPoolTaskExecutor();
        this.virtualThreadExecutor.setThreadNamePrefix("continuous-task-");
        this.virtualThreadExecutor.initialize();
    }

    public void start(String sessionId, ContinuousTaskConfig config, Runnable autoReporter) {
        if (!Boolean.TRUE.equals(config.getEnabled())) return;

        final int duration = config.getCycleDurationSeconds() != null ? config.getCycleDurationSeconds() : 120;
        final Integer maxLoops = config.getLoopCount();

        AtomicBoolean stopSignal = new AtomicBoolean(false);
        stopSignals.put(sessionId, stopSignal);

        Future<?> future = virtualThreadExecutor.submit(() -> {
            int cycle = 0;
            try {
                while (!stopSignal.get()) {
                    Thread.sleep(duration * 1000L);
                    cycle++;
                    log.info("🔄 [{}] 持续任务循环 #{} 完成", sessionId, cycle);

                    FatigueRotationConfig fatigue = config.getFatigueRotation();
                    if (fatigue != null && cycle % fatigue.getEveryCycles() == 0) {
                        log.info("🔄 [{}] 疲劳轮换：{} 换人", sessionId, fatigue.getRotateDuty());
                    }

                    if (config.getAutoReportIntervalSeconds() != null && autoReporter != null) {
                        autoReporter.run();
                    }

                    if (maxLoops != null && cycle >= maxLoops) {
                        log.info("✅ [{}] 持续任务已达到最大循环次数，自动停止", sessionId);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⏹️ [{}] 持续任务被中断", sessionId);
            } finally {
                // ★ 确保清理
                runningTasks.remove(sessionId);
                stopSignals.remove(sessionId);
                log.info("🧹 [{}] 持续任务资源已清理", sessionId);
            }
        });

        runningTasks.put(sessionId, future);
        log.info("▶️ [{}] 持续性任务已启动（周期 {}s）", sessionId, duration);
    }

    public boolean stop(String sessionId) {
        AtomicBoolean signal = stopSignals.get(sessionId);
        if (signal != null) {
            signal.set(true);
            Future<?> future = runningTasks.get(sessionId);
            if (future != null) {
                future.cancel(true);
                // 立即从 map 移除，避免后续检查
                runningTasks.remove(sessionId);
                stopSignals.remove(sessionId);
                log.info("⏹️ [{}] 持续性任务已停止", sessionId);
                return true;
            }
        }
        return false;
    }

    public boolean isRunning(String sessionId) {
        return runningTasks.containsKey(sessionId);
    }

    @PreDestroy
    public void shutdown() {
        runningTasks.keySet().forEach(this::stop);
        virtualThreadExecutor.shutdown();
    }
}

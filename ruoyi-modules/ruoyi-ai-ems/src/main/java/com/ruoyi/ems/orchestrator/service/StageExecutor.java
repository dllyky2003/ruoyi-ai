package com.ruoyi.ems.orchestrator.service;

import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.orchestrator.model.StageConfig;
import com.ruoyi.ems.orchestrator.model.TaskConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StageExecutor {

    private final AtomicActionDispatcher actionDispatcher;

    /**
     * 执行单个 Stage
     */
    public void execute(String sessionId, StageConfig stage, CaseContext context) throws Exception {
        if (stage == null) return;

        log.info("🎬 [{}] 执行 Stage [{}] 类型: {}", sessionId, stage.getId(), stage.getType());

        if ("parallel".equalsIgnoreCase(stage.getType())) {
            executeParallel(sessionId, stage.getTasks(), context);
        } else if ("atomic".equalsIgnoreCase(stage.getType()) && stage.getTask() != null) {
            executeAtomic(sessionId, stage.getTask(), context);
        } else {
            // sequential（默认）
            executeSequential(sessionId, stage.getTasks(), context);
        }

        // 超时控制（若 Stage 级别配置了超时）
        if (stage.getTimeoutSeconds() != null && stage.getTimeoutSeconds() > 0) {
            log.debug("Stage [{}] 超时设置为 {} 秒", stage.getId(), stage.getTimeoutSeconds());
        }
    }

    private void executeSequential(String sessionId, List<TaskConfig> tasks, CaseContext context) throws Exception {
        if (tasks == null) return;
        for (TaskConfig task : tasks) {
            executeAtomic(sessionId, task, context);
        }
    }

    /**
     * 并行执行（兼容 Java 17，使用固定大小线程池）
     */
    private void executeParallel(String sessionId, List<TaskConfig> tasks, CaseContext context) throws Exception {
        if (tasks == null || tasks.isEmpty()) return;

        int poolSize = Math.min(tasks.size(), 8);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (TaskConfig task : tasks) {
                // ★ 显式 final 副本，确保 lambda 捕获的是不可变引用
                final TaskConfig finalTask = task;
                final String finalSessionId = sessionId;
                final CaseContext finalContext = context;

                Future<?> future = executor.submit(() -> {
                    try {
                        executeAtomic(finalSessionId, finalTask, finalContext);
                    } catch (Exception e) {
                        log.error("并行任务执行失败: {}", finalTask.getRefAtomic(), e);
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("并行任务执行超时，强制中断");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    private void executeAtomic(String sessionId, TaskConfig task, CaseContext context) {
        if (task == null) return;

        actionDispatcher.dispatch(
            sessionId,
            task.getRefAtomic(),
            task.getActor(),
            task.getParams(),
            task.getSpelParams(),
            context
        );
    }
}

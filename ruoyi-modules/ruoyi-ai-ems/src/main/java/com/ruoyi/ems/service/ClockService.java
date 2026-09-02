package com.ruoyi.ems.service;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.engine.RuleMatcher;
import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.event.TaskProgressUpdatedEvent;
import com.ruoyi.ems.event.TaskStartedEvent;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.orchestrator.IntentOrchestrator;
import com.ruoyi.ems.websocket.AsrWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ClockService {

    @Autowired
    private EmsContextManager contextManager;

    @Autowired
    private IntentOrchestrator intentOrchestrator;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RuleMatcher ruleMatcher;

    @Autowired
    private YamlStateMachineLoader configLoader;

    @Autowired
    private TeamManager teamManager;

    // 进度去重缓存：key = sessionId + ":" + memberId, value = lastProgress
    private final Map<String, Double> lastProgressCache = new ConcurrentHashMap<>();

    // ============================================================
    // ★ 新增：监听任务开始事件
    // ============================================================
    @EventListener
    public void onTaskStarted(TaskStartedEvent event) {
        String sessionId = event.getSessionId();
        var member = event.getMember();
        String task = event.getTask();
        int duration = event.getDurationSeconds();

        // 推送到 WebSocket（前端可更新UI）
        Map<String, Object> data = Map.of(
            "memberName", member.getName(),
            "title", member.getTitle(),
            "task", task,
            "duration", duration,
            "progress", 0.0
        );
        AsrWebSocketHandler.pushTeamStatus(sessionId, "task_started", data);

        // 同时推文本提示
        String hint = String.format("📋 %s（%s）开始 %s（预计 %d 秒）",
            member.getTitle(), member.getName(), task, duration);
        AsrWebSocketHandler.pushHint(sessionId, hint);

        // 可选：存储到上下文
        CaseContext context = contextManager.getSnapshot(sessionId);
        if (context != null) {
            context.setVar("lastNpcTask", task, "SYSTEM");
            context.setVar("lastNpcTaskStartTime", System.currentTimeMillis(), "SYSTEM");
            contextManager.update(sessionId, context);
        }

        log.debug("📢 [NPC任务开始] {} 开始 {}", member.getFullName(), task);
    }

    // ============================================================
    // ★ 新增：监听任务进度更新事件
    // ============================================================
    @EventListener
    public void onTaskProgress(TaskProgressUpdatedEvent event) {
        String sessionId = event.getSessionId();
        var member = event.getMember();
        double progress = event.getProgress();

        // 去重：只有进度变化超过 2% 才推送，避免高频无用消息
        String key = sessionId + ":" + member.getId();
        Double lastProgress = lastProgressCache.get(key);
        if (lastProgress != null && Math.abs(progress - lastProgress) < 0.02) {
            return;
        }
        lastProgressCache.put(key, progress);

        // 推送到 WebSocket
        Map<String, Object> data = Map.of(
            "memberName", member.getName(),
            "progress", progress,
            "task", member.getCurrentTask()
        );
        AsrWebSocketHandler.pushTeamStatus(sessionId, "task_progress", data);

        log.trace("📊 [NPC进度] {} 进度 {:.0f}%", member.getName(), progress * 100);
    }

    // ============================================================
    // ★ 修改：监听任务完成事件（原有，增加 WebSocket 推送）
    // ============================================================
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        String sessionId = event.getSessionId();
        String report = event.getReport();

        log.info("📢 [NPC汇报] {}", report);

        // 将汇报存入上下文（用于LLM上下文和历史记录）
        CaseContext context = contextManager.getSnapshot(sessionId);
        if (context != null) {
            context.setVar("lastNpcReport", report, "SYSTEM");
            context.setVar("lastNpcReportTime", System.currentTimeMillis(), "SYSTEM");
            contextManager.update(sessionId, context);
        }

        // 推送到 WebSocket
        Map<String, Object> data = Map.of(
            "memberName", event.getMember().getName(),
            "success", event.isSuccess(),
            "report", report
        );
        AsrWebSocketHandler.pushTeamStatus(sessionId, "task_completed", data);
        AsrWebSocketHandler.pushHint(sessionId, report);

        // 清理进度缓存（任务已完成）
        String key = sessionId + ":" + event.getMember().getId();
        lastProgressCache.remove(key);

        // 业务逻辑处理
        if (event.isSuccess()) {
            log.debug("✅ 任务完成：{} 完成了 {}",
                event.getMember().getFullName(),
                event.getMember().getCurrentTask());
            // 可在此添加自动推进逻辑，如CPR完成自动触发检查心律等
        } else {
            log.debug("❌ 任务失败：{} 未能完成 {}",
                event.getMember().getFullName(),
                event.getMember().getCurrentTask());
        }
    }

    // ============================================================
    // 原有 tick 方法（增强）
    // ============================================================
    @Scheduled(fixedRate = 1000)
    public void tick() {
        Set<String> activeSessionIds = sessionService.getActiveSessionIds();
        for (String sessionId : activeSessionIds) {
            CaseContext context = contextManager.getSnapshot(sessionId);
            if (context == null) continue;

            // 1. 检查时钟状态
            if (!context.isClockRunning()) {
                continue;
            }

            // 2. 时间前进一秒
            context.tick();

            // 3. 更新团队状态（NPC任务进度）
            teamManager.updateStatus(sessionId);

            // 4. 获取当前配置
            String caseType = context.getCaseType();
            ResolvedCaseTypeConfig config = configLoader.getConfig(caseType);
            if (config == null) {
                log.warn("会话 {} 的案例类型 {} 未找到配置", sessionId, caseType);
                contextManager.update(sessionId, context);
                continue;
            }

            // 5. 查找系统定时触发规则
            List<TransitionRule> triggerableRules = ruleMatcher.findSystemTriggerableRules(
                config,
                context.getCurrentState(),
                context
            );

            // 6. 触发每个匹配的系统规则
            for (TransitionRule rule : triggerableRules) {
                log.info("⏰ 系统定时触发: 状态 {} 规则 {} (elapsed={}s)",
                    context.getCurrentState(),
                    rule.getDescription(),
                    context.getElapsedSeconds());

                context.resetTimer();

                IntentCommand systemCommand = IntentCommand.builder()
                    .intentCode(rule.getIntentCode() != null ? rule.getIntentCode() : "SYS_" + rule.getEvent())
                    .valid(true)
                    .confidence(1.0)
                    .rawText("[系统自动触发] " + rule.getDescription())
                    .slots(Map.of(
                        "trigger_type", "TIMEOUT",
                        "trigger_state", context.getCurrentState(),
                        "elapsed_seconds", context.getElapsedSeconds()
                    ))
                    .build();

                intentOrchestrator.processIntent(sessionId, systemCommand);
            }

            // 7. 持久化上下文
            contextManager.update(sessionId, context);
        }
    }
}

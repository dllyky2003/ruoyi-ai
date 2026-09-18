package com.ruoyi.acls.engine;

import com.ruoyi.acls.adapter.AclsIntelligentProcessor;

import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.engine.RuleMatcher;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.room.event.ClockPausedEvent;
import com.ruoyi.ems.room.event.ClockResumedEvent;
import com.ruoyi.ems.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ClockService {

    @Autowired private EmsContextManager contextManager;
    @Autowired private SessionService sessionService;
    @Autowired private RuleMatcher ruleMatcher;
    @Autowired private YamlStateMachineLoader configLoader;
    @Autowired private TeamManager teamManager;
    @Autowired private AclsIntelligentProcessor intentProcessor;
    @Autowired private ApplicationEventPublisher eventPublisher;

    /**
     * 每个会话的暂停时刻（墙上时间毫秒），用于恢复时计算暂停时长。
     * 只在该会话暂停期间存在 entry。
     */
    private final Map<String, Long> pausedAtMap = new ConcurrentHashMap<>();

    // ==================== 心跳（原有逻辑，不动） ====================

    @Scheduled(fixedRate = 1000)
    public void tick() {
        Set<String> activeSessionIds = sessionService.getActiveSessionIds();
        if (activeSessionIds.isEmpty()) {
            return;
        }

        for (String sessionId : activeSessionIds) {
            try {
                processSessionTick(sessionId);
            } catch (Exception e) {
                log.error("❌ 会话 {} 时钟滴答处理失败: {}", sessionId, e.getMessage(), e);
            }
        }
    }

    private void processSessionTick(String sessionId) {
        CaseContext context = contextManager.getSnapshot(sessionId);
        if (context == null) {
            return;
        }
        if (!context.isClockRunning()) {
            return;
        }

        contextManager.advanceClock(sessionId);
        teamManager.updateStatus(sessionId);


        refreshStateDuration(sessionId, context);
    }

    private void refreshStateDuration(String sessionId, CaseContext context) {
        Long enterTime = (Long) context.getVar("process.stateEnterTime",Long.class);
        if (enterTime == null) {
            return;
        }

        long durationSec = (System.currentTimeMillis() - enterTime) / 1000;
        Long prev = (Long) context.getVar("process.stateDuration",Long.class);
        if (prev != null && prev == durationSec) {
            return;  // 无变化不写
        }

        try {
            contextManager.batchSetVarsWithVersion(sessionId, Map.of(
                "process.stateDuration", durationSec
            ), "SYSTEM_TICK", context.getVersion());
        } catch (Exception e) {
            log.warn("刷新 stateDuration 失败: session={}, err={}", sessionId, e.getMessage());
        }
    }

    // ==================== 新增：时钟控制 ====================

    /**
     * 启动时钟。由 CaseReadyHandler 在 case_start 前调用。
     * <p>幂等：已运行则忽略。</p>
     */
    public void start(String sessionId) {
        if (sessionId == null) return;
        pausedAtMap.remove(sessionId);
        contextManager.setClockRunning(sessionId, true);
        log.info("⏱️ 时钟启动: session={}", sessionId);
    }

    /**
     * 暂停时钟。由 PauseResumeHandler 调用。
     * <p>幂等：已暂停则忽略。</p>
     */
    public void pause(String sessionId) {
        if (sessionId == null) return;
        if (isPaused(sessionId)) return;

        long now = System.currentTimeMillis();
        pausedAtMap.put(sessionId, now);
        contextManager.setClockRunning(sessionId, false);

        eventPublisher.publishEvent(new ClockPausedEvent(sessionId, now));
        log.info("⏸️ 时钟暂停: session={}", sessionId);
    }

    /**
     * 恢复时钟。由 PauseResumeHandler 调用。
     * <p>幂等：未暂停则忽略。</p>
     * <p>发布 ClockResumedEvent，携带本次暂停时长，供 TeamManager 补偿 taskStartTime。</p>
     */
    public void resume(String sessionId) {
        if (sessionId == null) return;
        if (!isPaused(sessionId)) return;

        Long pausedAt = pausedAtMap.remove(sessionId);
        long now = System.currentTimeMillis();
        long pauseDurationMs = pausedAt != null ? now - pausedAt : 0L;

        contextManager.setClockRunning(sessionId, true);
        eventPublisher.publishEvent(new ClockResumedEvent(sessionId, now, pauseDurationMs));
        log.info("▶️ 时钟恢复: session={}, 暂停时长={}ms", sessionId, pauseDurationMs);
    }

    /**
     * 停止时钟。由 CaseEndListener 在案例结束时调用。
     */
    public void stop(String sessionId) {
        if (sessionId == null) return;
        pausedAtMap.remove(sessionId);
        contextManager.setClockRunning(sessionId, false);
        log.info("⏹️ 时钟停止: session={}", sessionId);
    }

    public boolean isPaused(String sessionId) {
        return sessionId != null && pausedAtMap.containsKey(sessionId);
    }

    /**
     * 模拟已运行秒数，从 CaseContext 读取。
     * <p>因为 advanceClock 只在 running 时被调用，elapsedSeconds 自动排除暂停时间。</p>
     */
    public double elapsedSeconds(String sessionId) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        return ctx != null ? ctx.getElapsedSeconds() : 0.0;
    }

    // ==================== 系统规则（原有，不动） ====================

    private void triggerSystemRule(String sessionId, CaseContext context, TransitionRule rule) {
        String intentCode = rule.getIntentCode() != null ? rule.getIntentCode() : "SYS_" + rule.getEvent();

        log.info("⏰ [{}] 系统定时触发: 状态 {} → 规则 {} (elapsed={}s)",
            sessionId,
            context.getCurrentState(),
            rule.getDescription(),
            context.getElapsedSeconds()
        );

        IntentCommand systemCommand = IntentCommand.builder()
            .intentCode(intentCode)
            .confidence(1.0)
            .rawText("[系统自动触发] " + rule.getDescription())
            .slots(Map.of(
                "trigger_type", "TIMEOUT",
                "trigger_state", context.getCurrentState(),
                "elapsed_seconds", context.getElapsedSeconds()
            ))
            .build();

        intentProcessor.processCommand(sessionId, systemCommand);
    }
}

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

    // ==================== ★ 阶段 3：评估真实时间（墙上时间） ====================

    /**
     * 每个会话的评估真实时间起点（墙上时间毫秒）。
     * <p>在 {@link #start(String)} 首次调用时记录；重复 start 不重置，保证审计锚点稳定。</p>
     */
    private final Map<String, Long> assessmentRealStartMap = new ConcurrentHashMap<>();

    /**
     * 每个会话在 {@link #stop(String)} 时冻结的评估真实秒数。
     * <p>冻结后 {@link #assessmentRealElapsedSeconds(String)} 优先返回该值，
     * 避免会话结束后读数继续增长。</p>
     */
    private final Map<String, Double> assessmentRealFrozenMap = new ConcurrentHashMap<>();

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
        Long enterTime = (Long) context.getVar("process.stateEnterTime", Long.class);
        if (enterTime == null) {
            return;
        }

        long durationSec = (System.currentTimeMillis() - enterTime) / 1000;
        Long prev = (Long) context.getVar("process.stateDuration", Long.class);
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

    // ==================== 时钟控制 ====================

    /**
     * 启动时钟。由 CaseReadyHandler 在 case_start 前调用。
     * <p>幂等：已运行则忽略；评估真实时间起点只在首次调用时记录。</p>
     */
    public void start(String sessionId) {
        if (sessionId == null) return;
        pausedAtMap.remove(sessionId);
        contextManager.setClockRunning(sessionId, true);
        // ★ 阶段 3：评估真实时间起点。putIfAbsent 保证重复 start 不重置锚点。
        assessmentRealStartMap.putIfAbsent(sessionId, System.currentTimeMillis());
        log.info("⏱️ 时钟启动: session={}", sessionId);
    }

    /**
     * 暂停时钟。由 PauseResumeHandler 调用。
     * <p>幂等：已暂停则忽略。</p>
     * <p>注意：暂停只影响 simulated elapsed（{@link #elapsedSeconds(String)}），
     * 不影响 assessment real elapsed（{@link #assessmentRealElapsedSeconds(String)}），
     * 后者按墙上时间累计，包含暂停期间。</p>
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
     * <p>同时冻结评估真实秒数，避免后续读取继续增长。</p>
     */
    public void stop(String sessionId) {
        if (sessionId == null) return;
        pausedAtMap.remove(sessionId);

        // ★ 阶段 3：冻结评估真实时间
        Long start = assessmentRealStartMap.get(sessionId);
        if (start != null && !assessmentRealFrozenMap.containsKey(sessionId)) {
            assessmentRealFrozenMap.put(sessionId,
                (System.currentTimeMillis() - start) / 1000.0);
        }

        contextManager.setClockRunning(sessionId, false);
        log.info("⏹️ 时钟停止: session={}, assessmentReal={}s",
            sessionId, assessmentRealElapsedSeconds(sessionId));
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

    /**
     * ★ 阶段 3：评估真实时间（秒），按墙上时间累计，包含暂停时间。
     *
     * <p>口径与 {@link #elapsedSeconds(String)} 分离：
     * <ul>
     *   <li>{@code elapsedSeconds} = simulated elapsed，排除暂停；</li>
     *   <li>{@code assessmentRealElapsedSeconds} = real elapsed，包含暂停，
     *       用于 D4 评分中断与 S3 的 real time_basis。</li>
     * </ul>
     *
     * <p>优先级：已冻结值 > 起点计算 > 0。</p>
     */
    public double assessmentRealElapsedSeconds(String sessionId) {
        if (sessionId == null) return 0.0;
        Double frozen = assessmentRealFrozenMap.get(sessionId);
        if (frozen != null) return frozen;
        Long start = assessmentRealStartMap.get(sessionId);
        if (start == null) return 0.0;
        return (System.currentTimeMillis() - start) / 1000.0;
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

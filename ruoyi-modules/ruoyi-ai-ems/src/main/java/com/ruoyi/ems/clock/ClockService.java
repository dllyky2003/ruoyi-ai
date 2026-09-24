package com.ruoyi.ems.clock;

import com.ruoyi.ems.clock.contract.ClockEvent;
import com.ruoyi.ems.clock.contract.ClockEventListener;
import com.ruoyi.ems.clock.contract.ClockEventType;
import com.ruoyi.ems.clock.contract.ClockType;
import com.ruoyi.ems.clock.event.SimulationTickEvent;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.room.event.ClockPausedEvent;
import com.ruoyi.ems.room.event.ClockResumedEvent;
import com.ruoyi.ems.service.SessionService;
import com.ruoyi.ems.spi.ExceptionTickProcessor;
import com.ruoyi.ems.spi.IntelligentCommandProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class ClockService {

    @Autowired private EmsContextManager contextManager;
    @Autowired private SessionService sessionService;
    @Autowired private TeamManager teamManager;

    /**
     * 意图处理器（SPI 接口）。
     * <p>由 acls-engine 的 {@code AclsIntelligentProcessor} 实现；
     * 未装配时（如仅启动 ems 做单测）为 null，调用处已做空值降级。</p>
     */
    @Autowired(required = false)
    private IntelligentCommandProcessor intentProcessor;

    @Autowired private ApplicationEventPublisher eventPublisher;

    /**
     * 定时异常评估 SPI。
     * <p>由 acls-engine 的 {@code AclsCommandEngine} 实现；
     * 未装配时为 null，tick 时静默跳过。</p>
     */
    @Autowired(required = false)
    private ExceptionTickProcessor exceptionTickProcessor;

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

    // ==================== ★ 螺旋 4：5 类时钟契约 ====================

    /**
     * ★ 螺旋 4 G4-07：按 {@link ClockType} 分组的订阅者。
     * <p>不影响现有 {@link SimulationTickEvent} 发布路径。
     * 使用 CopyOnWriteArrayList 保证并发安全，订阅行为低频，写放大可忽略。</p>
     */
    private final Map<ClockType, CopyOnWriteArrayList<ClockEventListener>> listeners
        = new ConcurrentHashMap<>();

    // ==================== 心跳 ====================

    /**
     * 每秒定时心跳。
     * <p>约定：</p>
     * <ul>
     *   <li>无活跃会话时，不打印任何 INFO 日志，避免刷屏；仅在 DEBUG 级别输出。</li>
     *   <li>逐会话处理时，单会话异常不影响其他会话。</li>
     * </ul>
     */
    @Scheduled(fixedRate = 1000)
    public void tick() {
        Set<String> activeSessionIds = sessionService.getActiveSessionIds();

        if (log.isDebugEnabled()) {
            log.debug("🕐 ClockService.tick: activeSessionIds.size={}",
                activeSessionIds == null ? 0 : activeSessionIds.size());
        }

        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
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
        if (context == null || !context.isClockRunning()) {
            return;
        }

        contextManager.advanceClock(sessionId);
        teamManager.updateStatus(sessionId);

        // ★ 心跳写入：走"不递增版本号"的专用方法
        contextManager.setInternalVarNoVersion(
            sessionId,
            "process.clockTickAt",
            System.currentTimeMillis()
        );

        refreshStateDuration(sessionId, context);

        if (exceptionTickProcessor != null) {
            try {
                exceptionTickProcessor.tickExceptionsForSession(sessionId);
            } catch (Exception e) {
                log.warn("定时异常评估调用失败: session={}, err={}", sessionId, e.getMessage());
            }
        }

        // ★ 螺旋 2：发布仿真时钟滴答事件（同步派发）
        //   放在最后，保证 elapsedSeconds / stateDuration 已是最新值。
        CaseContext latest = contextManager.getSnapshot(sessionId);
        if (latest != null) {
            eventPublisher.publishEvent(new SimulationTickEvent(
                sessionId,
                System.currentTimeMillis(),
                latest.getElapsedSeconds(),
                latest.getVersion()
            ));

            // ★ 螺旋 4 G4-07：发布 SESSION / PHYSIOLOGY / DRUG 三类契约事件
            long now = System.currentTimeMillis();
            double elapsed = latest.getElapsedSeconds();

            // -------- SESSION/TICK：payload 值均为基本类型，Map.of 安全 --------
            publish(new ClockEvent(
                ClockType.SESSION, sessionId, "session-tick",
                ClockEventType.TICK, now, elapsed,
                Map.of("clockRunning", latest.isClockRunning())
            ));

            // -------- PHYSIOLOGY/TICK：stateDuration 可能为 null，改用 HashMap --------
            //   refreshStateDuration 在 process.stateEnterTime 为 null 时直接返回，
            //   此时 process.stateDuration 未写入 variables；若 _variables.json 也无
            //   默认值，getVar 返回 null，Map.of 会抛 NPE。故此处用 HashMap 允许 null。
            Map<String, Object> physPayload = new HashMap<>();
            physPayload.put("stateDuration", latest.getVar("process.stateDuration", Long.class));
            physPayload.put("currentState",  latest.getCurrentState());

            publish(new ClockEvent(
                ClockType.PHYSIOLOGY, sessionId, "physiology-tick",
                ClockEventType.TICK, now, elapsed, physPayload
            ));

            // -------- DRUG/TICK：adenosine 是嵌套 Map 变量，不能拼点号路径 --------
            //   variables 是扁平 Map，key 就是 "intervention.adenosine"（整体 Map）。
            //   不存在 "intervention.adenosine.lastTime" 这个 key，拼点号必然返回 null。
            Object adenosineObj = latest.getVar("intervention.adenosine", Object.class);

            Long adenosineLastTime = null;
            if (adenosineObj instanceof Map<?, ?> map) {
                Object lastTime = map.get("lastTime");
                if (lastTime instanceof Number n) {
                    adenosineLastTime = n.longValue();
                }
            }

            Map<String, Object> drugPayload = new HashMap<>();
            drugPayload.put("adenosineLastTime", adenosineLastTime);

            publish(new ClockEvent(
                ClockType.DRUG, sessionId, "drug-tick",
                ClockEventType.TICK, now, elapsed, drugPayload
            ));
        }
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
     * 启动时钟。
     *
     * <p><b>唯一调用点：</b>{@code EngineSessionControlHandler.handleStartSession}，
     * 即客户端通过 WebSocket 发来 {@code start_session} 之后。</p>
     *
     * <p><b>禁止调用点：</b>{@code CaseReadyHandler} 及任何处理 {@code case_ready} / {@code case_start}
     * 的路径。{@code case_ready} 只表示引擎就绪，不是案例开始，不得启动时钟、
     * 不得初始化 {@code process.lastInterventionTime}、不得启动 ASR。</p>
     *
     * <p>幂等：已运行则忽略；评估真实时间起点只在首次调用时记录。</p>
     */
    public void start(String sessionId) {
        if (sessionId == null) return;
        pausedAtMap.remove(sessionId);
        contextManager.setClockRunning(sessionId, true);
        // ★ 阶段 3：评估真实时间起点。putIfAbsent 保证重复 start 不重置锚点。
        assessmentRealStartMap.putIfAbsent(sessionId, System.currentTimeMillis());
        initializeLastInterventionTime(sessionId);
        log.info("⏱️ 时钟启动: session={}", sessionId);

        // ★ 螺旋 4 G4-07：生命周期事件
        publish(new ClockEvent(
            ClockType.SESSION, sessionId, "session-lifecycle",
            ClockEventType.START, System.currentTimeMillis(),
            elapsedSeconds(sessionId), Map.of()
        ));
    }

    /**
     * ★ 初始化 process.lastInterventionTime（若未设置）。
     *
     * <p>该变量是 {@code process.timeElapsedSinceLastIntervention} 的依赖；
     * 不初始化会导致延时规则永远无法触发。</p>
     *
     * <p>语义：案例“正式开始”（客户端 start_session）时刻的时间锚点。
     * 必须先有本锚点，tick 才能把 {@code timeElapsedSinceLastIntervention}
     * 计算为正数，延时规则才会在阈值后触发。</p>
     */
    private void initializeLastInterventionTime(String sessionId) {
        try {
            CaseContext ctx = contextManager.getSnapshot(sessionId);
            if (ctx == null) return;

            Object existing = ctx.getVar("process.lastInterventionTime", Object.class);
            if (existing != null) {
                return;   // 已有值，不覆盖
            }

            boolean ok = contextManager.batchSetVarsWithVersion(
                sessionId,
                Map.of("process.lastInterventionTime", System.currentTimeMillis()),
                "CLOCK_START",
                ctx.getVersion());

            if (ok) {
                log.info("⏱️ 初始化 lastInterventionTime: session={}", sessionId);
            } else {
                // 版本冲突：重试一次
                CaseContext latest = contextManager.getSnapshot(sessionId);
                if (latest != null) {
                    contextManager.batchSetVarsWithVersion(
                        sessionId,
                        Map.of("process.lastInterventionTime", System.currentTimeMillis()),
                        "CLOCK_START_RETRY",
                        latest.getVersion());
                }
            }
        } catch (Exception e) {
            log.warn("初始化 lastInterventionTime 失败: session={}, err={}",
                sessionId, e.getMessage());
        }
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

        // ★ 螺旋 4 G4-07：生命周期事件
        publish(new ClockEvent(
            ClockType.SESSION, sessionId, "session-lifecycle",
            ClockEventType.PAUSE, now,
            elapsedSeconds(sessionId), Map.of()
        ));
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

        // ★ 螺旋 4 G4-07：生命周期事件
        publish(new ClockEvent(
            ClockType.SESSION, sessionId, "session-lifecycle",
            ClockEventType.RESUME, now,
            elapsedSeconds(sessionId),
            Map.of("pauseDurationMs", pauseDurationMs)
        ));
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

        // ★ 螺旋 4 G4-07：生命周期事件（CANCEL）
        publish(new ClockEvent(
            ClockType.SESSION, sessionId, "session-lifecycle",
            ClockEventType.CANCEL, System.currentTimeMillis(),
            assessmentRealElapsedSeconds(sessionId), Map.of()
        ));
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

    // ==================== ★ 螺旋 4 G4-07：5 类时钟契约 ====================

    /**
     * 订阅某类时钟事件。
     * <p>幂等：同一 listener 对同一 type 不会重复加入。</p>
     */
    public void subscribe(ClockType type, ClockEventListener listener) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
            .addIfAbsent(listener);
    }

    public void unsubscribe(ClockType type, ClockEventListener listener) {
        if (type == null || listener == null) return;
        List<ClockEventListener> ls = listeners.get(type);
        if (ls != null) ls.remove(listener);
    }

    /**
     * 发布时钟事件。
     * <p>单订阅者异常不影响其他订阅者。</p>
     */
    public void publish(ClockEvent event) {
        if (event == null || event.type() == null) return;
        List<ClockEventListener> ls = listeners.get(event.type());
        if (ls == null || ls.isEmpty()) return;
        for (ClockEventListener l : ls) {
            try {
                l.onClockEvent(event);
            } catch (Exception e) {
                log.warn("时钟事件监听失败: type={}, session={}, err={}",
                    event.type(), event.sessionId(), e.getMessage());
            }
        }
    }

    // ==================== 系统规则 ====================

    /**
     * 系统定时规则触发（当前未被调用，保留供后续启用）。
     *
     * <p>走 SPI 接口 {@link IntelligentCommandProcessor}，不直接依赖 acls-engine。
     * 未装配时降级为日志，不抛异常。</p>
     */
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

        if (intentProcessor != null) {
            intentProcessor.processCommand(sessionId, systemCommand);
        } else {
            log.warn("intentProcessor 未装配，系统规则仅记录不派发: session={}, intent={}",
                sessionId, intentCode);
        }
    }
}

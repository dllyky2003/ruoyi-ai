package com.ruoyi.ems.clock;

import com.ruoyi.ems.clock.contract.ClockEvent;
import com.ruoyi.ems.clock.contract.ClockEventType;
import com.ruoyi.ems.clock.contract.ClockType;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ★ 螺旋 4 G4-06：药物时钟服务。
 *
 * <p>职责：
 * <ul>
 *   <li>订阅 {@link ClockType#DRUG} 事件；</li>
 *   <li>每次 DRUG/tick 时检查腺苷观察窗是否到期，到期发 EXPIRE 事件；</li>
 *   <li>对外暴露 {@link #isWithinAdenosineWindow(String)} 供 Tutor 层 WARN 使用。</li>
 * </ul>
 *
 * <p><b>变量选择：</b>读 {@code intervention.adenosine.lastTime}，
 * 而不是 {@code intervention.lastDrugTime} / {@code intervention.lastDrug}。
 * 原因：后者会被其他药物污染（如 LLM 兜底写入二线药物名），
 * 只有 {@code intervention.adenosine.lastTime} 是腺苷专属。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DrugClockService {

    /** 腺苷二次推药观察窗（毫秒）。 */
    public static final long ADENOSINE_RECHECK_WINDOW_MS = 60_000L;

    private final ClockService clockService;
    private final EmsContextManager contextManager;

    /** 记录每个会话上次腺苷窗口是否已 EXPIRE，避免重复发事件。 */
    private final Map<String, Long> expiredForAdenosineAtMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        clockService.subscribe(ClockType.DRUG, this::onDrugEvent);
        log.info("💊 DrugClockService 已订阅 ClockType.DRUG");
    }

    /** 判断该会话当前是否落在腺苷 60s 观察窗内。 */
    public boolean isWithinAdenosineWindow(String sessionId) {
        if (sessionId == null) return false;
        Long lastTime = getAdenosineLastTime(sessionId);
        if (lastTime == null) return false;
        long elapsed = System.currentTimeMillis() - lastTime;
        return elapsed > 0 && elapsed < ADENOSINE_RECHECK_WINDOW_MS;
    }

    private Long getAdenosineLastTime(String sessionId) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) return null;

        // ★ variables 里 key 是 "intervention.adenosine"（整体 Map），
        //   不存在 "intervention.adenosine.lastTime" 这个 key，不能拼点号路径。
        Object adenosineObj = ctx.getVar("intervention.adenosine", Object.class);
        if (!(adenosineObj instanceof Map<?, ?> map)) {
            return null;
        }
        Object lastTime = map.get("lastTime");
        if (lastTime instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * 处理 DRUG 类事件。
     * <p>本步只响应 TICK，检查 60s 窗口是否到期并发 EXPIRE。</p>
     */
    private void onDrugEvent(ClockEvent e) {
        if (e.eventType() != ClockEventType.TICK) return;

        String sessionId = e.sessionId();
        Long lastTime = getAdenosineLastTime(sessionId);

//        log.info("💊 DrugClockService 收到 DRUG/TICK: session={}, adenosineLastTime={}",
//            sessionId, lastTime);

        if (lastTime == null) {
            expiredForAdenosineAtMap.remove(sessionId);
            return;
        }

        // ★ 计算距上次给药已过去多少毫秒
        long elapsed = System.currentTimeMillis() - lastTime;

        // 仍在 60s 观察窗内：什么都不做，等待窗口到期
        if (elapsed < ADENOSINE_RECHECK_WINDOW_MS) {
            return;
        }

        // 已到期：检查"这一剂"是否已经发过 EXPIRE（用 lastTime 做去重 key）
        Long prev = expiredForAdenosineAtMap.get(sessionId);
        if (lastTime.equals(prev)) {
            return;   // 同一剂已发过 EXPIRE，避免重复
        }

        // 发一次 EXPIRE，并记录已对"这一剂"发过
        expiredForAdenosineAtMap.put(sessionId, lastTime);
        clockService.publish(new ClockEvent(
            ClockType.DRUG, sessionId, "drug-adenosine-window",
            ClockEventType.EXPIRE, System.currentTimeMillis(),
            e.elapsedSeconds(),
            Map.of(
                "drug", "adenosine",
                "windowMs", ADENOSINE_RECHECK_WINDOW_MS,
                "elapsedSinceLastDoseMs", elapsed
            )
        ));
        log.info("💊 腺苷观察窗到期: session={}, lastTime={}, elapsed={}ms",
            sessionId, lastTime, elapsed);
    }
}

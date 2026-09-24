package com.ruoyi.ems.clock;

import com.ruoyi.ems.clock.contract.ClockEvent;
import com.ruoyi.ems.clock.contract.ClockType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ★ 螺旋 4 G4-07 验证订阅者。
 *
 * <p>订阅全部 5 类时钟，把事件打到日志，用于：
 * <ul>
 *   <li>验证 {@link ClockService#subscribe} / {@link ClockService#publish} 生效；</li>
 *   <li>验证 SESSION / PHYSIOLOGY 两类生命周期与 tick 事件；</li>
 *   <li>为后续 DrugClock / AssessmentClock / TeachingClock 提供调试锚点。</li>
 * </ul>
 *
 * <p>G4-07 验收通过后，可把 {@code log.info} 降级为 {@code log.debug}，
 * 或直接删除本类（生产环境不需要刷日志）。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ClockEventLogger {

    private final ClockService clockService;

    @PostConstruct
    public void init() {
        for (ClockType t : ClockType.values()) {
            clockService.subscribe(t, this::onEvent);
        }
        log.info("🧪 ClockEventLogger 已订阅全部 5 类时钟");
    }

    private void onEvent(ClockEvent e) {
        log.info("🧪 [ClockEvent] type={} session={} clock={} event={} elapsed={}s payload={}",
            e.type(),
            e.sessionId(),
            e.clockId(),
            e.eventType(),
            String.format("%.1f", e.elapsedSeconds()),
            e.payload());
    }
}

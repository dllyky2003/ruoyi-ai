package com.ruoyi.acls.tutor.integration;

import com.ruoyi.ems.spi.TemporalEventSink;
import com.ruoyi.ems.spi.dto.ClinicalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 时间线事件接收 SPI 的 Tutor 实现（最小版本）。
 *
 * <p>本步只记录 INFO 日志，不注册/取消任何时间线。后续步骤会在这里接入
 * TemporalEventScheduler。</p>
 */
@Slf4j
@Component
@Primary
public class TutorTemporalEventSink implements TemporalEventSink {

    @Override
    public void onStateEntered(String sessionId, String stateId, long stateRevision) {
        log.info("[Tutor][StateEntered] session={}, state={}, rev={}",
            sessionId, stateId, stateRevision);
    }

    @Override
    public void onStateExited(String sessionId, String stateId, long stateRevision) {
        log.info("[Tutor][StateExited] session={}, state={}, rev={}",
            sessionId, stateId, stateRevision);
    }

    @Override
    public void onEventEmitted(ClinicalEvent event) {
        if (event == null) return;
        log.info("[Tutor][EventEmitted] session={}, type={}, source={}, rev={}",
            event.getSessionId(), event.getType(), event.getSource(), event.getStateRevision());
    }
}

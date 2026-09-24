package com.ruoyi.acls.test;

import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.room.event.ClockPausedEvent;
import com.ruoyi.ems.room.event.ClockResumedEvent;
import com.ruoyi.ems.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ClockServiceTest {

    private ClockService clockService;
    private EmsContextManager contextManager;
    private ApplicationEventPublisher publisher;

    private static final String SID = "s-001";

    @BeforeEach
    void setup() {
        clockService = new ClockService();

        contextManager = mock(EmsContextManager.class);
        publisher = mock(ApplicationEventPublisher.class);

        ReflectionTestUtils.setField(clockService, "contextManager", contextManager);
        ReflectionTestUtils.setField(clockService, "eventPublisher", publisher);
        ReflectionTestUtils.setField(clockService, "sessionService", mock(SessionService.class));
        ReflectionTestUtils.setField(clockService, "teamManager", mock(TeamManager.class));
    }

    @Test
    void startSetsRunning() {
        clockService.start(SID);
        verify(contextManager).setClockRunning(SID, true);
        assertFalse(clockService.isPaused(SID));
    }

    @Test
    void pauseStopsClockAndPublishesEvent() {
        clockService.pause(SID);
        verify(contextManager).setClockRunning(SID, false);
        verify(publisher).publishEvent(any(ClockPausedEvent.class));
        assertTrue(clockService.isPaused(SID));
    }

    @Test
    void pauseIsIdempotent() {
        clockService.pause(SID);
        clockService.pause(SID);
        verify(contextManager, times(1)).setClockRunning(SID, false);
        verify(publisher, times(1)).publishEvent(any(ClockPausedEvent.class));
    }

    @Test
    void resumeStartsClockAndPublishesEvent() throws Exception {
        clockService.pause(SID);
        Thread.sleep(50);
        clockService.resume(SID);

        verify(contextManager).setClockRunning(SID, true);
        ArgumentCaptor<ClockResumedEvent> captor = ArgumentCaptor.forClass(ClockResumedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertFalse(clockService.isPaused(SID));

        ClockResumedEvent evt = captor.getValue();
        assertEquals(SID, evt.sessionId());
        assertTrue(evt.pauseDurationMs() >= 50);
    }

    @Test
    void resumeIsIdempotentWhenNotPaused() {
        clockService.resume(SID);
        verify(contextManager, never()).setClockRunning(eq(SID), anyBoolean());
    }

    @Test
    void stopClearsPausedState() {
        clockService.pause(SID);
        clockService.stop(SID);
        assertFalse(clockService.isPaused(SID));
    }

    @Test
    void isPausedFalseForUnknownSession() {
        assertFalse(clockService.isPaused("unknown"));
    }

    @Test
    void isPausedNullSafe() {
        assertFalse(clockService.isPaused(null));
    }

    @Test
    void elapsedSecondsReadsFromContext() {
        CaseContext ctx = new CaseContext(SID);
        ctx.setElapsedSeconds(42);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        assertEquals(42.0, clockService.elapsedSeconds(SID), 0.001);
    }

    @Test
    void elapsedSecondsZeroWhenNoContext() {
        when(contextManager.getSnapshot(SID)).thenReturn(null);
        assertEquals(0.0, clockService.elapsedSeconds(SID), 0.001);
    }
}

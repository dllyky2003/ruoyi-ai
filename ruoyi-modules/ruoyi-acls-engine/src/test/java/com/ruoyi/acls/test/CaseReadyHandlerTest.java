package com.ruoyi.acls.test;

import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.acls.room.CaseReadyHandler;
import com.ruoyi.ems.domain.entity.EmsSession;
import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.ws.WsMessageContext;
import com.ruoyi.ems.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CaseReadyHandlerTest {

    private RoomRegistry roomRegistry;
    private RoomBroadcaster broadcaster;
    private SessionService sessionService;
    private ClockService clockService;
    private CaseReadyHandler handler;

    private static final String ROOM_ID = "room-001";
    private static final String EMS_ID = "ems-001";
    private static final String ENGINE_ID = "engine-1";

    @BeforeEach
    void setup() {
        roomRegistry = mock(RoomRegistry.class);
        broadcaster = mock(RoomBroadcaster.class);
        sessionService = mock(SessionService.class);
        clockService = mock(ClockService.class);

        handler = new CaseReadyHandler(roomRegistry, broadcaster, sessionService, clockService);
    }

    // ==================== 工具 ====================

    private SessionRoom readyRoom() {
        SessionRoom room = new SessionRoom(ROOM_ID, "stable_narrow", "3MAN");
        room.join(new ClientConnection(ENGINE_ID, Role.ENGINE, null, null));
        room.join(new ClientConnection("speaker-1", Role.SPEAKER, null, null));
        room.attachEmsSession(EMS_ID);
        return room;
    }

    private WsMessageContext ctx(Role role) {
        return new WsMessageContext(ROOM_ID, ENGINE_ID, role,
            Map.of("type", "case_ready", "data", Map.of()));
    }

    // ==================== 测试 ====================

    @Test
    void supportsOnlyCaseReady() {
        assertTrue(handler.supports("case_ready"));
        assertFalse(handler.supports("case_start"));
        assertFalse(handler.supports("ping"));
    }

    @Test
    void nonEngineRoleIgnored() {
        handler.handle(ctx(Role.SPEAKER));

        verifyNoInteractions(roomRegistry, sessionService, clockService);
    }

    @Test
    void noEmsSessionWarnsAndReturns() {
        SessionRoom room = new SessionRoom(ROOM_ID, "case", "3MAN");
        room.join(new ClientConnection(ENGINE_ID, Role.ENGINE, null, null));
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE));

        verifyNoInteractions(sessionService, clockService);
    }

    @Test
    void alreadyStartedIsIdempotent() {
        SessionRoom room = readyRoom();
        room.markStarted();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE));

        verifyNoInteractions(clockService);
        verify(sessionService, never()).startSession(any());
    }

    @Test
    void happyPathStartsCase() {
        SessionRoom room = readyRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        EmsSession ems = new EmsSession();
        ems.setId(EMS_ID);
        when(sessionService.getSessionById(EMS_ID)).thenReturn(ems);

        handler.handle(ctx(Role.ENGINE));

        verify(clockService).start(EMS_ID);
        verify(sessionService).startSession(ems);
        verify(broadcaster).broadcast(eq(ROOM_ID),
            argThat(msg -> "case_start".equals(msg.get("type"))));

        assertTrue(room.isStarted());
    }

    @Test
    void startFailureBroadcastsCaseStartFailed() {
        SessionRoom room = readyRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        EmsSession ems = new EmsSession();
        ems.setId(EMS_ID);
        when(sessionService.getSessionById(EMS_ID)).thenReturn(ems);
        doThrow(new RuntimeException("ASR failure"))
            .when(sessionService).startSession(any());

        handler.handle(ctx(Role.ENGINE));

        verify(broadcaster).broadcast(eq(ROOM_ID),
            argThat(msg -> "case_start_failed".equals(msg.get("type"))));
    }
}

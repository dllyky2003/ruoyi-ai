package com.ruoyi.acls.test;


import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.acls.room.PauseResumeHandler;
import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.ws.WsMessageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PauseResumeHandlerTest {

    private RoomRegistry roomRegistry;
    private RoomBroadcaster broadcaster;
    private ClockService clockService;
    private PauseResumeHandler handler;

    private static final String ROOM_ID = "room-001";
    private static final String EMS_ID = "ems-001";
    private static final String ENGINE_ID = "engine-1";

    @BeforeEach
    void setup() {
        roomRegistry = mock(RoomRegistry.class);
        broadcaster = mock(RoomBroadcaster.class);
        clockService = mock(ClockService.class);

        handler = new PauseResumeHandler(roomRegistry, broadcaster, clockService);
    }

    // ==================== 工具 ====================

    /**
     * 构造一个已启动的房间：
     * - engine + speaker 都在
     * - 已关联 emsSessionId
     * - 已 markStarted
     */
    private SessionRoom runningRoom() {
        SessionRoom room = new SessionRoom(ROOM_ID, "stable_narrow", "3MAN");
        room.join(new ClientConnection(ENGINE_ID, Role.ENGINE, null, null));
        room.join(new ClientConnection("speaker-1", Role.SPEAKER, null, null));
        room.attachEmsSession(EMS_ID);
        room.markStarted();
        return room;
    }

    private WsMessageContext ctx(Role role, String type) {
        return new WsMessageContext(ROOM_ID, ENGINE_ID, role,
            Map.of("type", type, "data", Map.of()));
    }

    // ==================== supports ====================

    @Test
    void supportsBothTypes() {
        assertTrue(handler.supports("pause_case_request"));
        assertTrue(handler.supports("resume_case_request"));
        assertFalse(handler.supports("case_ready"));
        assertFalse(handler.supports("ping"));
    }

    // ==================== 权限 ====================

    @Test
    void viewerRejected() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.VIEWER, "pause_case_request"));

        verifyNoInteractions(clockService);
        verify(broadcaster, never()).broadcast(any(), any());
    }

    // ==================== 房间未启动 ====================

    @Test
    void pauseNotStartedRoomIgnored() {
        SessionRoom room = new SessionRoom(ROOM_ID, "case", "3MAN");
        room.join(new ClientConnection(ENGINE_ID, Role.ENGINE, null, null));
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "pause_case_request"));

        verifyNoInteractions(clockService);
    }

    @Test
    void terminalRoomIgnored() {
        SessionRoom room = runningRoom();
        room.complete();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "pause_case_request"));

        verifyNoInteractions(clockService);
    }

    // ==================== pause ====================

    @Test
    void pauseHappyPath() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "pause_case_request"));

        verify(clockService).pause(EMS_ID);
        assertTrue(room.isPaused());
        verify(broadcaster).broadcast(eq(ROOM_ID),
            argThat(msg -> "case_paused".equals(msg.get("type"))));
    }

    @Test
    void pauseIsIdempotent() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "pause_case_request"));
        handler.handle(ctx(Role.ENGINE, "pause_case_request"));

        verify(clockService, times(1)).pause(EMS_ID);
        verify(broadcaster, times(1)).broadcast(eq(ROOM_ID),
            argThat(msg -> "case_paused".equals(msg.get("type"))));
    }

    // ==================== resume ====================

    @Test
    void resumeHappyPath() {
        SessionRoom room = runningRoom();
        room.markPaused();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "resume_case_request"));

        verify(clockService).resume(EMS_ID);
        assertFalse(room.isPaused());
        verify(broadcaster).broadcast(eq(ROOM_ID),
            argThat(msg -> "case_resumed".equals(msg.get("type"))));
    }

    @Test
    void resumeNotPausedIsNoop() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "resume_case_request"));

        verify(clockService, never()).resume(any());
        verify(broadcaster, never()).broadcast(any(), any());
    }

    @Test
    void resumeIsIdempotent() {
        SessionRoom room = runningRoom();
        room.markPaused();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "resume_case_request"));
        handler.handle(ctx(Role.ENGINE, "resume_case_request"));

        verify(clockService, times(1)).resume(EMS_ID);
    }

    // ==================== speaker / instructor 也可控 ====================

    @Test
    void speakerCanPause() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.SPEAKER, "pause_case_request"));

        verify(clockService).pause(EMS_ID);
    }

    @Test
    void instructorCanResume() {
        SessionRoom room = runningRoom();
        room.markPaused();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.INSTRUCTOR, "resume_case_request"));

        verify(clockService).resume(EMS_ID);
    }

    // ==================== 广播 payload ====================

    @Test
    void pauseBroadcastPayloadContainsEmsAndRole() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, "pause_case_request"));

        verify(broadcaster).broadcast(eq(ROOM_ID), argThat(msg -> {
            if (!"case_paused".equals(msg.get("type"))) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.get("data");
            return EMS_ID.equals(data.get("emsSessionId"))
                && "engine".equals(data.get("requestedBy"));
        }));
    }
}

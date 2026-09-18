package com.ruoyi.ems.room;

import com.ruoyi.ems.domain.entity.EmsSession;

import com.ruoyi.ems.npc.TeamSize;
import com.ruoyi.ems.room.dto.CaseConfigDto;
import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.ws.StartCaseHandler;
import com.ruoyi.ems.room.ws.WsMessageContext;
import com.ruoyi.ems.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StartCaseHandlerTest {

    private RoomRegistry roomRegistry;
    private SessionService sessionService;
    private CaseConfigAssembler assembler;
    private RoomBroadcaster broadcaster;
    private StartCaseHandler handler;

    private static final String ROOM_ID = "room-001";
    private static final String EMS_ID = "stable_narrow";
    private static final String CLIENT_ID = "client-001";

    @BeforeEach
    void setup() {
        roomRegistry = mock(RoomRegistry.class);
        sessionService = mock(SessionService.class);
        assembler = mock(CaseConfigAssembler.class);
        broadcaster = mock(RoomBroadcaster.class);

        handler = new StartCaseHandler(roomRegistry, sessionService, assembler, broadcaster);
    }

    // ==================== 工具 ====================

    private SessionRoom activeRoom() {
        SessionRoom room = new SessionRoom(ROOM_ID, "stable_narrow", "3MAN");
        room.join(new ClientConnection("e-1", Role.ENGINE, null, null));
        room.join(new ClientConnection("s-1", Role.SPEAKER, null, null));
        return room;   // 状态自动到 ACTIVE
    }

    private Map<String, Object> payload(String caseId, Integer teamSize) {
        Map<String, Object> m = new HashMap<>();
        if (caseId != null) m.put("caseId", caseId);
        if (teamSize != null) m.put("teamSize", teamSize);
        m.put("traineeName", "张三");
        m.put("mode", "EXAM");
        return m;
    }

    private WsMessageContext ctx(Role role, Map<String, Object> data) {
        return new WsMessageContext(ROOM_ID, CLIENT_ID, role,
            Map.of("type", "start_case_request", "data", data));
    }

    // ==================== supports ====================

    @Test
    void supportsOnlyStartCaseRequest() {
        org.junit.jupiter.api.Assertions.assertTrue(handler.supports("start_case_request"));
        org.junit.jupiter.api.Assertions.assertFalse(handler.supports("case_ready"));
        org.junit.jupiter.api.Assertions.assertFalse(handler.supports("ping"));
    }

    // ==================== 权限拒绝 ====================

    @Test
    void viewerRejected() {
        handler.handle(ctx(Role.VIEWER, payload("stable_narrow", 3)));

        verify(broadcaster).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
        verifyNoInteractions(sessionService);
    }

    @Test
    void speakerRejected() {
        handler.handle(ctx(Role.SPEAKER, payload("stable_narrow", 3)));

        verify(broadcaster).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
        verifyNoInteractions(sessionService);
    }

    // ==================== 房间状态拒绝 ====================

    @Test
    void roomAlreadyStartedRejected() {
        SessionRoom room = activeRoom();
        room.attachEmsSession(EMS_ID);
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, payload("stable_narrow", 3)));

        verify(broadcaster).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
        verifyNoInteractions(sessionService);
    }

    @Test
    void roomNotActiveRejected() {
        SessionRoom room = new SessionRoom(ROOM_ID, "stable_narrow", "3MAN");
        room.join(new ClientConnection("e-1", Role.ENGINE, null, null));
        // 缺 SPEAKER，状态为 WAITING
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        handler.handle(ctx(Role.ENGINE, payload("stable_narrow", 3)));

        verify(broadcaster).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
        verifyNoInteractions(sessionService);
    }

    // ==================== 参数校验 ====================

    @Test
    void missingCaseIdRejected() {
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(activeRoom());

        handler.handle(ctx(Role.ENGINE, payload(null, 3)));

        verify(broadcaster).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
        verifyNoInteractions(sessionService);
    }

    @Test
    void invalidTeamSizeRejected() {
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(activeRoom());

        handler.handle(ctx(Role.ENGINE, payload("stable_narrow", 7)));

        verify(broadcaster).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
        verifyNoInteractions(sessionService);
    }

    // ==================== 正常路径 ====================

    @Test
    void happyPathDispatchesCaseConfig() {
        SessionRoom room = activeRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        EmsSession ems = new EmsSession();
        ems.setId(EMS_ID);
        when(sessionService.createSession(
            eq("stable_narrow"), eq("张三"), eq("EXAM"), eq(TeamSize.TEAM_4MAN)))
            .thenReturn(ems);

        CaseConfigDto config = new CaseConfigDto();
        config.setEmsSessionId(EMS_ID);
        config.setTeamSize(TeamSize.TEAM_4MAN);
        when(assembler.assemble(EMS_ID, ROOM_ID)).thenReturn(config);

        handler.handle(ctx(Role.ENGINE, payload("stable_narrow", 4)));

        // 房间关联 emsSessionId
        org.junit.jupiter.api.Assertions.assertEquals(EMS_ID, room.getEmsSessionId());

        // 下发 case_config 给 engine
        verify(broadcaster).sendToRole(eq(ROOM_ID), eq(Role.ENGINE),
            argThat(msg -> "case_config".equals(msg.get("type"))));

        // 通知前端 case_initializing
        verify(broadcaster).sendToRoles(eq(ROOM_ID),
            eq(Set.of(Role.SPEAKER, Role.VIEWER, Role.INSTRUCTOR)),
            argThat(msg -> "case_initializing".equals(msg.get("type"))));

        // 没有发 start_case_rejected
        verify(broadcaster, never()).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
    }

    @Test
    void defaultTeamSizeIs3Man() {
        SessionRoom room = activeRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        EmsSession ems = new EmsSession();
        ems.setId(EMS_ID);
        when(sessionService.createSession(
            anyString(), anyString(), anyString(), eq(TeamSize.TEAM_3MAN)))
            .thenReturn(ems);

        CaseConfigDto config = new CaseConfigDto();
        when(assembler.assemble(anyString(), anyString())).thenReturn(config);

        // 不传 teamSize
        Map<String, Object> data = new HashMap<>();
        data.put("caseId", "stable_narrow");
        data.put("traineeName", "张三");
        data.put("mode", "EXAM");

        handler.handle(ctx(Role.ENGINE, data));

        verify(sessionService).createSession(
            eq("stable_narrow"), eq("张三"), eq("EXAM"), eq(TeamSize.TEAM_3MAN));
    }

    @Test
    void sessionCreationFailureRejected() {
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(activeRoom());
        when(sessionService.createSession(anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("案例不存在"));

        handler.handle(ctx(Role.ENGINE, payload("unknown_case", 3)));

        verify(broadcaster).sendToClient(eq(ROOM_ID), eq(CLIENT_ID),
            argThat(msg -> "start_case_rejected".equals(msg.get("type"))));
    }

    @Test
    void instructorCanStart() {
        SessionRoom room = activeRoom();
        when(roomRegistry.getOrThrow(ROOM_ID)).thenReturn(room);

        EmsSession ems = new EmsSession();
        ems.setId(EMS_ID);
        when(sessionService.createSession(anyString(), any(), any(), any()))
            .thenReturn(ems);

        CaseConfigDto config = new CaseConfigDto();
        when(assembler.assemble(anyString(), anyString())).thenReturn(config);

        handler.handle(ctx(Role.INSTRUCTOR, payload("stable_narrow", 3)));

        verify(broadcaster).sendToRole(eq(ROOM_ID), eq(Role.ENGINE),
            argThat(msg -> "case_config".equals(msg.get("type"))));
    }
}

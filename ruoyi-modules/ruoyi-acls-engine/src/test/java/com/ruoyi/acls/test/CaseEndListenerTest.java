package com.ruoyi.acls.test;



import com.ruoyi.ems.clock.ClockService;

import com.ruoyi.acls.room.CaseEndListener;
import com.ruoyi.ems.room.event.StateMachineEndEvent;
import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CaseEndListenerTest {

    private RoomRegistry roomRegistry;
    private RoomBroadcaster broadcaster;
    private ClockService clockService;
    private CaseEndListener listener;

    private static final String ROOM_ID = "room-001";
    private static final String EMS_ID = "ems-001";
    private static final String ENGINE_ID = "engine-1";

    @BeforeEach
    void setup() {
        roomRegistry = mock(RoomRegistry.class);
        broadcaster = mock(RoomBroadcaster.class);
        clockService = mock(ClockService.class);
        listener = new CaseEndListener(roomRegistry, broadcaster, clockService);
    }

    // ==================== 工具 ====================

    /**
     * 构造一个已启动的房间：
     * - engine + speaker 都在（满足 requiredRoles）
     * - 已关联 emsSessionId
     * - 已 markStarted（isStarted = true）
     */
    private SessionRoom runningRoom() {
        SessionRoom room = new SessionRoom(ROOM_ID, "stable_narrow", "3MAN");
        room.join(new ClientConnection(ENGINE_ID, Role.ENGINE, null, null));
        room.join(new ClientConnection("speaker-1", Role.SPEAKER, null, null));
        room.attachEmsSession(EMS_ID);
        room.markStarted();
        return room;
    }

    // ==================== 测试 ====================

    @Test
    void roomNotFoundLogsAndReturns() {
        when(roomRegistry.getByEmsSessionId(EMS_ID)).thenReturn(Optional.empty());

        listener.onStateMachineEnd(new StateMachineEndEvent(EMS_ID, "F_999_END", "completed"));

        verifyNoInteractions(broadcaster, clockService);
    }

    @Test
    void alreadyTerminalRoomIgnored() {
        SessionRoom room = runningRoom();
        room.complete();   // RoomState = COMPLETED
        when(roomRegistry.getByEmsSessionId(EMS_ID)).thenReturn(Optional.of(room));

        listener.onStateMachineEnd(new StateMachineEndEvent(EMS_ID, "F_999_END", "completed"));

        // 房间已终态，不应再次广播或停时钟
        verifyNoInteractions(broadcaster, clockService);
    }

    @Test
    void happyPathCompletesRoomAndBroadcasts() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getByEmsSessionId(EMS_ID)).thenReturn(Optional.of(room));
        when(clockService.elapsedSeconds(anyString())).thenReturn(42.0);

        listener.onStateMachineEnd(new StateMachineEndEvent(EMS_ID, "F_999_END", "completed"));

        // 房间状态 → COMPLETED
        assertEquals(RoomState.COMPLETED, room.getState());

        // 停止时钟
        verify(clockService).stop(EMS_ID);

        // 广播 case_end
        verify(broadcaster).broadcast(eq(ROOM_ID),
            argThat(msg -> "case_end".equals(msg.get("type"))));
    }

    @Test
    void broadcastPayloadContainsFinalStateAndReason() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getByEmsSessionId(EMS_ID)).thenReturn(Optional.of(room));
        when(clockService.elapsedSeconds(anyString())).thenReturn(120.0);

        listener.onStateMachineEnd(new StateMachineEndEvent(EMS_ID, "F_999_END", "aborted"));

        verify(broadcaster).broadcast(eq(ROOM_ID), argThat(msg -> {
            if (!"case_end".equals(msg.get("type"))) return false;

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.get("data");

            return "aborted".equals(data.get("reason"))
                && "F_999_END".equals(data.get("finalState"))
                && EMS_ID.equals(data.get("emsSessionId"));
        }));
    }

    @Test
    void elapsedSecondsReadBeforeStop() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getByEmsSessionId(EMS_ID)).thenReturn(Optional.of(room));
        when(clockService.elapsedSeconds(EMS_ID)).thenReturn(99.0);

        listener.onStateMachineEnd(new StateMachineEndEvent(EMS_ID, "F_999_END", "completed"));

        // 顺序断言：elapsedSeconds 在 stop 之前调用
        var inOrder = inOrder(clockService);
        inOrder.verify(clockService).elapsedSeconds(EMS_ID);
        inOrder.verify(clockService).stop(EMS_ID);
    }

    @Test
    void nullReasonDefaultsToCompleted() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getByEmsSessionId(EMS_ID)).thenReturn(Optional.of(room));
        when(clockService.elapsedSeconds(anyString())).thenReturn(10.0);

        // StateMachineEndEvent 紧凑构造器把 null reason 变成 "completed"
        listener.onStateMachineEnd(new StateMachineEndEvent(EMS_ID, "F_999_END", null));

        verify(broadcaster).broadcast(eq(ROOM_ID), argThat(msg -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.get("data");
            return "completed".equals(data.get("reason"));
        }));
    }

    @Test
    void clockStopHappensAfterRoomComplete() {
        SessionRoom room = runningRoom();
        when(roomRegistry.getByEmsSessionId(EMS_ID)).thenReturn(Optional.of(room));
        when(clockService.elapsedSeconds(anyString())).thenReturn(5.0);

        listener.onStateMachineEnd(new StateMachineEndEvent(EMS_ID, "F_999_END", "completed"));

        // 断言：房间已经是 COMPLETED 状态
        assertEquals(RoomState.COMPLETED, room.getState());
        // 时钟 stop 已调用
        verify(clockService).stop(EMS_ID);
    }
}

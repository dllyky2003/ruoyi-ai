package com.ruoyi.ems.room;

import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.model.SessionRoom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionRoomTest {

    private ClientConnection conn(String id, Role role) {
        return new ClientConnection(id, role, null, null);
    }

    @Test
    void initialStateIsCreated() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        assertEquals(RoomState.CREATED, room.getState());
    }

    @Test
    void waitingWhenOnlyViewerJoined() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        room.join(conn("c1", Role.VIEWER));
        assertEquals(RoomState.WAITING, room.getState());
    }

    @Test
    void activeWhenAllRequiredRolesPresent() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        room.join(conn("c1", Role.ENGINE));
        assertEquals(RoomState.WAITING, room.getState());

        room.join(conn("c2", Role.SPEAKER));
        assertEquals(RoomState.ACTIVE, room.getState());
    }

    @Test
    void pausedWhenRequiredRoleLeaves() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        room.join(conn("c1", Role.ENGINE));
        room.join(conn("c2", Role.SPEAKER));
        assertEquals(RoomState.ACTIVE, room.getState());

        room.leave("c1");
        assertEquals(RoomState.PAUSED, room.getState());
    }

    @Test
    void resumesToActiveOnReconnect() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        room.join(conn("c1", Role.ENGINE));
        room.join(conn("c2", Role.SPEAKER));
        room.leave("c1");
        assertEquals(RoomState.PAUSED, room.getState());

        room.join(conn("c1", Role.ENGINE));
        assertEquals(RoomState.ACTIVE, room.getState());
    }

    @Test
    void duplicateClientIdRejected() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        assertTrue(room.join(conn("c1", Role.ENGINE)));
        assertFalse(room.join(conn("c1", Role.VIEWER)));
    }

    @Test
    void completeMovesToTerminalState() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        room.join(conn("c1", Role.ENGINE));
        room.join(conn("c2", Role.SPEAKER));
        room.complete();
        assertEquals(RoomState.COMPLETED, room.getState());
        assertTrue(room.isTerminal());
    }

    @Test
    void cannotCompleteAfterExpired() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        room.join(conn("c1", Role.ENGINE));
        room.leave("c1");
        room.expire();
        assertEquals(RoomState.EXPIRED, room.getState());

        room.complete();
        assertEquals(RoomState.EXPIRED, room.getState());
    }

    @Test
    void getClientsByRole() {
        SessionRoom room = new SessionRoom("s1", "stable_narrow", "3MAN");
        room.join(conn("v1", Role.VIEWER));
        room.join(conn("v2", Role.VIEWER));
        room.join(conn("e1", Role.ENGINE));

        assertEquals(2, room.getClientsByRole(Role.VIEWER).size());
        assertEquals(1, room.getClientsByRole(Role.ENGINE).size());
        assertTrue(room.hasRole(Role.ENGINE));
        assertFalse(room.hasRole(Role.SPEAKER));
    }
}

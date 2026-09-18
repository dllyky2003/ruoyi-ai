package com.ruoyi.ems.room;

import com.ruoyi.ems.room.ws.WsSessionRegistry;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WsSessionRegistryTest {

    private Session mockSession() {
        return mock(Session.class);
    }

    @Test
    void registerAndUnregister() {
        WsSessionRegistry reg = new WsSessionRegistry();
        reg.register("s1", "c1", mockSession());

        assertEquals(1, reg.count("s1"));
        assertFalse(reg.isEmpty("s1"));

        reg.unregister("s1", "c1");
        assertEquals(0, reg.count("s1"));
        assertTrue(reg.isEmpty("s1"));
    }

    @Test
    void multipleClientsPerSession() {
        WsSessionRegistry reg = new WsSessionRegistry();
        reg.register("s1", "c1", mockSession());
        reg.register("s1", "c2", mockSession());
        reg.register("s2", "c3", mockSession());

        assertEquals(2, reg.count("s1"));
        assertEquals(1, reg.count("s2"));
        assertEquals(2, reg.getClientIds("s1").size());
    }

    @Test
    void registerNullSessionRejected() {
        WsSessionRegistry reg = new WsSessionRegistry();
        assertThrows(IllegalArgumentException.class,
            () -> reg.register("s1", "c1", null));
    }
}

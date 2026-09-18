package com.ruoyi.ems.room;


import com.ruoyi.ems.room.dto.*;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.service.RoomIdGenerator;
import com.ruoyi.ems.room.service.RoomService;
import com.ruoyi.ems.room.token.WsTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    private RoomService service;
    private RoomRegistry registry;

    @BeforeEach
    void setup() {
        registry = new RoomRegistry();
        WsTokenService tokens = new WsTokenService();
        RoomIdGenerator ids = new RoomIdGenerator();

        service = new RoomService(registry, tokens, ids);
        ReflectionTestUtils.setField(service, "wsBaseUrl", "ws://localhost:8080");
        ReflectionTestUtils.setField(service, "tokenTtlSeconds", 300L);
    }

    @Test
    void createReturnsSessionAndToken() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setCaseId("stable_narrow");
        req.setTemplateId("3MAN");
        req.setCreatorRole("instructor");

        CreateRoomResponse resp = service.create(req);

        assertNotNull(resp.getSessionId());
        assertNotNull(resp.getWsToken());
        assertEquals("instructor", resp.getCreatorRole());
        assertTrue(resp.getWsUrl().contains("token=" + resp.getWsToken()));
        assertTrue(registry.exists(resp.getSessionId()));
    }

    @Test
    void createWithoutCaseIdFails() {
        CreateRoomRequest req = new CreateRoomRequest();
        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void engineAndSpeakerCanJoinUntilActive() {
        CreateRoomResponse created = service.create(buildCreate("engine"));

        JoinRequest join = new JoinRequest();
        join.setRole("speaker");
        JoinResponse r1 = service.join(created.getSessionId(), join);

        assertEquals("speaker", r1.getRole());
        assertEquals(RoomState.ACTIVE.name(), r1.getState());
    }

    @Test
    void duplicateSpeakerRejected() {
        CreateRoomResponse created = service.create(buildCreate("engine"));

        JoinRequest join = new JoinRequest();
        join.setRole("speaker");
        service.join(created.getSessionId(), join);

        assertThrows(IllegalStateException.class,
            () -> service.join(created.getSessionId(), join));
    }

    @Test
    void multipleViewersAllowed() {
        CreateRoomResponse created = service.create(buildCreate("instructor"));

        JoinRequest join = new JoinRequest();
        join.setRole("viewer");
        service.join(created.getSessionId(), join);
        service.join(created.getSessionId(), join);
        service.join(created.getSessionId(), join);

        RoomDetail detail = service.detail(created.getSessionId());
        assertEquals(4, detail.getClients().size());
    }

    @Test
    void joinUnknownSessionFails() {
        JoinRequest join = new JoinRequest();
        join.setRole("viewer");
        assertThrows(NoSuchElementException.class,
            () -> service.join("NOPE", join));
    }

    @Test
    void endMovesToCompleted() {
        CreateRoomResponse created = service.create(buildCreate("instructor"));
        service.end(created.getSessionId(), "test");

        RoomDetail detail = service.detail(created.getSessionId());
        assertEquals(RoomState.COMPLETED.name(), detail.getState());
    }

    @Test
    void listFilterByCaseId() {
        service.create(buildCreate("instructor", "caseA"));
        service.create(buildCreate("instructor", "caseB"));

        List<RoomSummary> result = service.list("caseA", null);
        assertEquals(1, result.size());
        assertEquals("caseA", result.get(0).getCaseId());
    }

    private CreateRoomRequest buildCreate(String creatorRole) {
        return buildCreate(creatorRole, "stable_narrow");
    }

    private CreateRoomRequest buildCreate(String creatorRole, String caseId) {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setCaseId(caseId);
        req.setTemplateId("3MAN");
        req.setCreatorRole(creatorRole);
        return req;
    }
}

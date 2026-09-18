package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.domain.entity.EmsSession;


import com.ruoyi.ems.npc.TeamSize;
import com.ruoyi.ems.room.CaseConfigAssembler;
import com.ruoyi.ems.room.dto.CaseConfigDto;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartCaseHandler implements WsMessageRouter.WsMessageHandler {

    private final RoomRegistry roomRegistry;
    private final SessionService sessionService;
    private final CaseConfigAssembler assembler;
    private final RoomBroadcaster broadcaster;

    @Override
    public boolean supports(String type) {
        return "start_case_request".equals(type);
    }

    @Override
    public void handle(WsMessageContext ctx) {
        if (ctx.role() != Role.ENGINE && ctx.role() != Role.INSTRUCTOR) {
            reject(ctx, "角色无权限发起案例启动");
            return;
        }

        SessionRoom room = roomRegistry.getOrThrow(ctx.sessionId());

        if (room.getEmsSessionId() != null) {
            reject(ctx, "案例已启动: " + room.getEmsSessionId());
            return;
        }

        if (room.getState() != RoomState.ACTIVE) {
            reject(ctx, "必需角色未到齐，当前状态: " + room.getState());
            return;
        }

        Map<String, Object> data = ctx.data();
        String caseId      = (String) data.get("caseId");
        String traineeName = (String) data.get("traineeName");
        String mode        = (String) data.getOrDefault("mode", "EXAM");

        if (caseId == null || caseId.isBlank()) {
            reject(ctx, "缺少 caseId");
            return;
        }

        TeamSize teamSize = asTeamSize(data.get("teamSize"));
        if (teamSize == null) {
            reject(ctx, "不支持的 teamSize，可选 3 / 4 / 5");
            return;
        }

        try {
            EmsSession emsSession = sessionService.createSession(
                caseId, traineeName, mode, teamSize);

            if (emsSession == null) {
                reject(ctx, "创建业务会话失败");
                return;
            }

            room.attachEmsSession(emsSession.getId());

            CaseConfigDto config = assembler.assemble(
                emsSession.getId(), ctx.sessionId());

            broadcaster.sendToRole(ctx.sessionId(), Role.ENGINE,
                RoomBroadcaster.envelope("case_config", config));

            broadcaster.sendToRoles(ctx.sessionId(),
                Set.of(Role.SPEAKER, Role.VIEWER, Role.INSTRUCTOR),
                RoomBroadcaster.envelope("case_initializing", Map.of(
                    "emsSessionId", emsSession.getId(),
                    "caseId", caseId)));

            log.info("📋 案例启动: roomSession={}, emsSession={}, caseId={}, teamSize={}",
                ctx.sessionId(), emsSession.getId(), caseId, teamSize);

        } catch (Exception e) {
            log.error("案例启动失败: roomSession={}", ctx.sessionId(), e);
            reject(ctx, "启动失败: " + e.getMessage());
        }
    }

    private static TeamSize asTeamSize(Object v) {
        if (v == null) return TeamSize.defaultSize();

        Integer val = null;
        if (v instanceof Number n) {
            val = n.intValue();
        } else {
            try {
                val = Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                // val 保持 null
            }
        }
        return val != null ? TeamSize.fromValue(val) : null;
    }

    private void reject(WsMessageContext ctx, String reason) {
        broadcaster.sendToClient(ctx.sessionId(), ctx.clientId(),
            RoomBroadcaster.envelope("start_case_rejected", Map.of(
                "reason", reason)));
    }
}

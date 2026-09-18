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
        // 角色放宽：ENGINE / INSTRUCTOR / SPEAKER
        if (ctx.role() != Role.ENGINE
            && ctx.role() != Role.INSTRUCTOR
            && ctx.role() != Role.SPEAKER) {
            reject(ctx, "角色无权限发起案例启动");
            return;
        }

        SessionRoom room = roomRegistry.getOrThrow(ctx.sessionId());

        // ==================== ★ 房间已绑定 emsSession 的处理 ====================
        if (room.getEmsSessionId() != null) {
            String existingEmsId = room.getEmsSessionId();
            EmsSession existing = sessionService.getSessionById(existingEmsId);
            String status = existing != null ? existing.getStatus() : null;

            // 分支 1：会话仍在运行 → Unity 重连恢复，重新下发 case_config
            if ("RUNNING".equals(status)) {
                log.info("♻️ 案例仍运行中，重新下发 case_config: room={}, ems={}",
                    ctx.sessionId(), existingEmsId);
                CaseConfigDto config = assembler.assemble(existingEmsId, ctx.sessionId());
                broadcaster.sendToRole(ctx.sessionId(), Role.ENGINE,
                    RoomBroadcaster.envelope("case_config", config));
                return;
            }

            // 分支 2：会话已结束（COMPLETED/INIT 等） → 解绑旧会话，继续走创建流程
            log.info("🔄 旧会话已结束（status={}），解绑后重新创建: room={}, oldEms={}",
                status, ctx.sessionId(), existingEmsId);
            // ★ 修复：attachEmsSession(null) 会抛 IllegalArgumentException（见 SessionRoom L276-278）
            //   改用显式 detach 方法
            room.detachEmsSession();
        }

        // ==================== 房间状态检查（保持放宽） ====================
        RoomState state = room.getState();
        if (state != RoomState.ACTIVE && state != RoomState.WAITING) {
            log.warn("start_case 被拒：roomState={}, sessionId={}",
                state, ctx.sessionId());
            reject(ctx, "房间状态不允许启动: " + state);
            return;
        }

        log.info("📋 start_case 请求: room={}, role={}, roomState={}, clientCount={}",
            ctx.sessionId(), ctx.role(), state, room.size());

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

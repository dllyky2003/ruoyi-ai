package com.ruoyi.acls.room;


import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.ws.WsMessageContext;
import com.ruoyi.ems.room.ws.WsMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 暂停/恢复处理器。
 * <p>
 * 消息类型：pause_case_request / resume_case_request
 * 允许发起角色：engine / speaker / instructor（viewer 无权）
 * </p>
 * <p>
 * 前置条件：案例已启动（room.isStarted()），且房间未处于终态。
 * 幂等：已暂停时再发 pause 忽略；未暂停时再发 resume 忽略。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PauseResumeHandler implements WsMessageRouter.WsMessageHandler {

    private final RoomRegistry roomRegistry;
    private final RoomBroadcaster broadcaster;
    private final ClockService clockService;

    @Override
    public boolean supports(String type) {
        return "pause_case_request".equals(type)
            || "resume_case_request".equals(type);
    }

    @Override
    public void handle(WsMessageContext ctx) {
        if (!canControl(ctx.role())) {
            log.warn("角色无权限控制暂停/恢复: room={}, role={}",
                ctx.sessionId(), ctx.role());
            return;
        }

        SessionRoom room = roomRegistry.getOrThrow(ctx.sessionId());

        if (!room.isStarted() || room.isTerminal()) {
            log.warn("案例未运行或已结束，忽略暂停/恢复: room={}, state={}",
                ctx.sessionId(), room.getState());
            return;
        }

        String type = (String) ctx.message().get("type");
        String emsSessionId = room.getEmsSessionId();

        if ("pause_case_request".equals(type)) {
            handlePause(ctx, room, emsSessionId);
        } else {
            handleResume(ctx, room, emsSessionId);
        }
    }

    private void handlePause(WsMessageContext ctx, SessionRoom room, String emsSessionId) {
        if (room.isPaused()) {
            log.debug("案例已暂停，忽略重复请求: room={}", ctx.sessionId());
            return;
        }

        clockService.pause(emsSessionId);
        room.markPaused();

        broadcaster.broadcast(ctx.sessionId(),
            RoomBroadcaster.envelope("case_paused", Map.of(
                "emsSessionId", emsSessionId,
                "pausedAt", System.currentTimeMillis(),
                "requestedBy", ctx.role().getCode()
            )));

        log.info("⏸️ 案例暂停: room={}, ems={}, by={}",
            ctx.sessionId(), emsSessionId, ctx.role());
    }

    private void handleResume(WsMessageContext ctx, SessionRoom room, String emsSessionId) {
        if (!room.isPaused()) {
            log.debug("案例未暂停，忽略恢复请求: room={}", ctx.sessionId());
            return;
        }

        clockService.resume(emsSessionId);
        room.markResumed();

        broadcaster.broadcast(ctx.sessionId(),
            RoomBroadcaster.envelope("case_resumed", Map.of(
                "emsSessionId", emsSessionId,
                "resumedAt", System.currentTimeMillis(),
                "requestedBy", ctx.role().getCode()
            )));

        log.info("▶️ 案例恢复: room={}, ems={}, by={}",
            ctx.sessionId(), emsSessionId, ctx.role());
    }

    private boolean canControl(Role role) {
        return role == Role.ENGINE
            || role == Role.SPEAKER
            || role == Role.INSTRUCTOR;
    }
}

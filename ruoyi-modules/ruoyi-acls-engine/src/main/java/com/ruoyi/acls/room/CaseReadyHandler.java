package com.ruoyi.acls.room;


import com.ruoyi.acls.engine.ClockService;
import com.ruoyi.ems.domain.entity.EmsSession;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.ws.WsMessageContext;
import com.ruoyi.ems.room.ws.WsMessageRouter;
import com.ruoyi.ems.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * case_ready 处理器。
 * <p>
 * Unity 初始化 Pulse + NPC agents 完成后发送。
 * 服务端标记该 client 已就绪，ENGINE 就绪后：
 * <ol>
 *   <li>启动时钟</li>
 *   <li>调用 SessionService.startSession 启动状态机</li>
 *   <li>广播 case_start</li>
 * </ol>
 * </p>
 *
 * <p>注：当前允许单角色（ENGINE）启动，不再要求其他必需角色存在。
 * 未来如恢复多角色要求，将 {@code if (!engineReady)} 改回
 * {@code if (!room.allRequiredReady())} 即可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseReadyHandler implements WsMessageRouter.WsMessageHandler {

    private final RoomRegistry roomRegistry;
    private final RoomBroadcaster broadcaster;
    private final SessionService sessionService;
    private final ClockService clockService;

    @Override
    public boolean supports(String type) {
        return "case_ready".equals(type);
    }

    @Override
    public void handle(WsMessageContext ctx) {
        if (ctx.role() != Role.ENGINE) {
            log.warn("非引擎角色发送 case_ready: room={}, role={}",
                ctx.sessionId(), ctx.role());
            return;
        }

        SessionRoom room = roomRegistry.getOrThrow(ctx.sessionId());

        if (room.getEmsSessionId() == null) {
            log.warn("case_ready 但房间未关联业务会话: room={}", ctx.sessionId());
            return;
        }

        if (room.isStarted()) {
            log.debug("案例已启动，忽略重复 case_ready: room={}", ctx.sessionId());
            return;
        }

        // 标记就绪
        room.markReady(ctx.clientId());

        // 只要 ENGINE 就绪即启动
        boolean engineReady = room.isEngineReady();
        boolean allReady = room.allRequiredReady();

        log.info("✅ 引擎就绪: room={}, clientId={}, engineReady={}, allRequired={}",
            ctx.sessionId(), ctx.clientId(), engineReady, allReady);

        if (!engineReady) {
            return;
        }

        // 启动案例
        String emsSessionId = room.getEmsSessionId();
        try {
            room.markStarted();
            clockService.start(emsSessionId);

            EmsSession ems = sessionService.getSessionById(emsSessionId);
            if (ems != null) {
                sessionService.startSession(ems);
            }

            broadcaster.broadcast(ctx.sessionId(),
                RoomBroadcaster.envelope("case_start", Map.of(
                    "emsSessionId", emsSessionId,
                    "startedAt", System.currentTimeMillis()
                )));

            log.info("🚀 案例正式开始: room={}, ems={}",
                ctx.sessionId(), emsSessionId);

        } catch (Exception e) {
            log.error("启动案例失败: room={}, ems={}", ctx.sessionId(), emsSessionId, e);
            broadcaster.broadcast(ctx.sessionId(),
                RoomBroadcaster.envelope("case_start_failed", Map.of(
                    "reason", e.getMessage() != null ? e.getMessage() : "unknown"
                )));
        }
    }
}

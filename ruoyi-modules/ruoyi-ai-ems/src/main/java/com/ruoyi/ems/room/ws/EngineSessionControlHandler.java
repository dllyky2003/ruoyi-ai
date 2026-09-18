package com.ruoyi.ems.room.ws;


import com.ruoyi.ems.dispatcher.SessionCommandDispatcher;
import com.ruoyi.ems.domain.dto.CommandTask;
import com.ruoyi.ems.domain.entity.EmsSession;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Unity 引擎会话控制处理器。
 *
 * <p>处理 Unity 直接发起的会话级指令（替代独立学员语音端）：</p>
 * <ul>
 *   <li>{@code asr_transcript} —— 学员语音识别文本，转 {@link CommandTask} 走调度器</li>
 *   <li>{@code start_session}  —— 触发 {@link SessionService#startSession}</li>
 *   <li>{@code end_session}    —— 触发 {@link SessionService#endSession}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineSessionControlHandler implements WsMessageRouter.WsMessageHandler {

    private final RoomRegistry roomRegistry;
    private final SessionService sessionService;
    private final SessionCommandDispatcher dispatcher;

    private static final Set<String> SUPPORTED = Set.of(
        "asr_transcript",
        "start_session",
        "end_session"
    );

    @Override
    public boolean supports(String type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public void handle(WsMessageContext ctx) {
        if (ctx.role() != Role.ENGINE) {
            log.warn("非引擎角色发送会话控制消息: sessionId={}, role={}, type={}",
                ctx.sessionId(), ctx.role(), ctx.message().get("type"));
            return;
        }

        String type = (String) ctx.message().get("type");
        Map<String, Object> data = ctx.data();

        switch (type) {
            case "asr_transcript" -> handleAsrTranscript(ctx, data);
            case "start_session"  -> handleStartSession(ctx, data);
            case "end_session"    -> handleEndSession(ctx, data);
            default -> log.warn("未处理的会话控制消息: type={}", type);
        }
    }

    // ==================== asr_transcript ====================

    private void handleAsrTranscript(WsMessageContext ctx, Map<String, Object> data) {
        String roomSessionId = ctx.sessionId();
        String emsSessionId = resolveEmsSessionId(roomSessionId, data);
        if (emsSessionId == null) {
            log.warn("asr_transcript 无法解析 emsSessionId: room={}", roomSessionId);
            return;
        }

        // 非 final 结果直接丢弃（减少无谓处理）
        Object isFinalObj = data.get("isFinal");
        boolean isFinal = !(isFinalObj instanceof Boolean) || (Boolean) isFinalObj;
        if (!isFinal) {
            log.debug("asr_transcript 非最终结果，丢弃: room={}", roomSessionId);
            return;
        }

        String text = (String) data.get("text");
        if (text == null || text.trim().isEmpty()) {
            log.warn("asr_transcript 文本为空: room={}", roomSessionId);
            return;
        }

        log.info("🧠 [Unity ASR] room={}, ems={}, text='{}'",
            roomSessionId, emsSessionId, text);

        // Session 参数传 null —— WebSocketMessageSender.sendTranscript 会 warn 但不崩。
        // 后续可优化为通过 RoomBroadcaster 广播结果给 Unity（TODO）。
        CommandTask task = new CommandTask(
            emsSessionId,
            text,
            null,
            System.currentTimeMillis(),
            0
        );

        if (dispatcher == null) {
            log.error("asr_transcript: dispatcher 未初始化，无法处理");
            return;
        }
        dispatcher.submit(task, t -> {
            try {
                return sessionService.executeCommandSync(t);
            } catch (Exception e) {
                log.error("执行指令异常: ems={}", t.getSessionId(), e);
                return "ERROR: " + e.getMessage();
            }
        });
    }

    // ==================== start_session ====================

    private void handleStartSession(WsMessageContext ctx, Map<String, Object> data) {
        String emsSessionId = resolveEmsSessionId(ctx.sessionId(), data);
        if (emsSessionId == null) {
            log.warn("start_session 无法解析 emsSessionId: room={}", ctx.sessionId());
            return;
        }

        EmsSession session = sessionService.getSessionById(emsSessionId);
        if (session == null) {
            log.warn("start_session 找不到会话: ems={}", emsSessionId);
            return;
        }

        try {
            sessionService.startSession(session);
            log.info("✅ Unity 触发 startSession: ems={}, room={}",
                emsSessionId, ctx.sessionId());
        } catch (Exception e) {
            log.error("startSession 失败: ems={}", emsSessionId, e);
        }
    }

    // ==================== end_session ====================

    private void handleEndSession(WsMessageContext ctx, Map<String, Object> data) {
        String emsSessionId = resolveEmsSessionId(ctx.sessionId(), data);
        if (emsSessionId == null) {
            log.warn("end_session 无法解析 emsSessionId: room={}", ctx.sessionId());
            return;
        }

        try {
            sessionService.endSession(emsSessionId);
            log.info("✅ Unity 触发 endSession: ems={}, room={}",
                emsSessionId, ctx.sessionId());
        } catch (Exception e) {
            log.error("endSession 失败: ems={}", emsSessionId, e);
        }
    }

    // ==================== 辅助 ====================

    /**
     * 解析 emsSessionId。
     * 优先用 data 里的，其次用 roomRegistry 从 roomSessionId 反查。
     */
    private String resolveEmsSessionId(String roomSessionId, Map<String, Object> data) {
        if (data != null) {
            Object id = data.get("emsSessionId");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        }
        return roomRegistry.get(roomSessionId)
            .map(SessionRoom::getEmsSessionId)
            .orElse(null);
    }
}

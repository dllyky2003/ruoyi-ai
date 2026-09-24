package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.asr.AsrUnavailableException;
import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.ems.dispatcher.SessionCommandDispatcher;
import com.ruoyi.ems.domain.dto.CommandTask;
import com.ruoyi.ems.domain.entity.EmsSession;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.protocol.AttemptSnapshotService;
import com.ruoyi.ems.room.protocol.ProtocolBridgeService;
import com.ruoyi.ems.room.protocol.dto.SnapshotMessage;
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
 *   <li>{@code start_session}  —— 案例正式开始边界，触发时钟启动 + {@link SessionService#startSession}</li>
 *   <li>{@code end_session}    —— 触发时钟停止 + {@link SessionService#endSession}</li>
 * </ul>
 *
 * <p><b>案例开始契约：</b>只有收到客户端 {@code start_session} 才是案例正式开始。
 * {@code case_ready} / {@code case_start} 都不是开始，不得启动时钟、不得初始化
 * {@code process.lastInterventionTime}、不得启动 ASR。</p>
 *
 * <p>★ 2026-09-22 改造（P0-A 房间复用隔离）：</p>
 * <ul>
 *   <li>收到 {@code start_session} 时，调用 {@code room.markStartSessionConsumed()}，
 *       标记该房间已被消费。后续 {@code RoomService.create} 的复用判断会拒绝复用。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineSessionControlHandler implements WsMessageRouter.WsMessageHandler {

    private final RoomRegistry roomRegistry;
    private final SessionService sessionService;
    private final SessionCommandDispatcher dispatcher;
    private final ClockService clockService;
    private final AttemptSnapshotService snapshotService;
    private final ProtocolBridgeService protocolBridge;
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

    /**
     * 案例正式开始。
     *
     * <p>执行顺序（关键）：</p>
     * <ol>
     *   <li>校验 session 存在、room 未启动</li>
     *   <li><b>先启动时钟</b>——初始化 {@code process.lastInterventionTime}、
     *       记录评估真实时间锚点。失败则直接返回，room 保持未开始，允许客户端重试。</li>
     *   <li><b>再 markStarted + markStartSessionConsumed</b>——此时时钟已就绪，进入"已开始"状态。</li>
     *   <li><b>最后启动 ASR 会话</b>——ASR 不可用时降级，不阻塞案例开始，不回滚。</li>
     * </ol>
     *
     * <p>为什么时钟必须在 markStarted 之前：若先标记 started 再启动时钟，
     * 时钟启动失败会导致 room 状态为"已开始"但时钟未运行，客户端重发 start_session
     * 被幂等拒绝，会话卡死。</p>
     *
     * <p>★ 为什么同时要 markStartSessionConsumed：RoomService.create 在复用判断时，
     * 只认 startSessionConsumed 标志。必须在同一时刻一起打上，避免中间态被复用。</p>
     */
    /**
     * 案例正式开始。
     *
     * <p>执行顺序（关键）：</p>
     * <ol>
     *   <li>校验 session 存在、room 未启动</li>
     *   <li><b>先启动时钟</b>——初始化 {@code process.lastInterventionTime}、
     *       记录评估真实时间锚点。失败则直接返回，room 保持未开始，允许客户端重试。</li>
     *   <li><b>再 markStarted + markStartSessionConsumed</b>——此时时钟已就绪，进入"已开始"状态。</li>
     *   <li><b>最后启动 ASR 会话</b>——ASR 不可用时降级，不阻塞案例开始，不回滚。</li>
     *   <li><b>契约 V1.0.0：主动推一次全量 SNAPSHOT</b>——让 Unity 拿到基线，
     *       后续增量快照和 CMD_RESULT 才能被接受。</li>
     * </ol>
     */
    private void handleStartSession(WsMessageContext ctx, Map<String, Object> data) {
        String roomSessionId = ctx.sessionId();
        String emsSessionId = resolveEmsSessionId(roomSessionId, data);
        if (emsSessionId == null) {
            log.warn("start_session 无法解析 emsSessionId: room={}", roomSessionId);
            return;
        }

        EmsSession session = sessionService.getSessionById(emsSessionId);
        if (session == null) {
            log.warn("start_session 找不到会话: ems={}", emsSessionId);
            return;
        }

        SessionRoom room = roomRegistry.getOrThrow(roomSessionId);

        // 幂等：已正式开始的忽略
        if (room.isStarted()) {
            log.warn("⚠️ 重复 start_session，忽略: room={}, ems={}", roomSessionId, emsSessionId);
            return;
        }

        log.info("✅ Unity 触发 startSession: ems={}, room={}", emsSessionId, roomSessionId);

        // ===== 步骤 1：启动时钟（失败即中止，room 保持未开始） =====
        try {
            clockService.start(emsSessionId);
        } catch (Exception e) {
            log.error("❌ 启动时钟失败，start_session 中止（可重试）: room={}, ems={}",
                roomSessionId, emsSessionId, e);
            return;
        }

        // ===== 步骤 2：标记房间已开始 + 标记 start_session 已消费 =====
        room.markStarted();
        room.markStartSessionConsumed();

        // ===== 步骤 3：启动 ASR 会话（不可用降级，不阻塞） =====
        try {
            sessionService.startSession(session);
        } catch (AsrUnavailableException e) {
            log.warn("⚠️ ASR 网关不可用，语音识别降级: ems={}, reason={}",
                emsSessionId, e.getMessage());
        } catch (Exception e) {
            log.error("❌ startSession 失败（时钟已启动，room 已标记）: ems={}",
                emsSessionId, e);
            return;
        }

        // ===== 步骤 4：契约 V1.0.0 —— 主动推一次全量 SNAPSHOT =====
        try {
            String attemptId = room.getAttemptId();   // ← 从已拿到的 room 取
            if (attemptId == null || attemptId.isBlank()) {
                log.warn("⚠️ 房间缺少 attemptId，跳过快照推送: room={}, ems={}",
                    roomSessionId, emsSessionId);
            } else {
                SnapshotMessage initial = snapshotService.buildFullSnapshot(emsSessionId, attemptId);
                if (initial != null) {
                    protocolBridge.pushSnapshot(emsSessionId, initial);
                    log.info("📤 start_session 后主动推送全量快照: ems={}, rev={}, attemptId={}",
                        emsSessionId, initial.getStateRevision(), initial.getAttemptId());
                } else {
                    log.warn("⚠️ 快照构建返回 null，跳过: ems={}, attemptId={}",
                        emsSessionId, attemptId);
                }
            }
        } catch (Exception e) {
            log.warn("start_session 后推送快照失败: ems={}, err={}",
                emsSessionId, e.getMessage());
        }
        log.info("🚀 案例正式开始: room={}, ems={}", roomSessionId, emsSessionId);
    }

    // ==================== end_session ====================

    /**
     * 案例结束。
     *
     * <p>先停时钟（冻结评估真实时间、停止 tick），再结束业务会话。
     * 停时钟失败不阻塞 end_session——即使时钟短暂继续跑，会话结束后
     * {@code ClockService.tick} 因 {@code CaseContext} 失效也会自然跳过。</p>
     */
    private void handleEndSession(WsMessageContext ctx, Map<String, Object> data) {
        String roomSessionId = ctx.sessionId();
        String emsSessionId = resolveEmsSessionId(roomSessionId, data);
        if (emsSessionId == null) {
            log.warn("end_session 无法解析 emsSessionId: room={}", roomSessionId);
            return;
        }

        // 先停时钟：冻结 assessmentRealElapsedSeconds，停止每秒 tick
        try {
            clockService.stop(emsSessionId);
        } catch (Exception e) {
            log.warn("停止时钟失败（继续 endSession）: ems={}, err={}",
                emsSessionId, e.getMessage());
        }

        try {
            sessionService.endSession(emsSessionId);
            log.info("✅ Unity 触发 endSession: ems={}, room={}",
                emsSessionId, roomSessionId);
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

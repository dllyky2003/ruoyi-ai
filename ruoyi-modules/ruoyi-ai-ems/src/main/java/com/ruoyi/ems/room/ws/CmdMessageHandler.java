package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.dispatcher.SessionCommandDispatcher;
import com.ruoyi.ems.domain.dto.CommandTask;
import com.ruoyi.ems.domain.dto.ExecutionOutcome;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.protocol.AttemptSnapshotService;
import com.ruoyi.ems.room.protocol.ErrorCode;
import com.ruoyi.ems.room.protocol.IdempotencyCache;
import com.ruoyi.ems.room.protocol.ProtocolBridgeService;
import com.ruoyi.ems.room.protocol.dto.CmdMessage;
import com.ruoyi.ems.room.protocol.dto.CommandResultMessage;
import com.ruoyi.ems.room.protocol.dto.SnapshotMessage;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.service.SessionService;
import com.ruoyi.ems.spi.dto.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约 V1.0.0 §5 —— 命令协议入口。
 *
 * <p>★ 批 1（螺旋 2 / 阶段 2.1）：CMD_RESULT 从 {@link ExecutionOutcome} 组装，
 * decision / severity / primaryRuleId / allMatchedRuleIds / feedbackKeys /
 * assessmentTags 取真实 tutor 决策。</p>
 *
 * <p>★ 批 2（螺旋 2 / 阶段 2.1）：
 * <ul>
 *   <li>所有业务层 CMD_RESULT 统一走 {@link ProtocolBridgeService#pushCommandResult}，
 *       serverSequence 由 {@code AttemptSequencer} 统一填充；</li>
 *   <li>STALE_REVISION / SEQUENCE_GAP 附全量快照；</li>
 *   <li>DTO 统一为 {@link CommandResultMessage}（旧 CmdResultMessage 弃用）；</li>
 *   <li>极早退（attemptId/emsSessionId 未就绪阶段）保留旧 room 广播。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmdMessageHandler implements WsMessageRouter.WsMessageHandler {

    private static final String TYPE = "CMD";
    private static final Set<String> VALID_TIME_BASIS = Set.of("simulated", "real");

    private final RoomRegistry roomRegistry;
    private final SessionService sessionService;
    private final SessionCommandDispatcher dispatcher;
    private final IdempotencyCache idempotencyCache;
    private final EmsContextManager contextManager;
    private final RoomBroadcaster broadcaster;

    // ★ 批 2 新增
    private final ProtocolBridgeService protocolBridgeService;
    private final AttemptSnapshotService attemptSnapshotService;

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public void handle(WsMessageContext ctx) {
        String roomSessionId = ctx.sessionId();
        Map<String, Object> data = ctx.data();

        // ===== 1. 解析 CmdMessage =====
        CmdMessage cmd = parse(data);
        if (cmd == null) {
            log.warn("[CMD] 消息体为空或解析失败: room={}", roomSessionId);
            return;
        }

        // ===== 2. attemptId 校验（极早退，用旧广播） =====
        SessionRoom room = roomRegistry.get(roomSessionId).orElse(null);
        if (room == null) {
            log.warn("[CMD] room 不存在: room={}", roomSessionId);
            return;
        }
        String roomAttemptId = room.getAttemptId();
        if (roomAttemptId == null || roomAttemptId.isBlank()) {
            log.warn("[CMD] room 未记录 attemptId: room={}", roomSessionId);
            return;
        }
        if (cmd.getAttemptId() == null || !cmd.getAttemptId().equals(roomAttemptId)) {
            log.warn("[CMD] attemptId 不匹配: msg={}, room={}",
                cmd.getAttemptId(), roomAttemptId);
            broadcastToRoomLegacy(roomSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "attemptId 不匹配"));
            return;
        }

        // ===== 3. emsSessionId 解析（极早退，用旧广播） =====
        String emsSessionId = resolveEmsSessionId(room, data);
        if (emsSessionId == null) {
            log.warn("[CMD] emsSessionId 解析失败: room={}", roomSessionId);
            broadcastToRoomLegacy(roomSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "emsSessionId 缺失"));
            return;
        }

        // ===== 4. rawInput 非空（走 ProtocolBridgeService） =====
        if (cmd.getRawInput() == null || cmd.getRawInput().isBlank()) {
            log.warn("[CMD] rawInput 为空: room={}, attemptId={}",
                roomSessionId, roomAttemptId);
            pushCmdResult(emsSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "rawInput 为空"));
            return;
        }

        // ===== 4.5 A-6 timeBasis 强制校验 =====
        String timeBasis = cmd.getTimeBasis();
        if (timeBasis == null || timeBasis.isBlank()) {
            log.warn("[CMD] timeBasis 缺失: room={}, attemptId={}", roomSessionId, roomAttemptId);
            pushCmdResult(emsSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.SCHEMA_REJECTED, "timeBasis 必填（simulated 或 real）"));
            return;
        }
        if (!VALID_TIME_BASIS.contains(timeBasis)) {
            log.warn("[CMD] timeBasis 非法: room={}, attemptId={}, value={}",
                roomSessionId, roomAttemptId, timeBasis);
            pushCmdResult(emsSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.SCHEMA_REJECTED,
                "timeBasis 非法（必须为 simulated 或 real），实际=" + timeBasis));
            return;
        }
        if ("real".equals(timeBasis)) {
            if (cmd.getRealTimeRationale() == null || cmd.getRealTimeRationale().isBlank()) {
                log.warn("[CMD] real 缺少 realTimeRationale: room={}, attemptId={}",
                    roomSessionId, roomAttemptId);
                pushCmdResult(emsSessionId, buildError(cmd, roomAttemptId,
                    ErrorCode.SCHEMA_REJECTED, "timeBasis=real 必须带 realTimeRationale"));
                return;
            }
            if (!Boolean.TRUE.equals(cmd.getPauseSensitive())) {
                log.warn("[CMD] real 未声明 pauseSensitive=true: room={}, attemptId={}",
                    roomSessionId, roomAttemptId);
                pushCmdResult(emsSessionId, buildError(cmd, roomAttemptId,
                    ErrorCode.SCHEMA_REJECTED, "timeBasis=real 必须 pauseSensitive=true"));
                return;
            }
        }

        // ===== 5. A-2 幂等检查 =====
        String idemKey = cmd.getIdempotencyKey();
        if (idemKey != null && !idemKey.isBlank()) {
            CommandResultMessage cached = idempotencyCache.get(roomAttemptId, idemKey);
            if (cached != null) {
                log.info("♻️ [CMD] 幂等命中: attemptId={}, idem={}", roomAttemptId, idemKey);
                CommandResultMessage replay = CommandResultMessage.builder()
                    .attemptId(roomAttemptId)
                    .errorCode(ErrorCode.IDEMPOTENT_REPLAY.name())
                    .errorMessage("幂等重放")
                    .replayedIdempotencyKey(idemKey)
                    .decision(cached.getDecision())
                    .primaryRuleId(cached.getPrimaryRuleId())
                    .stateRevision(cached.getStateRevision())
                    .serverSequence(cached.getServerSequence())
                    .build();
                pushCmdResult(emsSessionId, replay);
                return;
            }
        }

        // ===== 6. A-3 序列检查 =====
        long clientSeq = cmd.getClientSequence();
        if (clientSeq > 0) {
            long lastSeq = room.getLastClientSequence();
            if (lastSeq == 0) {
                room.setLastClientSequence(clientSeq);
                log.info("🔢 [CMD] 首包 clientSeq={}, attemptId={}", clientSeq, roomAttemptId);
            } else if (clientSeq == lastSeq + 1) {
                room.setLastClientSequence(clientSeq);
            } else if (clientSeq <= lastSeq) {
                log.warn("⚠️ [CMD] clientSeq 重复或乱序: attemptId={}, clientSeq={}, last={}",
                    roomAttemptId, clientSeq, lastSeq);
                CommandResultMessage err = buildError(cmd, roomAttemptId,
                    ErrorCode.SEQUENCE_GAP,
                    String.format("clientSeq=%d 重复或乱序（last=%d）", clientSeq, lastSeq));
                attachFullSnapshot(err, emsSessionId, roomAttemptId);
                pushCmdResult(emsSessionId, err);
                return;
            } else {
                log.warn("⚠️ [CMD] clientSeq 断档: attemptId={}, clientSeq={}, last={}",
                    roomAttemptId, clientSeq, lastSeq);
                // ★ 批 2：断档附全量快照；lastSeq 不动（允许客户端补发）
                CommandResultMessage err = buildError(cmd, roomAttemptId,
                    ErrorCode.SEQUENCE_GAP,
                    String.format("clientSeq=%d 断档（last=%d）", clientSeq, lastSeq));
                attachFullSnapshot(err, emsSessionId, roomAttemptId);
                pushCmdResult(emsSessionId, err);
                return;
            }
        }

        // ===== 7. A-4 STALE_REVISION 校验 =====
        Long expectedRev = cmd.getExpectedStateRevision();
        if (expectedRev != null && expectedRev > 0) {
            long currentRev = resolveCurrentRevision(emsSessionId);
            if (currentRev > 0 && expectedRev < currentRev) {
                log.warn("⚠️ [CMD] STALE_REVISION: attemptId={}, expected={}, current={}",
                    roomAttemptId, expectedRev, currentRev);
                CommandResultMessage err = buildError(cmd, roomAttemptId,
                    ErrorCode.STALE_REVISION,
                    String.format("期望修订 %d 落后于当前 %d", expectedRev, currentRev));
                err.setStateRevision(currentRev);
                // ★ 批 2：附全量快照，客户端一次性同步
                attachFullSnapshot(err, emsSessionId, roomAttemptId);
                pushCmdResult(emsSessionId, err);
                return;
            }
        }

        log.info("📥 [CMD] room={}, ems={}, attemptId={}, clientSeq={}, expectedRev={}, timeBasis={}, idem={}, intervention={}, raw='{}'",
            roomSessionId, emsSessionId, roomAttemptId,
            clientSeq, expectedRev, timeBasis, idemKey,
            cmd.getInterventionId(), cmd.getRawInput());

        // ===== 8. 串行提交 =====
        CommandTask task = new CommandTask(
            emsSessionId,
            cmd.getRawInput(),
            null,
            System.currentTimeMillis(),
            0
        );

        final String finalIdemKey = idemKey;
        final String finalAttemptId = roomAttemptId;
        final String finalEmsSessionId = emsSessionId;

        dispatcher.submit(task, t -> {
            try {
                ExecutionOutcome outcome = sessionService.executeCommandSyncStructured(t);

                CommandResultMessage result = outcome.isSuccess()
                    ? buildSuccess(cmd, finalAttemptId, finalEmsSessionId, outcome)
                    : buildError(cmd, finalAttemptId, finalEmsSessionId, outcome);

                if (finalIdemKey != null && !finalIdemKey.isBlank()) {
                    idempotencyCache.put(finalAttemptId, finalIdemKey, result);
                }

                pushCmdResult(finalEmsSessionId, result);
                return outcome.isSuccess()
                    ? outcome.getResponseText()
                    : outcome.getErrorMessage();
            } catch (Exception e) {
                log.error("[CMD] 执行异常: ems={}, attemptId={}",
                    t.getSessionId(), finalAttemptId, e);
                CommandResultMessage err = CommandResultMessage.builder()
                    .attemptId(finalAttemptId)
                    .errorCode(ErrorCode.SCHEMA_REJECTED.name())
                    .errorMessage("执行异常: " + e.getMessage())
                    .decision("BLOCK")
                    .severity("error")
                    .allMatchedRuleIds(List.of())
                    .feedbackKeys(List.of())
                    .assessmentTags(List.of())
                    .build();
                pushCmdResult(finalEmsSessionId, err);
                return "ERROR: " + e.getMessage();
            }
        });
    }

    // ==================== 结果组装 ====================

    private CommandResultMessage buildSuccess(CmdMessage cmd, String attemptId,
                                              String emsSessionId, ExecutionOutcome outcome) {
        CommandResultMessage.CommandResultMessageBuilder b = CommandResultMessage.builder()
            .attemptId(attemptId);

        ValidationResult vr = outcome.getValidation();
        if (vr != null) {
            b.decision(vr.getDecision() != null ? vr.getDecision().name() : "ALLOW")
                .severity(vr.getSeverity() != null ? vr.getSeverity() : "none")
                .primaryRuleId(vr.getPrimaryRuleId())
                .allMatchedRuleIds(vr.getAllMatchedRuleIds() != null
                    ? vr.getAllMatchedRuleIds() : List.of())
                .feedbackKeys(vr.getFeedbackKeys() != null
                    ? vr.getFeedbackKeys() : List.of())
                .assessmentTags(vr.getAssessmentTags() != null
                    ? vr.getAssessmentTags() : List.of());
        } else {
            b.decision("ALLOW")
                .severity("none")
                .allMatchedRuleIds(List.of())
                .feedbackKeys(List.of())
                .assessmentTags(List.of());
        }

        try {
            var ctx = contextManager.getSnapshot(emsSessionId);
            if (ctx != null) {
                b.stateRevision(ctx.getVersion());
                b.eventSequence(ctx.getEventSequence());
            }
        } catch (Exception ignored) {}

        return b.build();
    }

    private CommandResultMessage buildError(CmdMessage cmd, String attemptId,
                                            String emsSessionId, ExecutionOutcome outcome) {
        ErrorCode code = mapFailureKindToErrorCode(outcome.getFailureKind());

        CommandResultMessage.CommandResultMessageBuilder b = CommandResultMessage.builder()
            .attemptId(attemptId)
            .errorCode(code.name())
            .errorMessage(outcome.getErrorMessage() != null
                ? outcome.getErrorMessage() : "执行失败")
            .decision("BLOCK");

        ValidationResult vr = outcome.getValidation();
        if (vr != null) {
            b.severity(vr.getSeverity() != null ? vr.getSeverity() : "error")
                .primaryRuleId(vr.getPrimaryRuleId())
                .allMatchedRuleIds(vr.getAllMatchedRuleIds() != null
                    ? vr.getAllMatchedRuleIds() : List.of())
                .feedbackKeys(vr.getFeedbackKeys() != null
                    ? vr.getFeedbackKeys() : List.of())
                .assessmentTags(vr.getAssessmentTags() != null
                    ? vr.getAssessmentTags() : List.of());
        } else {
            b.severity("error")
                .allMatchedRuleIds(List.of())
                .feedbackKeys(List.of())
                .assessmentTags(List.of());
        }

        try {
            var ctx = contextManager.getSnapshot(emsSessionId);
            if (ctx != null) {
                b.stateRevision(ctx.getVersion());
                b.eventSequence(ctx.getEventSequence());
            }
        } catch (Exception ignored) {}

        return b.build();
    }

    /** 参数路径专用 buildError（保留旧签名，供早退分支调用）。 */
    private CommandResultMessage buildError(CmdMessage cmd, String attemptId,
                                            ErrorCode code, String reason) {
        return CommandResultMessage.builder()
            .attemptId(attemptId)
            .errorCode(code.name())
            .errorMessage(reason)
            .decision("BLOCK")
            .severity("error")
            .allMatchedRuleIds(List.of())
            .feedbackKeys(List.of())
            .assessmentTags(List.of())
            .build();
    }

    private ErrorCode mapFailureKindToErrorCode(String failureKind) {
        if (failureKind == null) return ErrorCode.SCHEMA_REJECTED;
        return switch (failureKind) {
            case "TUTOR_BLOCK", "NO_MATCH", "ARBITER_BLOCK" -> ErrorCode.BLOCKED_BY_RULE;
            case "EXCEPTION" -> ErrorCode.SCHEMA_REJECTED;
            default -> ErrorCode.SCHEMA_REJECTED;
        };
    }

    // ==================== 下行 ====================

    /**
     * ★ 批 2：统一走 ProtocolBridgeService，serverSequence 由 AttemptSequencer 填充。
     */
    private void pushCmdResult(String emsSessionId, CommandResultMessage msg) {
        if (msg == null) return;
        try {
            protocolBridgeService.pushCommandResult(emsSessionId, msg);
        } catch (Exception e) {
            log.warn("[CMD] pushCommandResult 失败: ems={}, err={}",
                emsSessionId, e.getMessage());
        }
    }

    /**
     * ★ 批 2：极早退阶段（attemptId/emsSessionId 未就绪）的旧广播路径。
     * 用 roomSessionId 直接广播，不经过 ProtocolBridgeService。
     */
    private void broadcastToRoomLegacy(String roomSessionId, CommandResultMessage msg) {
        if (msg == null) return;
        try {
            Map<String, Object> payload = RoomBroadcaster.envelope("CMD_RESULT", msg);
            broadcaster.broadcast(roomSessionId, payload);
        } catch (Exception e) {
            log.warn("[CMD] CMD_RESULT 极早退回推失败: room={}, err={}",
                roomSessionId, e.getMessage());
        }
    }

    /**
     * ★ 批 2：给错误结果附全量快照（STALE_REVISION / SEQUENCE_GAP 用）。
     * 快照构建失败不阻断主流程。
     */
    private void attachFullSnapshot(CommandResultMessage msg,
                                    String emsSessionId, String attemptId) {
        if (msg == null) return;
        try {
            SnapshotMessage snapshot =
                attemptSnapshotService.buildFullSnapshot(emsSessionId, attemptId);
            if (snapshot != null) {
                msg.setSnapshot(snapshot);
            } else {
                log.warn("[CMD] 全量快照为空: ems={}, attemptId={}",
                    emsSessionId, attemptId);
            }
        } catch (Exception e) {
            log.warn("[CMD] 构建全量快照失败: ems={}, err={}",
                emsSessionId, e.getMessage());
        }
    }

    // ==================== 辅助 ====================

    private long resolveCurrentRevision(String emsSessionId) {
        try {
            var ctx = contextManager.getSnapshot(emsSessionId);
            if (ctx == null) return -1L;
            return ctx.getVersion();
        } catch (Exception e) {
            log.warn("[CMD] 获取 stateRevision 失败: ems={}, err={}",
                emsSessionId, e.getMessage());
            return -1L;
        }
    }

    private CmdMessage parse(Map<String, Object> data) {
        if (data == null) return null;
        CmdMessage cmd = new CmdMessage();
        cmd.setAttemptId(asString(data.get("attemptId")));
        cmd.setClientSequence(asLong(data.get("clientSequence"), 0L));
        cmd.setIdempotencyKey(asString(data.get("idempotencyKey")));
        cmd.setExpectedStateRevision(asLongObj(data.get("expectedStateRevision")));
        cmd.setInterventionId(asString(data.get("interventionId")));
        cmd.setRawInput(asString(data.get("rawInput")));
        cmd.setTimeBasis(asString(data.get("timeBasis")));
        cmd.setRealTimeRationale(asString(data.get("realTimeRationale")));
        cmd.setPauseSensitive(asBoolean(data.get("pauseSensitive")));
        Object params = data.get("parameters");
        if (params instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) m;
            cmd.setParameters(typed);
        }
        return cmd;
    }

    private String resolveEmsSessionId(SessionRoom room, Map<String, Object> data) {
        if (data != null) {
            Object id = data.get("emsSessionId");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        }
        return room.getEmsSessionId();
    }

    private static String asString(Object v) { return v == null ? null : String.valueOf(v); }
    private static long asLong(Object v, long def) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return def;
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return def; }
    }
    private static Long asLongObj(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }
    private static Boolean asBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return null;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}

package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.dispatcher.SessionCommandDispatcher;
import com.ruoyi.ems.domain.dto.CommandTask;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.protocol.ErrorCode;
import com.ruoyi.ems.room.protocol.IdempotencyCache;
import com.ruoyi.ems.room.protocol.dto.CmdMessage;
import com.ruoyi.ems.room.protocol.dto.CmdResultMessage;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约 V1.0.0 §5 —— 命令协议入口。
 *
 * <p><b>已完成：</b></p>
 * <ul>
 *   <li>A-1：注册 {@code type=CMD}，解析、attemptId 校验、串行提交</li>
 *   <li>A-2：幂等（§5.4 / §6 IDEMPOTENT_REPLAY）</li>
 *   <li>A-3：序列断档（§5.4 / §6 SEQUENCE_GAP）</li>
 *   <li>A-4：过期修订（§5.4 / §6 STALE_REVISION）</li>
 *   <li>A-5：{@code CMD_RESULT} 组装并回推</li>
 *   <li>A-6：{@code timeBasis} 强制校验（§5.4 / §6 SCHEMA_REJECTED）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmdMessageHandler implements WsMessageRouter.WsMessageHandler {

    private static final String TYPE = "CMD";

    /** A-6：允许的 timeBasis 枚举值 */
    private static final Set<String> VALID_TIME_BASIS = Set.of("simulated", "real");

    private final RoomRegistry roomRegistry;
    private final SessionService sessionService;
    private final SessionCommandDispatcher dispatcher;
    private final IdempotencyCache idempotencyCache;
    private final EmsContextManager contextManager;
    private final RoomBroadcaster broadcaster;

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

        // ===== 2. attemptId 校验 =====
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
            broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "attemptId 不匹配"));
            return;
        }

        // ===== 3. emsSessionId 解析 =====
        String emsSessionId = resolveEmsSessionId(room, data);
        if (emsSessionId == null) {
            log.warn("[CMD] emsSessionId 解析失败: room={}", roomSessionId);
            broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "emsSessionId 缺失"));
            return;
        }

        // ===== 4. rawInput 非空 =====
        if (cmd.getRawInput() == null || cmd.getRawInput().isBlank()) {
            log.warn("[CMD] rawInput 为空: room={}, attemptId={}",
                roomSessionId, roomAttemptId);
            broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "rawInput 为空"));
            return;
        }

        // ===== 4.5 A-6 timeBasis 强制校验（契约 §5.4） =====
        String timeBasis = cmd.getTimeBasis();
        if (timeBasis == null || timeBasis.isBlank()) {
            log.warn("[CMD] timeBasis 缺失: room={}, attemptId={}", roomSessionId, roomAttemptId);
            broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.SCHEMA_REJECTED, "timeBasis 必填（simulated 或 real）"));
            return;
        }
        if (!VALID_TIME_BASIS.contains(timeBasis)) {
            log.warn("[CMD] timeBasis 非法: room={}, attemptId={}, value={}",
                roomSessionId, roomAttemptId, timeBasis);
            broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                ErrorCode.SCHEMA_REJECTED,
                "timeBasis 非法（必须为 simulated 或 real），实际=" + timeBasis));
            return;
        }
        if ("real".equals(timeBasis)) {
            if (cmd.getRealTimeRationale() == null || cmd.getRealTimeRationale().isBlank()) {
                log.warn("[CMD] real 缺少 realTimeRationale: room={}, attemptId={}",
                    roomSessionId, roomAttemptId);
                broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                    ErrorCode.SCHEMA_REJECTED, "timeBasis=real 必须带 realTimeRationale"));
                return;
            }
            if (!Boolean.TRUE.equals(cmd.getPauseSensitive())) {
                log.warn("[CMD] real 未声明 pauseSensitive=true: room={}, attemptId={}",
                    roomSessionId, roomAttemptId);
                broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                    ErrorCode.SCHEMA_REJECTED, "timeBasis=real 必须 pauseSensitive=true"));
                return;
            }
        }

        // ===== 5. A-2 幂等检查 =====
        String idemKey = cmd.getIdempotencyKey();
        if (idemKey != null && !idemKey.isBlank()) {
            CmdResultMessage cached = idempotencyCache.get(roomAttemptId, idemKey);
            if (cached != null) {
                log.info("♻️ [CMD] 幂等命中: attemptId={}, idem={}", roomAttemptId, idemKey);
                CmdResultMessage replay = new CmdResultMessage();
                replay.setAttemptId(roomAttemptId);
                replay.setErrorCode(ErrorCode.IDEMPOTENT_REPLAY.name());
                replay.setErrorMessage("幂等重放");
                replay.setReplayedIdempotencyKey(idemKey);
                replay.setDecision(cached.getDecision());
                replay.setPrimaryRuleId(cached.getPrimaryRuleId());
                replay.setStateRevision(cached.getStateRevision());
                replay.setServerSequence(cached.getServerSequence());
                broadcast(roomSessionId, replay);
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
                broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                    ErrorCode.SEQUENCE_GAP,
                    String.format("clientSeq=%d 重复或乱序（last=%d）", clientSeq, lastSeq)));
                return;
            } else {
                log.warn("⚠️ [CMD] clientSeq 断档: attemptId={}, clientSeq={}, last={}",
                    roomAttemptId, clientSeq, lastSeq);
                broadcast(roomSessionId, buildError(cmd, roomAttemptId,
                    ErrorCode.SEQUENCE_GAP,
                    String.format("clientSeq=%d 断档（last=%d）", clientSeq, lastSeq)));
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
                CmdResultMessage err = buildError(cmd, roomAttemptId,
                    ErrorCode.STALE_REVISION,
                    String.format("期望修订 %d 落后于当前 %d", expectedRev, currentRev));
                err.setStateRevision(currentRev);
                broadcast(roomSessionId, err);
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
        final String finalRoomId = roomSessionId;

        dispatcher.submit(task, t -> {
            try {
                String raw = sessionService.executeCommandSync(t);

                CmdResultMessage result = buildSuccess(cmd, finalAttemptId, emsSessionId, raw);

                if (finalIdemKey != null && !finalIdemKey.isBlank()) {
                    idempotencyCache.put(finalAttemptId, finalIdemKey, result);
                }

                broadcast(finalRoomId, result);
                return raw;
            } catch (Exception e) {
                log.error("[CMD] 执行异常: ems={}, attemptId={}",
                    t.getSessionId(), finalAttemptId, e);
                CmdResultMessage err = new CmdResultMessage();
                err.setAttemptId(finalAttemptId);
                err.setErrorCode(ErrorCode.SCHEMA_REJECTED.name());
                err.setErrorMessage("执行异常: " + e.getMessage());
                broadcast(finalRoomId, err);
                return "ERROR: " + e.getMessage();
            }
        });
    }

    // ==================== A-5：结果组装 ====================

    private CmdResultMessage buildSuccess(CmdMessage cmd, String attemptId,
                                          String emsSessionId, String rawResult) {
        CmdResultMessage msg = new CmdResultMessage();
        msg.setAttemptId(attemptId);
        msg.setDecision("ALLOW");
        msg.setSeverity("none");
        msg.setAllMatchedRuleIds(List.of());
        msg.setFeedbackKeys(List.of());
        msg.setAssessmentTags(List.of());

        try {
            var ctx = contextManager.getSnapshot(emsSessionId);
            if (ctx != null) {
                msg.setStateRevision(ctx.getVersion());
                msg.setEventSequence(ctx.getEventSequence());
            }
        } catch (Exception ignored) {}

        return msg;
    }

    private CmdResultMessage buildError(CmdMessage cmd, String attemptId,
                                        ErrorCode code, String reason) {
        CmdResultMessage msg = new CmdResultMessage();
        msg.setAttemptId(attemptId);
        msg.setErrorCode(code.name());
        msg.setErrorMessage(reason);
        return msg;
    }

    private void broadcast(String roomSessionId, CmdResultMessage msg) {
        if (msg == null) return;
        try {
            Map<String, Object> payload = RoomBroadcaster.envelope("CMD_RESULT", msg);
            broadcaster.broadcast(roomSessionId, payload);
            log.debug("📤 [CMD_RESULT] room={}, attemptId={}, errorCode={}, decision={}",
                roomSessionId, msg.getAttemptId(), msg.getErrorCode(), msg.getDecision());
        } catch (Exception e) {
            log.warn("[CMD] CMD_RESULT 回推失败: room={}, err={}",
                roomSessionId, e.getMessage());
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

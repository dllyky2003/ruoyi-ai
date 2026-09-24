package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.audit.AttemptLogService;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.protocol.ErrorCode;
import com.ruoyi.ems.room.protocol.dto.OverrideMessage;
import com.ruoyi.ems.room.protocol.dto.OverrideResultMessage;
import com.ruoyi.ems.room.protocol.dto.ProposedEffect;
import com.ruoyi.ems.room.protocol.effect.ControlledEffect;
import com.ruoyi.ems.room.protocol.effect.ControlledEffectRegistry;
import com.ruoyi.ems.room.protocol.effect.EffectContext;
import com.ruoyi.ems.room.protocol.effect.EffectResult;
import com.ruoyi.ems.room.registry.RoomRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 契约 V1.0.0 §7 —— 导师覆盖协议入口。
 *
 * <p><b>已完成：</b></p>
 * <ul>
 *   <li>OVR-1：注册 {@code type=OVR}；attemptId 校验；overrideId 白名单；
 *       reason 强制；{@code expectedStateRevision} 校验</li>
 *   <li>OVR-2a：映射到 {@link ControlledEffectRegistry} 中已注册的受控效果，
 *       未注册效果拒绝（§7.2 "不能直接写 ClinicalState"）</li>
 *   <li>OVR-2b：{@code pause_clock} 真实生效；{@code force_sync_mode} 占位</li>
 *   <li>OVR-3：审计写入（JSONL，弱一致，失败不影响 OVR 结果）</li>
 * </ul>
 *
 * <p><b>后续阶段：</b></p>
 * <ul>
 *   <li>OVR-4：评分中断（assessment_interruption）</li>
 *   <li>OVR-5：暂停语义接入（§10.3 systemStatus.paused）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverrideHandler implements WsMessageRouter.WsMessageHandler {

    private static final String TYPE = "OVR";

    /** 契约 §7.3 阶段 5 覆盖清单 */
    private static final Set<String> APPROVED_OVERRIDES = Set.of(
        "IO_PAUSE_CLOCK",
        "IO_FORCE_SYNC_MODE"
    );

    /**
     * 契约 §7.2 —— 覆盖 ID → 受控 effectId 的权威映射。
     *
     * <p>服务端以此为权威，{@code proposedEffect.effectId} 只作参考。</p>
     */
    private static final Map<String, String> OVERRIDE_TO_EFFECT = Map.of(
        "IO_PAUSE_CLOCK",       "pause_clock",
        "IO_FORCE_SYNC_MODE",   "force_sync_mode"
    );

    private final RoomRegistry roomRegistry;
    private final EmsContextManager contextManager;
    private final RoomBroadcaster broadcaster;
    private final ControlledEffectRegistry effectRegistry;
    private final AttemptLogService attemptLogService;

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public void handle(WsMessageContext ctx) {
        String roomSessionId = ctx.sessionId();
        Map<String, Object> data = ctx.data();

        // ===== 1. 解析 =====
        OverrideMessage ovr = parse(data);
        if (ovr == null) {
            log.warn("[OVR] 消息体为空或解析失败: room={}", roomSessionId);
            return;
        }

        // ===== 2. room + attemptId 校验 =====
        SessionRoom room = roomRegistry.get(roomSessionId).orElse(null);
        if (room == null) {
            log.warn("[OVR] room 不存在: room={}", roomSessionId);
            return;
        }
        String roomAttemptId = room.getAttemptId();
        if (roomAttemptId == null || roomAttemptId.isBlank()) {
            log.warn("[OVR] room 未记录 attemptId: room={}", roomSessionId);
            return;
        }
        if (ovr.getAttemptId() == null || !ovr.getAttemptId().equals(roomAttemptId)) {
            log.warn("[OVR] attemptId 不匹配: msg={}, room={}",
                ovr.getAttemptId(), roomAttemptId);
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "attemptId 不匹配"));
            return;
        }

        // ===== 3. overrideId 白名单（§7.3） =====
        String overrideId = ovr.getOverrideId();
        if (overrideId == null || overrideId.isBlank()) {
            log.warn("[OVR] overrideId 缺失: room={}", roomSessionId);
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "overrideId 必填"));
            return;
        }
        if (!APPROVED_OVERRIDES.contains(overrideId)) {
            log.warn("[OVR] 未授权的覆盖: room={}, overrideId={}",
                roomSessionId, overrideId);
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.UNAUTHORIZED_OVERRIDE,
                "未授权的覆盖 ID: " + overrideId));
            return;
        }

        // ===== 4. reason 强制（§7.2） =====
        if (ovr.getReason() == null || ovr.getReason().isBlank()) {
            log.warn("[OVR] reason 缺失: room={}, overrideId={}",
                roomSessionId, overrideId);
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.INVALID_PARAMETER, "reason 必填"));
            return;
        }

        // ===== 5. expectedStateRevision 校验 =====
        String emsSessionId = room.getEmsSessionId();
        Long expectedRev = ovr.getExpectedStateRevision();
        if (expectedRev != null && expectedRev > 0) {
            long currentRev = resolveCurrentRevision(emsSessionId);
            if (currentRev > 0 && expectedRev < currentRev) {
                log.warn("[OVR] STALE_REVISION: room={}, overrideId={}, expected={}, current={}",
                    roomSessionId, overrideId, expectedRev, currentRev);
                OverrideResultMessage err = buildError(ovr, roomAttemptId,
                    ErrorCode.STALE_REVISION,
                    String.format("期望修订 %d 落后于当前 %d", expectedRev, currentRev));
                err.setStateRevision(currentRev);
                broadcast(roomSessionId, err);
                return;
            }
        }

        // ===== 6. OVR-2a：映射到受控效果 =====
        String effectId = OVERRIDE_TO_EFFECT.get(overrideId);
        if (effectId == null) {
            log.warn("[OVR] overrideId 无对应 effectId 映射: room={}, overrideId={}",
                roomSessionId, overrideId);
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.UNAUTHORIZED_OVERRIDE,
                "覆盖 ID 无受控效果映射: " + overrideId));
            return;
        }
        ControlledEffect effect = effectRegistry.get(effectId);
        if (effect == null) {
            log.warn("[OVR] 受控效果未注册: room={}, overrideId={}, effectId={}",
                roomSessionId, overrideId, effectId);
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.UNAUTHORIZED_OVERRIDE,
                "受控效果未注册: " + effectId));
            return;
        }

        log.info("🎛️ [OVR] room={}, ems={}, attemptId={}, overrideId={}, effectId={}, reason='{}', expectedRev={}, auditRequired={}, triggersInterruption={}",
            roomSessionId, emsSessionId, roomAttemptId,
            overrideId, effectId, ovr.getReason(), expectedRev,
            ovr.isAuditRequired(),
            ovr.isTriggersAssessmentInterruption());

        // ===== 7. 执行受控效果 =====
        EffectResult result;
        try {
            EffectContext effCtx = EffectContext.builder()
                .roomSessionId(roomSessionId)
                .emsSessionId(emsSessionId)
                .attemptId(roomAttemptId)
                .overrideId(overrideId)
                .reason(ovr.getReason())
                .proposedEffect(ovr.getProposedEffect())
                .triggersAssessmentInterruption(ovr.isTriggersAssessmentInterruption())
                .build();
            result = effect.apply(effCtx);
        } catch (Exception e) {
            log.error("[OVR] 受控效果执行异常: room={}, overrideId={}, effectId={}",
                roomSessionId, overrideId, effectId, e);
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.SCHEMA_REJECTED,
                "效果执行异常: " + e.getMessage()));
            return;
        }

        if (result == null || !result.isAccepted()) {
            broadcast(roomSessionId, buildError(ovr, roomAttemptId,
                ErrorCode.SCHEMA_REJECTED,
                result == null ? "效果未返回结果" : result.getMessage()));
            return;
        }

        // ===== 8. 回推 OVR_RESULT =====
        OverrideResultMessage ok = new OverrideResultMessage();
        ok.setAttemptId(roomAttemptId);
        ok.setOverrideId(overrideId);
        ok.setAccepted(true);
        ok.setMessage(result.getMessage());
        ok.setStateRevision(result.getStateRevision());
        broadcast(roomSessionId, ok);

        // ===== 9. OVR-3：审计写入（弱一致，失败不回滚 OVR） =====
        if (ovr.isAuditRequired()) {
            try {
                attemptLogService.recordOverride(
                    roomAttemptId,
                    roomSessionId,
                    emsSessionId,
                    overrideId,
                    effectId,
                    ovr.getReason(),
                    result.getStateRevision(),
                    ovr.getProposedEffect()
                );
            } catch (Exception e) {
                log.error("🚨 [OVR-3] 审计写入失败（OVR 仍生效）: attemptId={}, overrideId={}",
                    roomAttemptId, overrideId, e);
            }
        } else {
            log.debug("🔕 [OVR-3] auditRequired=false，跳过审计写入: attemptId={}, overrideId={}",
                roomAttemptId, overrideId);
        }
    }

    // ==================== 辅助 ====================

    private long resolveCurrentRevision(String emsSessionId) {
        try {
            var ctx = contextManager.getSnapshot(emsSessionId);
            if (ctx == null) return -1L;
            return ctx.getVersion();
        } catch (Exception e) {
            log.warn("[OVR] 获取 stateRevision 失败: ems={}, err={}",
                emsSessionId, e.getMessage());
            return -1L;
        }
    }

    private OverrideMessage parse(Map<String, Object> data) {
        if (data == null) return null;

        OverrideMessage ovr = new OverrideMessage();

        ovr.setAttemptId(asString(data.get("attemptId")));
        ovr.setOverrideId(asString(data.get("overrideId")));
        ovr.setReason(asString(data.get("reason")));

        Long expectedRev = asLongObj(data.get("expectedStateRevision"));
        ovr.setExpectedStateRevision(expectedRev != null ? expectedRev : 0L);

        Boolean auditRequired = asBoolean(data.get("auditRequired"));
        ovr.setAuditRequired(auditRequired != null ? auditRequired : true);

        Boolean triggers = asBoolean(data.get("triggersAssessmentInterruption"));
        ovr.setTriggersAssessmentInterruption(triggers != null && triggers);

        Object effect = data.get("proposedEffect");
        if (effect instanceof Map<?, ?> m) {
            ProposedEffect pe = new ProposedEffect();
            pe.setEffectId(asString(m.get("effectId")));

            Boolean resolved = asBoolean(m.get("resolved"));
            pe.setResolved(resolved != null && resolved);

            ovr.setProposedEffect(pe);
        }

        return ovr;
    }

    private OverrideResultMessage buildError(OverrideMessage ovr, String attemptId,
                                             ErrorCode code, String reason) {
        OverrideResultMessage msg = new OverrideResultMessage();
        msg.setAttemptId(attemptId);
        msg.setOverrideId(ovr != null ? ovr.getOverrideId() : null);
        msg.setAccepted(false);
        msg.setErrorCode(code.name());
        msg.setErrorMessage(reason);
        return msg;
    }

    private void broadcast(String roomSessionId, OverrideResultMessage msg) {
        if (msg == null) return;
        try {
            Map<String, Object> payload = RoomBroadcaster.envelope("OVR_RESULT", msg);
            broadcaster.broadcast(roomSessionId, payload);
            log.debug("📤 [OVR_RESULT] room={}, attemptId={}, overrideId={}, accepted={}, errorCode={}",
                roomSessionId, msg.getAttemptId(), msg.getOverrideId(),
                msg.getAccepted(), msg.getErrorCode());
        } catch (Exception e) {
            log.warn("[OVR] OVR_RESULT 回推失败: room={}, err={}",
                roomSessionId, e.getMessage());
        }
    }

    private static String asString(Object v) { return v == null ? null : String.valueOf(v); }

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

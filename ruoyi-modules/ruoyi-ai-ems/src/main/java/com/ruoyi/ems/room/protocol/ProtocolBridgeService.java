package com.ruoyi.ems.room.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.protocol.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契约层下行统一出口。
 *
 * <p>与 {@code NpcUnityBridgeService} 平行：NPC 走那一条，契约消息走这一条。
 * 共用 {@link RoomBroadcaster} 通道，共用 emsSessionId → roomSessionId 映射逻辑。</p>
 *
 * <p><b>修复 B-1（2026-09-25）</b>：新增 {@link #pushIncrementalSnapshotForEms}，</p>
 *
 * <p><b>阶段 3 评价报告（2026-09-25）</b>：新增 {@link #pushReport} 与
 * {@link #getManifestHash}；{@link #pushManifest} 内顺带缓存 manifestHash，
 * 供报告生成时读取，避免 ems 侧反向依赖 tutor 类型。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolBridgeService {

    private final RoomBroadcaster broadcaster;
    private final RoomRegistry roomRegistry;
    private final AttemptSequencer sequencer;
    private final AttemptSnapshotService attemptSnapshotService;
    private final ObjectMapper objectMapper;   // ★ 新增：解析 manifest payload 拿 hash

    /** ★ 新增：emsSessionId -> manifestHash，供报告生成时读取。 */
    private final ConcurrentHashMap<String, String> manifestHashCache = new ConcurrentHashMap<>();

    // ========== SNAPSHOT ==========
    public void pushSnapshot(String emsSessionId, SnapshotMessage snapshot) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) {
            log.warn("⚠️ SNAPSHOT 下行跳过：找不到房间映射 emsSessionId={}", emsSessionId);
            return;
        }

        long seq = sequencer.advance(emsSessionId);
        snapshot.setServerSequence(seq);

        Map<String, Object> env = RoomBroadcaster.envelope("SNAPSHOT", snapshot);
        broadcaster.sendToRole(roomSessionId, Role.ENGINE, env);

        log.info("📤 SNAPSHOT: ems={}, rev={}, seq={}, full={}",
            emsSessionId, snapshot.getStateRevision(), seq, snapshot.isFullSnapshot());
    }

    /**
     * ★ 修复 B-1（2026-09-25）：从 emsSessionId 反查 room / attempt，推送增量快照。
     */
    public void pushIncrementalSnapshotForEms(String emsSessionId, long stateRevision) {
        if (emsSessionId == null) return;

        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) {
            log.warn("⚠️ 增量快照跳过：找不到房间映射 emsSessionId={}", emsSessionId);
            return;
        }

        String attemptId = roomRegistry.get(roomSessionId)
            .map(SessionRoom::getAttemptId)
            .orElse(null);
        if (attemptId == null) {
            log.warn("⚠️ 增量快照跳过：attemptId 缺失 room={}", roomSessionId);
            return;
        }

        try {
            SnapshotMessage snapshot = attemptSnapshotService
                .buildIncrementalSnapshot(emsSessionId, attemptId);
            if (snapshot == null) {
                log.debug("增量快照为空，跳过: ems={}", emsSessionId);
                return;
            }
            snapshot.setStateRevision(stateRevision);
            pushSnapshot(emsSessionId, snapshot);
        } catch (Exception e) {
            log.warn("构建增量快照失败: ems={}, err={}", emsSessionId, e.getMessage());
        }
    }

    // ========== CMD_RESULT ==========
    public void pushCommandResult(String emsSessionId, CommandResultMessage result) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) return;

        long seq = sequencer.advance(emsSessionId);
        result.setServerSequence(seq);

        Map<String, Object> env = RoomBroadcaster.envelope("CMD_RESULT", result);
        broadcaster.sendToRole(roomSessionId, Role.ENGINE, env);

        log.info("📤 CMD_RESULT: ems={}, decision={}, seq={}",
            emsSessionId, result.getDecision(), seq);
    }

    // ========== ATTEMPT_MANIFEST ==========
    /**
     * ★ 阶段 2.2：下发 AttemptManifest。
     *
     * <p>不占 serverSequence：Manifest 是"版本冻结声明"，非时序消息，
     * 与 OVR / ASSESSMENT_INTERRUPTION 同类。</p>
     *
     * <p>★ 阶段 3：顺带缓存 manifestHash，供报告生成时读取。</p>
     */
    public void pushManifest(String emsSessionId, Object manifestPayload) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) {
            log.warn("⚠️ MANIFEST 下行跳过：找不到房间映射 emsSessionId={}", emsSessionId);
            return;
        }

        // ★ 新增：缓存 manifestHash（解析 payload，容错，不阻断下发）
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.convertValue(manifestPayload, Map.class);
            Object h = m != null ? m.get("manifestHash") : null;
            if (h != null) {
                manifestHashCache.put(emsSessionId, String.valueOf(h));
                log.debug("🔖 Manifest hash 已缓存: ems={}, hash={}",
                    emsSessionId, String.valueOf(h).substring(0, Math.min(8, String.valueOf(h).length())));
            }
        } catch (Exception e) {
            log.warn("⚠️ Manifest hash 缓存失败（不阻断下发）: {}", e.getMessage());
        }

        Map<String, Object> env = RoomBroadcaster.envelope("ATTEMPT_MANIFEST", manifestPayload);
        broadcaster.sendToRole(roomSessionId, Role.ENGINE, env);

        log.info("📤 MANIFEST: ems={}", emsSessionId);
    }

    // ========== ATTEMPT_REPORT ==========
    /**
     * ★ 阶段 3：下发最终评价报告。
     *
     * <p>不占 serverSequence：与 ATTEMPT_MANIFEST 同类，是"结束声明"而非时序消息。</p>
     *
     * <p>payload 类型用 Object 承载：{@code AttemptReport} 定义在 ems 侧，
     * 但为了与 Manifest 保持对称、且允许未来由 tutor 侧组装，签名保持 Object。</p>
     */
    public void pushReport(String emsSessionId, Object reportPayload) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) {
            log.warn("⚠️ ATTEMPT_REPORT 下行跳过：找不到房间映射 emsSessionId={}", emsSessionId);
            return;
        }

        Map<String, Object> env = RoomBroadcaster.envelope("ATTEMPT_REPORT", reportPayload);
        broadcaster.sendToRole(roomSessionId, Role.ENGINE, env);

        log.info("📤 ATTEMPT_REPORT: ems={}", emsSessionId);
    }

    /**
     * ★ 阶段 3：读取已缓存的 manifestHash（供 AttemptReportService 使用）。
     * 未缓存时返回 null。
     */
    public String getManifestHash(String emsSessionId) {
        return emsSessionId == null ? null : manifestHashCache.get(emsSessionId);
    }

    // ========== ERROR ==========
    public void pushError(String emsSessionId, ErrorMessage error) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) return;

        long seq = sequencer.advance(emsSessionId);
        error.setServerSequence(seq);

        Map<String, Object> env = RoomBroadcaster.envelope("ERROR", error);
        broadcaster.sendToRole(roomSessionId, Role.ENGINE, env);

        log.warn("📤 ERROR: ems={}, code={}, seq={}", emsSessionId, error.getCode(), seq);
    }

    // ========== OVR ==========
    public void pushOverride(String emsSessionId, OverrideMessage ovr) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) return;

        Map<String, Object> env = RoomBroadcaster.envelope("OVR", ovr);
        broadcaster.broadcast(roomSessionId, env);

        log.info("📤 OVR: ems={}, overrideId={}", emsSessionId, ovr.getOverrideId());
    }

    // ========== ASSESSMENT_INTERRUPTION ==========
    public void pushInterruption(String emsSessionId, AssessmentInterruption interruption) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) return;

        Map<String, Object> env = RoomBroadcaster.envelope("ASSESSMENT_INTERRUPTION", interruption);
        broadcaster.broadcast(roomSessionId, env);

        log.info("📤 ASSESSMENT_INTERRUPTION: ems={}, id={}",
            emsSessionId, interruption.getInterruptionId());
    }

    // ========== 内部 ==========
    private String resolveRoomSessionId(String emsSessionId) {
        if (emsSessionId == null) return null;
        return roomRegistry.getByEmsSessionId(emsSessionId)
            .map(SessionRoom::getSessionId)
            .orElse(null);
    }
}

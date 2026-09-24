package com.ruoyi.ems.room.protocol;

import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.protocol.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 契约层下行统一出口。
 *
 * <p>与 {@code NpcUnityBridgeService} 平行：NPC 走那一条，契约消息走这一条。
 * 共用 {@link RoomBroadcaster} 通道，共用 emsSessionId → roomSessionId 映射逻辑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolBridgeService {

    private final RoomBroadcaster broadcaster;
    private final RoomRegistry roomRegistry;
    private final AttemptSequencer sequencer;

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

package com.ruoyi.ems.npc;

import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NpcUnityBridgeService {

    private final RoomBroadcaster broadcaster;
    private final RoomRegistry roomRegistry;   // ★ 只用来查 emsSessionId → roomSessionId

    public void pushSpawn(String emsSessionId, List<TeamMember> members) {
        List<Map<String, Object>> npcs = members.stream().map(m -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("npcId", m.getId());
            o.put("name", m.getName());
            o.put("title", m.getTitle());
            o.put("duties", m.getDuties() == null ? List.of()
                : m.getDuties().stream().map(Enum::name).toList());
            o.put("color", m.getColor());
            o.put("ttsVoice", m.getTtsVoice());
            o.put("onDuty", m.isOnDuty());
            return o;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emsSessionId", emsSessionId);
        data.put("npcs", npcs);
        broadcast(emsSessionId, "npc_spawn", data);
    }

    public void pushTask(String emsSessionId, TeamMember m, String phase, String report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emsSessionId", emsSessionId);
        data.put("npcId", m.getId());
        data.put("phase", phase);
        data.put("task", m.getCurrentTask());
        data.put("durationSeconds", m.getTaskDuration());
        data.put("progress", m.getTaskProgress());
        data.put("status", m.getStatus() == null ? null : m.getStatus().name());
        data.put("report", report);
        broadcast(emsSessionId, "npc_task", data);
    }

    public void pushDialogue(String emsSessionId, String npcId, String text,
                             String ttsVoice, boolean interrupt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emsSessionId", emsSessionId);
        data.put("npcId", npcId);
        data.put("text", text);
        data.put("ttsVoice", ttsVoice);
        data.put("emotion", "normal");
        data.put("interrupt", interrupt);
        broadcast(emsSessionId, "npc_dialogue", data);
    }

    public void pushAction(String emsSessionId, String npcId, String action,
                           Map<String, Object> params, int durationSeconds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emsSessionId", emsSessionId);
        data.put("npcId", npcId);
        data.put("action", action);
        data.put("durationSeconds", durationSeconds);
        data.put("params", params == null ? Map.of() : params);
        broadcast(emsSessionId, "npc_action", data);
    }

    public void pushDespawn(String emsSessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emsSessionId", emsSessionId);
        broadcast(emsSessionId, "npc_despawn", data);
    }

    // ==================== 内部 ====================

    /**
     * NPC 下行统一出口。
     *
     * <p>入参 {@code emsSessionId} 是业务会话 ID（形如 e16df1b1...），
     * 而 {@link RoomBroadcaster} 需要的是 WebSocket 房间 ID（形如 20260915-WLNBCPBL），
     * 两者不是同一个东西，必须在这里做一次映射。</p>
     */
    private void broadcast(String emsSessionId, String type, Map<String, Object> data) {
        String roomSessionId = resolveRoomSessionId(emsSessionId);
        if (roomSessionId == null) {
            log.warn("⚠️ NPC 下行跳过：找不到房间映射 emsSessionId={}, type={}",
                emsSessionId, type);
            return;
        }

        Map<String, Object> env = RoomBroadcaster.envelope(type, data);
        log.info("📡 NPC 下行: type={}, ems={}, room={}", type, emsSessionId, roomSessionId);

        // 想只给 Unity：broadcaster.sendToRole(roomSessionId, Role.ENGINE, env);
        broadcaster.broadcast(roomSessionId, env);
    }

    /**
     * emsSessionId → roomSessionId。
     *
     * <p>依赖 {@link RoomRegistry} 里维护的反向映射（由 SessionService.startSession 建立）。
     * 如果 RoomRegistry 的 API 名不同，只需改这一个方法体。</p>
     */
    private String resolveRoomSessionId(String emsSessionId) {
        if (emsSessionId == null) return null;
        return roomRegistry.getByEmsSessionId(emsSessionId)
            .map(SessionRoom::getSessionId)   // @Getter 生成，字段 sessionId
            .orElse(null);
    }
}

package com.ruoyi.ems.room.ws;


import com.fasterxml.jackson.databind.ObjectMapper;

import com.ruoyi.ems.room.dto.NpcActionResult;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.service.VariableWritebackService;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * NPC 动作完成处理器。
 * <p>
 * 收到 npc_action_completed 后：
 * <ol>
 *   <li>从 outputs 回写 CaseContext</li>
 *   <li>广播给前端（speaker / viewer / instructor）</li>
 * </ol>
 * 失败 / 取消仅广播，不回写变量。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NpcCompletionHandler {

    private static final Set<Role> FRONTEND_ROLES =
        Set.of(Role.SPEAKER, Role.VIEWER, Role.INSTRUCTOR);

    private final RoomRegistry roomRegistry;
    private final RoomBroadcaster broadcaster;
    private final VariableWritebackService writeback;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 处理完成回执。
     *
     * @param roomSessionId 房间 ID
     * @param emsSessionId  业务会话 ID
     * @param data          Unity 上报的 data 部分
     */
    public void onCompleted(String roomSessionId, String emsSessionId,
                            Map<String, Object> data) {
        if (data == null) return;

        NpcActionResult result;
        try {
            result = mapper.convertValue(data, NpcActionResult.class);
        } catch (Exception e) {
            log.error("解析 npc_action_completed 失败: room={}, data={}",
                roomSessionId, data, e);
            return;
        }
        if (result == null) return;

        // 1. 回写 outputs
        if (result.getOutputs() != null && !result.getOutputs().isEmpty()) {
            writeback.writeAll(emsSessionId, result.getOutputs(), "npc_action");
            log.info("NPC 动作 outputs 已回写: ems={}, npc={}, action={}, vars={}",
                emsSessionId, result.getNpcId(), result.getActionCode(),
                result.getOutputs().keySet());
        }

        // 2. 广播给前端
        Map<String, Object> payload = new HashMap<>();
        payload.put("npcId", result.getNpcId() != null ? result.getNpcId() : "");
        payload.put("actionCode", result.getActionCode() != null ? result.getActionCode() : "");
        payload.put("actualDuration", result.getActualDuration());
        payload.put("simulated", result.isSimulated());
        if (result.getQueryResult() != null) {
            payload.put("queryResult", result.getQueryResult());
        }

        broadcaster.sendToRoles(roomSessionId, FRONTEND_ROLES,
            RoomBroadcaster.envelope("team_status", Map.of(
                "statusType", "npc_action_completed",
                "data", payload
            )));
    }

    /**
     * 处理失败 / 取消。仅广播，不回写变量。
     *
     * @param type "npc_action_failed" 或 "npc_action_cancelled"
     */
    public void onFailedOrCancelled(String roomSessionId,
                                    Map<String, Object> data,
                                    String type) {
        if (data == null) return;

        NpcActionResult result;
        try {
            result = mapper.convertValue(data, NpcActionResult.class);
        } catch (Exception e) {
            log.error("解析 {} 失败: room={}, data={}", type, roomSessionId, data, e);
            return;
        }
        if (result == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("npcId", result.getNpcId() != null ? result.getNpcId() : "");
        payload.put("actionCode", result.getActionCode() != null ? result.getActionCode() : "");
        payload.put("reason", result.getReason() != null ? result.getReason() : "");

        broadcaster.sendToRoles(roomSessionId, FRONTEND_ROLES,
            RoomBroadcaster.envelope("team_status", Map.of(
                "statusType", type,
                "data", payload
            )));

        log.info("NPC 动作 {}: room={}, npc={}, action={}, reason={}",
            type, roomSessionId, result.getNpcId(),
            result.getActionCode(), result.getReason());
    }

    /**
     * 从房间反查 emsSessionId。当 Unity 上行消息未携带 emsSessionId 时使用。
     */
    public String resolveEmsSessionId(String roomSessionId, Map<String, Object> data) {
        if (data != null) {
            Object id = data.get("emsSessionId");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        }
        return roomRegistry.get(roomSessionId)
            .map(r -> r.getEmsSessionId())
            .orElse(null);
    }
}

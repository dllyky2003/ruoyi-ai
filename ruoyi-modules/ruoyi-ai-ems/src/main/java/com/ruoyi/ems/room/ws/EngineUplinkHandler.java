package com.ruoyi.ems.room.ws;


import com.fasterxml.jackson.databind.ObjectMapper;

import com.ruoyi.ems.room.PendingRequestManager;
import com.ruoyi.ems.room.dto.VitalValueDto;
import com.ruoyi.ems.room.model.PhysiologyVariableCache;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.ws.WsMessageContext;
import com.ruoyi.ems.room.ws.WsMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unity 引擎上行消息处理器。
 * <p>
 * 处理：
 * <ul>
 *   <li>vitals_response —— 完成 pending + 写缓存</li>
 *   <li>vitals_update   —— 写缓存</li>
 *   <li>physiology_event —— 广播生理事件</li>
 *   <li>intervention_ack —— 完成 pending</li>
 *   <li>npc_action_ack / started / progress —— 仅日志</li>
 *   <li>npc_action_completed / failed / cancelled —— 完成 pending + 业务处理</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineUplinkHandler implements WsMessageRouter.WsMessageHandler {

    private final PendingRequestManager pending;
    private final PhysiologyVariableCache physCache;
    private final NpcCompletionHandler npcCompletion;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Set<String> SUPPORTED = Set.of(
        "vitals_response",
        "vitals_update",
        "physiology_event",
        "intervention_ack",
        "npc_action_ack",
        "npc_action_started",
        "npc_action_progress",
        "npc_action_completed",
        "npc_action_failed",
        "npc_action_cancelled"
    );

    @Override
    public boolean supports(String type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public void handle(WsMessageContext ctx) {
        if (ctx.role() != Role.ENGINE) {
            log.warn("非引擎角色发送引擎消息: sessionId={}, role={}, type={}",
                ctx.sessionId(), ctx.role(), ctx.message().get("type"));
            return;
        }

        String type = (String) ctx.message().get("type");
        String requestId = ctx.requestId();
        String roomSessionId = ctx.sessionId();
        Map<String, Object> data = ctx.data();

        switch (type) {
            case "vitals_response"      -> handleVitalsResponse(roomSessionId, requestId, data);
            case "vitals_update"        -> handleVitalsUpdate(roomSessionId, data);
            case "physiology_event"     -> handlePhysiologyEvent(roomSessionId, data);
            case "intervention_ack"     -> handleInterventionAck(roomSessionId, requestId, data);
            case "npc_action_ack"       -> handleNpcAck(roomSessionId, requestId, data);
            case "npc_action_started"   -> handleNpcStarted(roomSessionId, requestId, data);
            case "npc_action_progress"  -> handleNpcProgress(roomSessionId, requestId, data);
            case "npc_action_completed" -> handleNpcCompleted(roomSessionId, requestId, data);
            case "npc_action_failed"    -> handleNpcFailed(roomSessionId, requestId, data);
            case "npc_action_cancelled" -> handleNpcCancelled(roomSessionId, requestId, data);
            default -> log.warn("未处理的引擎上行消息: type={}", type);
        }
    }

    // ==================== vitals ====================

    private void handleVitalsResponse(String roomSessionId, String requestId,
                                      Map<String, Object> data) {
        // 1. 写缓存
        writeToCache(roomSessionId, data);

        // 2. 完成 pending
        if (requestId == null) {
            log.warn("vitals_response 缺少 requestId: room={}", roomSessionId);
            return;
        }
        boolean matched = pending.complete(requestId, data);
        log.debug("体征查询响应: room={}, requestId={}, matched={}",
            roomSessionId, requestId, matched);
    }

    private void handleVitalsUpdate(String roomSessionId, Map<String, Object> data) {
        writeToCache(roomSessionId, data);
    }

    /**
     * 把 Unity 上报的 values 写入缓存。
     * <p>
     * 优先用 data.emsSessionId 作为 key；缺失时回退用 roomSessionId。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private void writeToCache(String roomSessionId, Map<String, Object> data) {
        if (data == null) return;

        Object emsId = data.get("emsSessionId");
        String emsSessionId = (emsId != null && !String.valueOf(emsId).isBlank())
            ? String.valueOf(emsId)
            : roomSessionId;

        Object values = data.get("values");
        if (!(values instanceof Map)) return;

        Map<String, Object> raw = (Map<String, Object>) values;
        Map<String, VitalValueDto> typed = new HashMap<>();
        raw.forEach((k, v) -> {
            if (k == null || v == null) return;
            if (v instanceof Map) {
                try {
                    typed.put(k, mapper.convertValue(v, VitalValueDto.class));
                } catch (Exception e) {
                    log.warn("体征值解析失败: key={}, value={}", k, v);
                }
            } else {
                // 简写形式：直接给值
                VitalValueDto dto = new VitalValueDto();
                dto.setValue(v);
                dto.setSource("physiology_engine");
                dto.setTimestamp(System.currentTimeMillis());
                typed.put(k, dto);
            }
        });

        if (!typed.isEmpty()) {
            physCache.updateAll(emsSessionId, typed);
            log.info("体征缓存更新: ems={}, count={}", emsSessionId, typed.size());
        }
    }

    private void handlePhysiologyEvent(String roomSessionId, Map<String, Object> data) {
        log.info("生理事件: room={}, event={}", roomSessionId, data.get("event"));
        // TODO 第七步：广播给前端
    }

    // ==================== intervention ====================

    private void handleInterventionAck(String roomSessionId, String requestId,
                                       Map<String, Object> data) {
        if (requestId == null) return;
        pending.complete(requestId, data);
    }

    // ==================== npc action ====================

    private void handleNpcAck(String roomSessionId, String requestId,
                              Map<String, Object> data) {
        log.debug("NPC 动作已入队: room={}, requestId={}, npcId={}",
            roomSessionId, requestId, data.get("npcId"));
        // ack 不完成 pending，等待 completed / failed / cancelled
    }

    private void handleNpcStarted(String roomSessionId, String requestId,
                                  Map<String, Object> data) {
        log.debug("NPC 动作开始: room={}, requestId={}, npcId={}, action={}",
            roomSessionId, requestId, data.get("npcId"), data.get("actionCode"));
        // TODO 第七步：更新 TeamMember 状态为 EXECUTING
    }

    private void handleNpcProgress(String roomSessionId, String requestId,
                                   Map<String, Object> data) {
        // TODO 第七步：更新 TeamMember.taskProgress
    }

    private void handleNpcCompleted(String roomSessionId, String requestId,
                                    Map<String, Object> data) {
        // 1. 解析 emsSessionId
        String emsSessionId = npcCompletion.resolveEmsSessionId(roomSessionId, data);

        // 2. 完成 pending
        if (requestId != null) {
            boolean matched = pending.complete(requestId, data);
            log.info("NPC 动作完成: room={}, ems={}, requestId={}, npcId={}, action={}, matched={}",
                roomSessionId, emsSessionId, requestId,
                data.get("npcId"), data.get("actionCode"), matched);
        } else {
            log.warn("npc_action_completed 缺少 requestId: room={}", roomSessionId);
        }

        // 3. 业务处理：回写 outputs + 广播
        if (emsSessionId != null) {
            npcCompletion.onCompleted(roomSessionId, emsSessionId, data);
        } else {
            log.warn("无法解析 emsSessionId，跳过 outputs 回写: room={}", roomSessionId);
        }
    }

    private void handleNpcFailed(String roomSessionId, String requestId,
                                 Map<String, Object> data) {
        if (requestId != null) {
            String reason = String.valueOf(data.get("reason"));
            pending.completeExceptionally(requestId,
                new RuntimeException("NPC 动作失败: " + reason));
        }
        npcCompletion.onFailedOrCancelled(roomSessionId, data, "npc_action_failed");
    }

    private void handleNpcCancelled(String roomSessionId, String requestId,
                                    Map<String, Object> data) {
        if (requestId != null) {
            String reason = String.valueOf(data.get("reason"));
            pending.completeExceptionally(requestId,
                new RuntimeException("NPC 动作被取消: " + reason));
        }
        npcCompletion.onFailedOrCancelled(roomSessionId, data, "npc_action_cancelled");
    }
}

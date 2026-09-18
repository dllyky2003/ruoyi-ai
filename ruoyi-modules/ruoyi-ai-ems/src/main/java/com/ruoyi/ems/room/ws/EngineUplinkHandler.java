package com.ruoyi.ems.room.ws;


import com.fasterxml.jackson.databind.ObjectMapper;

import com.ruoyi.ems.room.PendingRequestManager;
import com.ruoyi.ems.room.dto.VitalValueDto;
import com.ruoyi.ems.room.model.PhysiologyVariableCache;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.service.VariableWritebackService;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineUplinkHandler implements WsMessageRouter.WsMessageHandler {

    private final PendingRequestManager pending;
    private final PhysiologyVariableCache physCache;
    private final NpcCompletionHandler npcCompletion;
    private final VariableWritebackService writeback;
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

    private void handleVitalsUpdate(String roomSessionId, Map<String, Object> data) {
        // 1. 写缓存（保留原行为）
        writeToCache(roomSessionId, data);

        // 2. ★ 写回 EmsContextManager（新增）
        writebackToContext(roomSessionId, data);
    }

    private void handleVitalsResponse(String roomSessionId, String requestId,
                                      Map<String, Object> data) {
        // 1. 写缓存
        writeToCache(roomSessionId, data);

        // 2. ★ 写回 EmsContextManager（查询响应也是权威值）
        writebackToContext(roomSessionId, data);

        // 3. 完成 pending（原逻辑）
        if (requestId == null) {
            log.warn("vitals_response 缺少 requestId: room={}", roomSessionId);
            return;
        }
        boolean matched = pending.complete(requestId, data);
        log.debug("体征查询响应: room={}, requestId={}, matched={}",
            roomSessionId, requestId, matched);
    }

    /**
     * 把 Unity 上报的 values 写入缓存。
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

    /**
     * ★ 新增：把 Unity 体征写回 EmsContextManager。
     *
     * <p>只处理 PulseVariableMap 里的 6 个连续量。
     * 写入 caller = "UNITY_VITALS"，用于区分权威源。</p>
     */
    @SuppressWarnings("unchecked")
    private void writebackToContext(String roomSessionId, Map<String, Object> data) {
        if (data == null) return;

        Object emsId = data.get("emsSessionId");
        String emsSessionId = (emsId != null && !String.valueOf(emsId).isBlank())
            ? String.valueOf(emsId)
            : npcCompletion.resolveEmsSessionId(roomSessionId, Map.of());
        if (emsSessionId == null) {
            log.warn("vitals 写回跳过：无法解析 emsSessionId, room={}", roomSessionId);
            return;
        }

        Object values = data.get("values");
        if (!(values instanceof Map)) return;
        Map<String, Object> raw = (Map<String, Object>) values;

        // 过滤：只允许 PulseVariableMap 里的 6 个连续量
        Set<String> allowed = Set.of(
            "phys.heartRate", "phys.systolicBp", "phys.diastolicBp",
            "phys.spo2", "phys.etco2", "phys.respiratoryRate"
        );
        Map<String, Object> filtered = new HashMap<>();
        raw.forEach((k, v) -> {
            if (allowed.contains(k) && v != null) {
                filtered.put(k, v);
            }
        });
        if (filtered.isEmpty()) return;

        writeback.writeAll(emsSessionId, filtered, "UNITY_VITALS");
        log.info("Unity 体征已写回 EmsContextManager: ems={}, vars={}",
            emsSessionId, filtered.keySet());
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
    }

    private void handleNpcStarted(String roomSessionId, String requestId,
                                  Map<String, Object> data) {
        log.debug("NPC 动作开始: room={}, requestId={}, npcId={}, action={}",
            roomSessionId, requestId, data.get("npcId"), data.get("actionCode"));
    }

    private void handleNpcProgress(String roomSessionId, String requestId,
                                   Map<String, Object> data) {
        // TODO 第七步：更新 TeamMember.taskProgress
    }

    private void handleNpcCompleted(String roomSessionId, String requestId,
                                    Map<String, Object> data) {
        String emsSessionId = npcCompletion.resolveEmsSessionId(roomSessionId, data);

        if (requestId != null) {
            boolean matched = pending.complete(requestId, data);
            log.info("NPC 动作完成: room={}, ems={}, requestId={}, npcId={}, action={}, matched={}",
                roomSessionId, emsSessionId, requestId,
                data.get("npcId"), data.get("actionCode"), matched);
        } else {
            log.warn("npc_action_completed 缺少 requestId: room={}", roomSessionId);
        }

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

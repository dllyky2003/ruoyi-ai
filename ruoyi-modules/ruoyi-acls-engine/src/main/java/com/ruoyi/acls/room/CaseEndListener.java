package com.ruoyi.acls.room;

import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.ems.room.event.StateMachineEndEvent;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 案例结束监听器。
 * <p>
 * 状态机进入 endState 时发布 {@link StateMachineEndEvent}，本监听器负责：
 * <ol>
 *   <li>把房间状态置为 COMPLETED</li>
 *   <li>停止时钟</li>
 *   <li>广播 case_end 给房间内所有客户端</li>
 * </ol>
 * </p>
 * <p>
 * 幂等：房间已终态时忽略重复事件。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseEndListener {

    private final RoomRegistry roomRegistry;
    private final RoomBroadcaster broadcaster;
    private final ClockService clockService;

    @EventListener
    public void onStateMachineEnd(StateMachineEndEvent evt) {
        String emsSessionId = evt.emsSessionId();

        // ★ 用 emsSessionId 反查房间，而不是用房间 sessionId 查
        Optional<SessionRoom> roomOpt = roomRegistry.getByEmsSessionId(emsSessionId);
        if (roomOpt.isEmpty()) {
            log.warn("收到状态机结束事件，但未找到关联房间: ems={}", emsSessionId);
            return;
        }

        SessionRoom room = roomOpt.get();
        if (room.isTerminal()) {
            log.debug("房间已终态，忽略重复结束事件: room={}, state={}",
                room.getSessionId(), room.getState());
            return;
        }

        // 先取快照值，避免 stop 后 elapsedSeconds 读数受影响
        double elapsedSeconds = clockService.elapsedSeconds(emsSessionId);

        room.complete();
        clockService.stop(emsSessionId);

        broadcaster.broadcast(room.getSessionId(),
            RoomBroadcaster.envelope("case_end", Map.of(
                "emsSessionId", emsSessionId,
                "reason", evt.reason() != null ? evt.reason() : "completed",
                "finalState", evt.finalState() != null ? evt.finalState() : "",
                "endedAt", System.currentTimeMillis(),
                "elapsedSeconds", elapsedSeconds
            )));

        log.info("🏁 案例结束广播: room={}, ems={}, reason={}, finalState={}, elapsed={}s",
            room.getSessionId(), emsSessionId, evt.reason(), evt.finalState(), elapsedSeconds);
    }
}

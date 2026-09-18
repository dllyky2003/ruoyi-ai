package com.ruoyi.ems.websocket;

import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.event.TaskProgressUpdatedEvent;
import com.ruoyi.ems.event.TaskStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeamStatusWebSocketListener {

    // 仅作为日志记录器，不再推送 WebSocket
    // 所有推送由 ClockService 统一处理

    @EventListener
    public void onTaskStarted(TaskStartedEvent event) {
        log.debug("📨 [Event] TaskStarted: sessionId={}, member={}, task={}, duration={}",
            event.getSessionId(),
            event.getMember().getName(),
            event.getTask(),
            event.getDurationSeconds()
        );
        // 不再推送，因为 ClockService 会处理
    }

    @EventListener
    public void onTaskProgress(TaskProgressUpdatedEvent event) {
        log.debug("📨 [Event] TaskProgress: sessionId={}, member={}, progress={}",
            event.getSessionId(),
            event.getMember().getName(),
            event.getProgress()
        );
        // 不再推送
    }

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        log.debug("📨 [Event] TaskCompleted: sessionId={}, member={}, success={}, report={}",
            event.getSessionId(),
            event.getMember().getName(),
            event.isSuccess(),
            event.getReport()
        );
        // 不再推送
    }

    // 如果还有 VrActionCompletedEvent 监听，同样移除推送
    // @EventListener
    // public void handleVrActionCompleted(VrActionCompletedEvent event) {
    //     log.debug("📨 [Event] VrActionCompleted: sessionId={}, action={}, success={}",
    //         event.getSessionId(), event.getAction(), event.isSuccess());
    // }
}

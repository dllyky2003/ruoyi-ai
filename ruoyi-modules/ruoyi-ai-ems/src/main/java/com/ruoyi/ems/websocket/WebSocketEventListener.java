package com.ruoyi.ems.websocket;

import com.ruoyi.ems.domain.dto.TeamStatusEvent;
import com.ruoyi.ems.domain.dto.WebSocketPushEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {
    private final WebSocketMessageSender sender;

    @Async
    @EventListener
    public void handlePush(WebSocketPushEvent e) {
        Map<String, Object> payload = Map.of(
            "type", e.getType(),
            "data", e.getData(),
            "timestamp", System.currentTimeMillis()
        );
        sender.send(e.getSessionId(), payload);
    }

    @EventListener
    public void handleTeamStatus(TeamStatusEvent e) {
        Map<String, Object> payload = Map.of(
            "type", "team_status",
            "statusType", e.getStatusType(),
            "data", e.getData(),
            "timestamp", System.currentTimeMillis()
        );
        sender.send(e.getSessionId(), payload);
    }

}

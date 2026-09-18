package com.ruoyi.ems.domain.dto;

import lombok.Data;

@Data
public class WebSocketPushEvent {
    private final String sessionId;
    private final String type;
    private final Object data;
}


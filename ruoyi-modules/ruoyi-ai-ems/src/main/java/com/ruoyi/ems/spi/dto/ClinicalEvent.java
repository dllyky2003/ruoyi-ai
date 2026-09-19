package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态机发出的事件（供 tutor 接收）。
 */
@Data
public class ClinicalEvent {

    private String sessionId;
    private String eventId;
    private String type;
    private String source;
    private long occurredAt;
    private long stateRevision;
    private String idempotencyKey;
    private Map<String, Object> payload = new HashMap<>();
}

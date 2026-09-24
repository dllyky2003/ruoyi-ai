package com.ruoyi.ems.room.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SnapshotEvent {
    private String eventId;
    private long eventSequence;
    private String eventType;
    private long stateRevision;
    private boolean incrementsStateRevision;
    private String payload;   // 原始 JSON 字符串
}

package com.ruoyi.ems.room.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class RoomDetail {
    private String sessionId;
    private String caseId;
    private String templateId;
    private String state;
    private long createdAt;
    private long lastActivityAt;
    private long pausedDuration;
    private List<Map<String, Object>> clients;
}

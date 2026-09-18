package com.ruoyi.ems.room.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoomSummary {
    private String sessionId;
    private String caseId;
    private String templateId;
    private String state;
    private int clientCount;
    private List<String> roles;
    private long createdAt;
    private long lastActivityAt;
}

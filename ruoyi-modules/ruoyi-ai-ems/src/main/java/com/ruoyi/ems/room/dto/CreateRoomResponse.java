package com.ruoyi.ems.room.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateRoomResponse {
    private String sessionId;
    private String caseId;
    private String templateId;
    private String state;
    private String creatorRole;
    private String wsToken;
    private String wsUrl;
    private long expiresInSeconds;
}

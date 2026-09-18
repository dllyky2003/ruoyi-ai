package com.ruoyi.ems.room.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JoinResponse {
    private String sessionId;
    private String role;
    private String wsToken;
    private String wsUrl;
    private long expiresInSeconds;
    private String state;
}

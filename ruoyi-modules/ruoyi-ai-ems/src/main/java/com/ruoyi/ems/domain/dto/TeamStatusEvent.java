package com.ruoyi.ems.domain.dto;

import lombok.Data;

import java.util.Map;

@Data
public class TeamStatusEvent {
    private final String sessionId;
    private final String statusType;
    private final Map<String, Object> data;
}

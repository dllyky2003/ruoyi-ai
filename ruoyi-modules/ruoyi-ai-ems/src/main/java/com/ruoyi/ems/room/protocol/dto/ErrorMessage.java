package com.ruoyi.ems.room.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ErrorMessage {
    @Builder.Default private String type = "ERROR";
    private String attemptId;
    private String code;                     // WsErrorCode 枚举 name
    private String message;
    private String requestId;
    private long stateRevision;
    private long serverSequence;
    @Builder.Default private List<KV> details = new ArrayList<>();
}


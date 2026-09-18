package com.ruoyi.ems.domain.dto;

import jakarta.websocket.Session;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommandTask {
    private String sessionId;
    private String transcript;      // 转写文本
    private Session webSocketSession; // 用于回传响应
    private long timestamp;          // 入队时间戳（毫秒）
    private int retryCount;          // 预留重试字段
}

package com.ruoyi.ems.room.event;

import java.time.Instant;

/**
 * Unity 引擎断线事件。
 * <p>
 * 由 {@code SessionWebSocketHandler.onClose} 在检测到 ENGINE 角色断开时发布。
 * 订阅者：
 * <ul>
 *   <li>{@code PendingRequestManager} —— 该会话所有 pending 请求立即失败</li>
 *   <li>前端广播器 —— 通知 speaker / viewer / instructor "引擎已离线"</li>
 * </ul>
 * </p>
 * <p>
 * 使用 record 保证不可变，字段精简，便于序列化和跨实例广播。
 * </p>
 */
public record EngineOfflineEvent(
    /** 房间 ID */
    String sessionId,

    /** 断开的 clientId，可空 */
    String clientId,

    /** 断开原因（CloseReason.getReasonPhrase），可空 */
    String reason,

    /** 事件时间 */
    Instant occurredAt
) {

    /**
     * 最小构造：只需 sessionId。
     */
    public EngineOfflineEvent(String sessionId) {
        this(sessionId, null, null, Instant.now());
    }

    /**
     * 带原因的构造。
     */
    public EngineOfflineEvent(String sessionId, String clientId, String reason) {
        this(sessionId, clientId, reason, Instant.now());
    }

    /**
     * 紧凑构造器：校验 sessionId 非空。
     */
    public EngineOfflineEvent {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("EngineOfflineEvent.sessionId 不能为空");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}

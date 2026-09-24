package com.ruoyi.ems.room.protocol.dto;

import lombok.Data;

/**
 * 契约 V1.0.0 §7 导师覆盖响应结构。
 *
 * <p>服务端处理 {@link OverrideMessage} 后回推的结果，通过
 * {@code RoomBroadcaster.broadcast(roomSessionId, envelope("OVR_RESULT", msg))} 下发。</p>
 */
@Data
public class OverrideResultMessage {

    /** 固定 "OVR_RESULT" */
    private String type = "OVR_RESULT";

    /** 会话隔离键 */
    private String attemptId;

    /** 覆盖 ID（IO_PAUSE_CLOCK / IO_FORCE_SYNC_MODE） */
    private String overrideId;

    /** 是否已被接受 */
    private Boolean accepted;

    /** 人类可读的结果说明 */
    private String message;

    /** 契约 §6 错误码（拒绝时非空） */
    private String errorCode;

    /** 错误详情 */
    private String errorMessage;

    /** 覆盖生效后的状态修订 */
    private Long stateRevision;

    /** 服务端消息序号（OVR-2b 后由 AttemptSequencer 填充） */
    private Long serverSequence;
}

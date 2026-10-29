package com.ruoyi.ems.room.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 契约 V1.0.0 §5.2 命令响应结构。
 *
 * <p>★ 批 2（螺旋 2 / 阶段 2.1）：
 * <ul>
 *   <li>与旧 {@code CmdResultMessage} 合并为单一 DTO；</li>
 *   <li>补齐 errorCode / errorMessage / replayedIdempotencyKey；</li>
 *   <li>stateRevision / eventSequence / serverSequence 统一为 Long。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResultMessage {

    @Builder.Default
    private String type = "CMD_RESULT";

    private String attemptId;

    /** ALLOW / ALLOW_WITH_WARNING / BLOCK / EXECUTE_HARMFUL_ACTION */
    private String decision;

    private String primaryRuleId;

    @Builder.Default
    private List<String> allMatchedRuleIds = new ArrayList<>();

    /** none / minor / moderate / major / critical（由 ValidationResult 提供） */
    private String severity;

    @Builder.Default
    private List<String> feedbackKeys = new ArrayList<>();

    @Builder.Default
    private List<String> assessmentTags = new ArrayList<>();

    /** 指令生效后的状态修订 */
    private Long stateRevision;

    /** 指令产生的事件序号 */
    private Long eventSequence;

    /** 服务端消息序号（由 ProtocolBridgeService + AttemptSequencer 填充） */
    private Long serverSequence;

    /** 结果快照（可选；STALE_REVISION / SEQUENCE_GAP 时附全量快照） */
    private SnapshotMessage snapshot;

    // ========== ★ 批 2 新增（原 CmdResultMessage 独有） ==========

    /** 契约 §6 错误码，非空时 decision 无意义 */
    private String errorCode;

    /** 错误详情（人类可读，仅用于日志） */
    private String errorMessage;

    /** 幂等命中时回填原请求的 idempotencyKey */
    private String replayedIdempotencyKey;
}

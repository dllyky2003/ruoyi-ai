package com.ruoyi.ems.room.protocol.dto;

import lombok.Data;
import java.util.List;

/**
 * 契约 V1.0.0 §5.2 命令响应结构。
 */
@Data
public class CmdResultMessage {

    /** 固定 "CMD_RESULT" */
    private String type = "CMD_RESULT";

    private String attemptId;

    /** ALLOW / ALLOW_WITH_WARNING / BLOCK / EXECUTE_HARMFUL_ACTION */
    private String decision;

    /** 主规则 ID */
    private String primaryRuleId;

    /** 全部命中规则 ID */
    private List<String> allMatchedRuleIds;

    /** none / minor / moderate / major / critical */
    private String severity;

    /** 反馈 key 列表 */
    private List<String> feedbackKeys;

    /** 评估标签 */
    private List<String> assessmentTags;

    /** 指令生效后的状态修订 */
    private Long stateRevision;

    /** 指令产生的事件序号 */
    private Long eventSequence;

    /** 服务端消息序号 */
    private Long serverSequence;

    /** 结果快照（可选，增量或全量） */
    private SnapshotMessage snapshot;

    /** 契约 §6 错误码，非空时 decision 无意义 */
    private String errorCode;

    /** 错误详情（人类可读，仅用于日志） */
    private String errorMessage;

    /** 幂等命中时回填原请求的 idempotencyKey */
    private String replayedIdempotencyKey;
}

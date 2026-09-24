package com.ruoyi.ems.room.protocol.dto;

import lombok.Data;
import java.util.Map;

/**
 * 契约 V1.0.0 §5.1 入站命令结构。
 *
 * <p>所有状态改变指令必须走这个结构；旧 asr_transcript 由
 * {@code CmdMessageAdapter} 转换后进入。</p>
 */
@Data
public class CmdMessage {

    /** 固定 "CMD" */
    private String type = "CMD";

    /** 会话隔离键 */
    private String attemptId;

    /** 客户端自增序号，用于断档检测 */
    private long clientSequence;

    /** 幂等键，全局唯一 */
    private String idempotencyKey;

    /** 客户端期望的状态修订 */
    private Long expectedStateRevision;

    /** 干预 ID（对应 intent / interventionId） */
    private String interventionId;

    /** 结构化参数 */
    private Map<String, Object> parameters;

    /** 原始输入（语音文本 / 操作文本） */
    private String rawInput;

    /** 必填：simulated 或 real */
    private String timeBasis;

    /** timeBasis=real 时必填 */
    private String realTimeRationale;

    /** timeBasis=real 时必填 */
    private Boolean pauseSensitive;
}

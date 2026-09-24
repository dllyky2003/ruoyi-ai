package com.ruoyi.ems.room.protocol.effect;

import com.ruoyi.ems.room.protocol.dto.ProposedEffect;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 契约 V1.0.0 §7 —— 受控效果执行上下文。
 */
@Data
@Builder
public class EffectContext {

    /** 房间会话 ID */
    private String roomSessionId;

    /** EMS 会话 ID */
    private String emsSessionId;

    /** attemptId */
    private String attemptId;

    /** 覆盖 ID（IO_PAUSE_CLOCK / IO_FORCE_SYNC_MODE） */
    private String overrideId;

    /** 覆盖原因（必填，已由 OverrideHandler 校验） */
    private String reason;

    /** 客户端提议的受控效果原文 */
    private ProposedEffect proposedEffect;

    /** 是否触发评分中断（§8） */
    private Boolean triggersAssessmentInterruption;
}

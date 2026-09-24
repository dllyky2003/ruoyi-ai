package com.ruoyi.ems.room.protocol.effect;

import lombok.Builder;
import lombok.Data;

/**
 * 契约 V1.0.0 §7 —— 受控效果执行结果。
 */
@Data
@Builder
public class EffectResult {

    /** 是否已接受 */
    private boolean accepted;

    /** 人类可读的结果说明 */
    private String message;

    /** 效果生效后的 stateRevision（OVR-2b 填实） */
    private Long stateRevision;
}

package com.ruoyi.ems.engine.guidance;

import com.ruoyi.ems.engine.fsm.ACLSStates;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教学引导提示信息
 *
 * 封装了一次提示的内容、级别、关联状态等元数据
 */
@Data
@Builder
public class GuidanceHint {
    /** 会话ID */
    private String sessionId;

    /** 触发提示时的状态机状态 */
    private ACLSStates state;

    /** 提示级别（1~3级，逐步增强） */
    private GuidanceService.GuidanceLevel level;

    /** 提示文本内容 */
    private String text;

    /** 空闲等待秒数（从上次操作至今） */
    private long idleSeconds;

    /** 提示生成时间戳 */
    private LocalDateTime timestamp;
}

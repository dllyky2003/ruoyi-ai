package com.ruoyi.ems.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 契约 §12 —— 审计日志。
 *
 * <p>当前只覆盖 OVR-3 导师覆盖事件，后续 §12 审计链会补 prevHash / currHash。</p>
 */
@Data
@TableName("attempt_log")
public class AttemptLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 契约 §4 attemptId */
    private String attemptId;

    /** Room 会话 ID */
    private String roomSessionId;

    /** EMS 会话 ID */
    private String emsSessionId;

    /** 事件类型：TUTOR_OVERRIDE / ASSESSMENT_INTERRUPTION / ... */
    private String eventType;

    /** 事件动作：IO_PAUSE_CLOCK / IO_FORCE_SYNC_MODE / ... */
    private String action;

    /** 事件细节 JSON */
    private String detail;

    /** 事件发生时的 stateRevision */
    private Long stateRevision;

    /** 操作者：TUTOR / SYSTEM / ... */
    private String createdBy;

    private LocalDateTime createdAt;
}

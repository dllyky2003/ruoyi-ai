package org.ruoyi.system.domain;

import org.ruoyi.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;

/**
 * 会话事件日志（追加式/可审计）对象 emergency_session_event_log
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_session_event_log")
public class EmergencySessionEventLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 部门id
     */
    private Long deptId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 排序号
     */
    private Long orderNum;

    /**
     * 删除标志
     */
    @TableLogic
    private Long delFlag;

    /**
     * 
     */
    private Long sessionId;

    /**
     * 
     */
    private Long sequenceNo;

    /**
     * 
     */
    private Date occurredAt;

    /**
     * 
     */
    private String eventType;

    /**
     * device/user/asr/instructor/rule_engine/system
     */
    private String sourceType;

    /**
     * 
     */
    private String sourceId;

    /**
     * 
     */
    private String payloadJson;

    /**
     * 
     */
    private String decisionJson;

    /**
     * 
     */
    private Date createdAt;


}

package com.ruoyi.ems.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作轨迹表（状态机转换记录）
 */
@Data
@TableName("ems_action_trace")
public class EmsActionTrace {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String sessionId;
    private String fromState;
    private String toState;
    private String event;
    private String triggerAction;
    private String triggerText;
    private LocalDateTime timestamp;
}

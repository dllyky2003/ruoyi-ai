package com.ruoyi.ems.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 操作事件表（LLM抽取结果）
 */
@Data
@TableName("ems_action_event")
public class EmsActionEvent {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String sessionId;
    private Integer seq;
    private Integer timestamp;
    private String actor;
    private String actionType;
    private String detail;
    private String value;
    private String result;
    private String sourceText;
}

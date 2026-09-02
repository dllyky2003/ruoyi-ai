package com.ruoyi.ems.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体评估记录实体
 *
 * 对应表：ems_agent_evaluation
 * 记录每个专业智能体对一次考核会话的独立评估结果
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ems_agent_evaluation")
public class EmsAgentEvaluation {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 会话ID（关联 ems_session.id） */
    private String sessionId;

    /** 智能体名称：AssessmentAgent, DecisionAgent, SafetyAgent, FeedbackAgent */
    private String agentName;

    /** 智能体评分（0-100） */
    private Integer agentScore;

    /** 智能体原始输出（JSON格式，含推理过程） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object rawOutput;

    /** 智能体分析轨迹（文本） */
    private String traceLog;

    /** 是否有关键违规 */
    private Boolean hasCriticalViolation;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标识 */
    @TableLogic
    private Integer deleted;
}

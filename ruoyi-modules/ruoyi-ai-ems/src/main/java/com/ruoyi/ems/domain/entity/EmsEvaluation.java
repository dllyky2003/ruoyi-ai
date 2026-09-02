package com.ruoyi.ems.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ems_evaluation", autoResultMap = true)
public class EmsEvaluation {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String sessionId;

    private Integer totalScore;               // 对应 setTotalScore
    private Boolean isPass;                   // 对应 setIsPass

    // JSON 字段
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object dimensionScores;           // 各维度得分

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object criticalErrors;            // 致命错误

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object warnings;                  // 警告

    private String reportText;                // 报告文本

    // 智能体相关字段
    private Integer agentFusionScore;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object agentDetails;              // 各智能体详情

    // 时间字段
    private LocalDateTime createTime;
    private LocalDateTime createdAt;          // 对应 setCreatedAt
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

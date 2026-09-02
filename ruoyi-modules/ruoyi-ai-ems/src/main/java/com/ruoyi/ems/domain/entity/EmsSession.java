package com.ruoyi.ems.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 演练会话表
 */
@Data
@TableName("ems_session")
public class EmsSession {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String scenarioId;
    private String traineeName;
    private String mode;  // TEACH / EXAM
    private String status; // INIT / RUNNING / PAUSED / COMPLETED
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String audioFilePath;  // MinIO路径
    private String transcript;     // 完整ASR转写

    @TableField("create_time")
    private LocalDateTime createdTime;

    @TableField("update_time")
    private LocalDateTime updatedTime;

}

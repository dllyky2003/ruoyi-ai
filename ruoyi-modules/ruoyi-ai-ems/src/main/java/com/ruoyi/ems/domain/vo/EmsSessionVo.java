package com.ruoyi.ems.domain.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * EMS会话视图对象
 *
 * @author yourname
 * @date 2026-08-23
 */
@Data
public class EmsSessionVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String scenarioId;
    private String traineeName;
    private String mode;          // TEACH / EXAM
    private String status;        // INIT / RUNNING / PAUSED / COMPLETED
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String audioFilePath;
    private String transcript;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

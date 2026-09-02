package com.ruoyi.ems.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 考核场景表
 */
@Data
@TableName("ems_scenario")
public class EmsScenario {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String difficulty;
    private String guideVersion;
    private String configJson;  // YAML转JSON
    private String description;
    @TableField("create_time")
    private Date createdTime;
    @TableField("update_time")
    private Date updatedTime;
}

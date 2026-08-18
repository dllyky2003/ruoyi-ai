package org.ruoyi.system.domain;

import org.ruoyi.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;

/**
 * 会话评分记录（追加式）对象 emergency_session_score_record
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_session_score_record")
public class EmergencySessionScoreRecord extends TenantEntity {

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
    private String metricCode;

    /**
     * 
     */
    private Long score;

    /**
     * 
     */
    private Long maxScore;

    /**
     * 
     */
    private String result;

    /**
     * 
     */
    private String evidenceJson;

    /**
     * 
     */
    private String explanation;

    /**
     * 
     */
    private Date determinedAt;


}

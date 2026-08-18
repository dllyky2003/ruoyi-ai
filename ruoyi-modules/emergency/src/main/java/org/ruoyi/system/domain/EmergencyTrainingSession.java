package org.ruoyi.system.domain;

import org.ruoyi.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;

/**
 * 训练会话对象 emergency_training_session
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_training_session")
public class EmergencyTrainingSession extends TenantEntity {

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
    private String sessionCode;

    /**
     * 机构/单位隔离维度
     */
    private Long organizationId;

    /**
     * 
     */
    private Long caseVersionId;

    /**
     * 
     */
    private String caseDefinitionHash;

    /**
     * created/running/paused/completed/aborted
     */
    private String status;

    /**
     * 
     */
    private Date startedAt;

    /**
     * 
     */
    private Date endedAt;


}

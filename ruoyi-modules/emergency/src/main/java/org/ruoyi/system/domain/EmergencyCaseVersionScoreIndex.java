package org.ruoyi.system.domain;

import org.ruoyi.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 病例版本评分项索引对象 emergency_case_version_score_index
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_case_version_score_index")
public class EmergencyCaseVersionScoreIndex extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
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

    private Long caseVersionId;

    /**
     *
     */

    private String metricConfigId;

    /**
     *
     */
    private Long libraryItemId;

    /**
     *
     */
    private Long libraryVersionId;

    /**
     *
     */
    private Long weight;

    /**
     *
     */
    private Long enabled;


}

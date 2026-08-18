package org.ruoyi.system.domain;

import org.ruoyi.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 病例版本引用的资源配置与公共库版本映射对象 emergency_case_version_resource_ref
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_case_version_resource_ref")
public class EmergencyCaseVersionResourceRef extends TenantEntity {

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

    private String resourceConfigId;

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
    private String resourceType;

    /**
     *
     */
    private Long enabled;


}

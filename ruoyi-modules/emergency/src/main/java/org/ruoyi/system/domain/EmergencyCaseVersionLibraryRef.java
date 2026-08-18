package org.ruoyi.system.domain;

import org.ruoyi.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 病例引用了哪些公共库版本；用于影响分析与发布校验对象 emergency_case_version_library_ref
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_case_version_library_ref")
public class EmergencyCaseVersionLibraryRef extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 以下为业务字段，基类中不存在的才保留
    private Long caseVersionId;
    private Long libraryItemId;
    private Long libraryVersionId;
    private String refType;
    private String jsonPath;
}

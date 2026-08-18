package org.ruoyi.system.domain;

import org.ruoyi.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 公共库对象身份对象 emergency_library_item
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_library_item")
public class EmergencyLibraryItem extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 0=平台标准库(所有用户可见)，非0=机构级公共库
     */
    private Long organizationId;

    /**
     * 库类型
     */
    private String libraryType;

    /**
     * 项编码（唯一）
     */
    private String itemCode;

    /**
     * 名称
     */
    private String name;

    /**
     * 状态：draft / published 等
     */
    private String status;

    /**
     * 当前已发布的版本ID（外键引用 emergency_library_item_version.id）
     */
    private Long currentPublishedVersionId;
}

package org.ruoyi.system.domain.bo;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.system.domain.EmergencyLibraryItemVersion;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;

/**
 * 公共库冻结定义；published 后禁止 UPDATE业务对象 emergency_library_item_version
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EmergencyLibraryItemVersion.class, reverseConvertGenerate = false)
public class EmergencyLibraryItemVersionBo extends BaseEntity {

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { EditGroup.class })
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
     *
     */
    @NotNull(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long libraryItemId;

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long versionNo;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private String schemaVersion;

    /**
     * draft/reviewing/published/archived
     */
    @NotBlank(message = "draft/reviewing/published/archived不能为空", groups = { AddGroup.class, EditGroup.class })
    private String status;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private EmergencyLibraryItemDefinition definitionJson;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private String definitionHash;

    /**
     *
     */
    private String sourceGuideline;

    /**
     *
     */
    private String sourceVersion;

    /**
     *
     */
    private Date effectiveFrom;

    /**
     *
     */
    private Date effectiveTo;

    /**
     *
     */
    private String changeReason;

    /**
     *
     */
    private Long reviewedBy;

    /**
     *
     */
    private Date reviewedAt;


}

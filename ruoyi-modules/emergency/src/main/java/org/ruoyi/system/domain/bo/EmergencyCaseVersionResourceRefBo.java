package org.ruoyi.system.domain.bo;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.system.domain.EmergencyCaseVersionResourceRef;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 病例版本引用的资源配置与公共库版本映射业务对象 emergency_case_version_resource_ref
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EmergencyCaseVersionResourceRef.class, reverseConvertGenerate = false)
public class EmergencyCaseVersionResourceRefBo extends BaseEntity {

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
    @NotNull(message = "不能为空", groups = { EditGroup.class })
    private Long caseVersionId;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { EditGroup.class })
    private String resourceConfigId;

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long libraryItemId;

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long libraryVersionId;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private String resourceType;

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long enabled;


}

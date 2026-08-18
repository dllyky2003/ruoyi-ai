package org.ruoyi.system.domain.bo;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.system.domain.EmergencyTeachingCaseVersion;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.ruoyi.system.domain.definition.EmergencyTeachingCaseDefinition;

/**
 * 病例定义版本；published 后禁止 UPDATE业务对象 emergency_teaching_case_version
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EmergencyTeachingCaseVersion.class, reverseConvertGenerate = false)
public class EmergencyTeachingCaseVersionBo extends BaseEntity {

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
    private Long caseId;

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
     * 病例方案唯一事实源
     */
    @NotBlank(message = "病例方案唯一事实源不能为空", groups = { AddGroup.class, EditGroup.class })
    private EmergencyTeachingCaseDefinition definitionJson;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private String definitionHash;

    /**
     *
     */
    private Long basedOnVersionId;

    /**
     *
     */
    private String changeSummary;

    /**
     *
     */
    private Long reviewedBy;

    /**
     *
     */
    private Date reviewedAt;

    /**
     *
     */
    private Long publishedBy;

    /**
     *
     */
    private Date publishedAt;


}

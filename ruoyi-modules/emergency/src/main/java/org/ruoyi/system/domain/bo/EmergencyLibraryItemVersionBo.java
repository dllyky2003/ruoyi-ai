package org.ruoyi.system.domain.bo;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.system.domain.EmergencyLibraryItemVersion;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;

import java.util.Date;

/**
 * 公共库冻结定义；published 后禁止 UPDATE 业务对象 emergency_library_item_version
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EmergencyLibraryItemVersion.class, reverseConvertGenerate = false)
public class EmergencyLibraryItemVersionBo extends BaseEntity {

    // ==================== 主键 ====================
    @NotNull(message = "ID不能为空", groups = {EditGroup.class})
    private Long id;

    // ==================== 租户/部门/用户 ====================
    private String tenantId;           // 租户编号
    private Long deptId;               // 部门id
    private Long userId;               // 用户id
    private Integer orderNum;          // 排序号
    private Long createDept;           // 创建部门

    // ==================== 业务字段 ====================
    private String name;               // 名称
    private String workflowType;       // 工作流类型
    private String mode;               // 模式
    private String tags;               // 标签
    private String sex;
    private String difficultyLevel;
    private int age;
    private float estimatedDurationMinutes;
    private String  learningObjectives;

    @NotNull(message = "libraryItemId不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long libraryItemId;

    @NotNull(message = "versionNo不能为空", groups = {AddGroup.class, EditGroup.class})
    private Integer versionNo;

    @NotBlank(message = "schemaVersion不能为空", groups = {AddGroup.class, EditGroup.class})
    private String schemaVersion;

    @NotBlank(message = "status不能为空", groups = {AddGroup.class, EditGroup.class})
    private String status;            // draft/reviewing/published/archived

    @NotNull(message = "definitionJson不能为空", groups = {AddGroup.class, EditGroup.class})
    private EmergencyLibraryItemDefinition definitionJson;

    @NotBlank(message = "definitionHash不能为空", groups = {AddGroup.class, EditGroup.class})
    private String definitionHash;

    // 可选字段
    private String sourceGuideline;
    private String sourceVersion;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String changeReason;
    private Long reviewedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewedAt;

    // 注意：createTime、createBy、updateTime、updateBy、delFlag 由父类 BaseEntity 提供，无需重复声明
}

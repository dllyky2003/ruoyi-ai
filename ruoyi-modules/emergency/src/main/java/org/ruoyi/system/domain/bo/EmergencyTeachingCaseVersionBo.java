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
 * 病例定义版本；published 后禁止 UPDATE 业务对象 emergency_teaching_case_version
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EmergencyTeachingCaseVersion.class, reverseConvertGenerate = false)
public class EmergencyTeachingCaseVersionBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "id不能为空", groups = { EditGroup.class })
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
     * 关联病例ID
     */
    @NotNull(message = "caseId不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long caseId;

    /**
     * 版本号
     */
    @NotNull(message = "versionNo不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long versionNo;

    /**
     * Schema版本
     */
    @NotBlank(message = "schemaVersion不能为空", groups = { AddGroup.class, EditGroup.class })
    private String schemaVersion;

    /**
     * draft/reviewing/published/archived
     */
    @NotBlank(message = "status不能为空", groups = { AddGroup.class, EditGroup.class })
    private String status;

    /**
     * 病例方案唯一事实源（超大JSON）
     * 注意：@NotBlank 不能用于对象类型，已修正为 @NotNull
     */
    @NotNull(message = "definitionJson不能为空", groups = { AddGroup.class, EditGroup.class })
    private EmergencyTeachingCaseDefinition definitionJson;

    /**
     * 定义哈希值
     */
    @NotBlank(message = "definitionHash不能为空", groups = { AddGroup.class, EditGroup.class })
    private String definitionHash;

    // ========== 以下为从 definition_json 提取的概览字段 ==========
    // 注意：这些字段由后端自动填充，前端提交时会被忽略（以 definitionJson 为准）
    // 保留在 BO 中是为了便于 MapStruct 自动映射到实体类

    /**
     * 病例标题（对应 caseMeta.title）
     */
    private String title;

    /**
     * 患者年龄（岁）
     */
    private Integer age;

    /**
     * 患者性别（male/female/other）
     */
    private String sex;

    /**
     * 难度等级（初级/中级/高级）
     */
    private String difficulty;

    /**
     * 阶段总数
     */
    private Integer phasesCount;

    /**
     * 预计时长（分钟）
     */
    private Integer duration;

    /**
     * 标签（逗号分隔）
     */
    private String tags;

    /**
     * 简短描述
     */
    private String description;

    /**
     * 引用指南版本
     */
    private String guidelineProfile;

    // ========== 原有业务字段 ==========

    /**
     * 基于的版本ID
     */
    private Long basedOnVersionId;

    /**
     * 变更摘要
     */
    private String changeSummary;

    /**
     * 审核人
     */
    private Long reviewedBy;

    /**
     * 审核时间
     */
    private Date reviewedAt;

    /**
     * 发布人
     */
    private Long publishedBy;

    /**
     * 发布时间
     */
    private Date publishedAt;
}

package org.ruoyi.system.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.system.domain.definition.EmergencyTeachingCaseDefinition;

import java.util.Date;

/**
 * 病例定义版本；published 后禁止 UPDATE
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_teaching_case_version")
public class EmergencyTeachingCaseVersion extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private Long deptId;
    private Long userId;
    private Integer orderNum;
    private Long createDept;

    /**
     * 关联病例ID
     */
    private Long caseId;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * Schema版本
     */
    private String schemaVersion;

    /**
     * 状态：draft/reviewing/published/archived
     */
    private String status;

    /**
     * 病例方案唯一事实源（超大JSON）
     * 使用 JacksonTypeHandler 自动序列化/反序列化
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private EmergencyTeachingCaseDefinition definitionJson;

    /**
     * 定义哈希值
     */
    private String definitionHash;

    // ========== 以下为新增概览字段（从 definition_json 提取，提升列表性能） ==========

    /**
     * 病例标题（对应 caseMeta.title）
     */
    private String title;

    /**
     * 患者年龄（岁），对应 clinicalContext.patient.demographics.age
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
     * 阶段总数（phases 数组长度）
     */
    private Integer phasesCount;

    /**
     * 预计时长（分钟），对应 caseMeta.estimatedDurationMin
     */
    private Integer duration;

    /**
     * 标签（逗号分隔字符串，如 "ACLS学员,院前急救"）
     */
    private String tags;

    /**
     * 简短描述（caseMeta.description）
     */
    private String description;

    /**
     * 引用指南版本（如 "2025"），对应 governance.guidelineProfileRef.version
     */
    private String guidelineProfile;

    // ========== 原有业务字段 ==========

    private Long basedOnVersionId;
    private String changeSummary;
    private Long reviewedBy;
    private Date reviewedAt;
    private Long publishedBy;
    private Date publishedAt;

    /**
     * 删除标志（逻辑删除）
     */
    @TableLogic
    private Integer delFlag;
}

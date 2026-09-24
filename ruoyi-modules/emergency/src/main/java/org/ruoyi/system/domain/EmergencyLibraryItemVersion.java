package org.ruoyi.system.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;

import java.util.Date;

/**
 * 可版本化的教学方案实例定义
 * 对应表 emergency_library_item_version
 *
 * @author ageerle
 * @date 2026-08-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "emergency_library_item_version", autoResultMap = true)
public class EmergencyLibraryItemVersion extends BaseEntity {

    // ==================== 主键 ====================
    @TableId(type = IdType.AUTO)
    private Long id;

    // ==================== 租户/部门/用户 ====================
    @TableField("tenant_id")
    private String tenantId;

    @TableField("dept_id")
    private Long deptId;

    @TableField("user_id")
    private Long userId;

    @TableField("order_num")
    private Integer orderNum;

    @TableField("create_dept")
    private Long createDept;

    // ==================== 业务字段 ====================
    @TableField("library_item_id")
    private Long libraryItemId;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("schema_version")
    private String schemaVersion;

    @TableField("status")
    private String status;          // draft/reviewing/published/archived

    // JSON 字段，使用 JacksonTypeHandler 处理
    @TableField(value = "definition_json", typeHandler = JacksonTypeHandler.class)
    private EmergencyLibraryItemDefinition definitionJson;

    @TableField("definition_hash")
    private String definitionHash;

    @TableField("source_guideline")
    private String sourceGuideline;

    @TableField("source_version")
    private String sourceVersion;

    @TableField("effective_from")
    private Date effectiveFrom;

    @TableField("effective_to")
    private Date effectiveTo;

    @TableField("change_reason")
    private String changeReason;

    @TableField("reviewed_by")
    private Long reviewedBy;

    @TableField("reviewed_at")
    private Date reviewedAt;

    @TableField("name")
    private String name;

    @TableField("sex")
    private String sex;

    @TableField("workflow_type")
    private String workflowType;

    @TableField("difficulty")
    private String difficultyLevel;

    @TableField("age")
    private int age;
    @TableField("estimatedDurationMin")
    private float estimatedDurationMinutes;

    @TableField("learningObjectives")
    private String  learningObjectives;

    @TableField("mode")
    private String mode;

    @TableField("tags")
    private String tags;

    @TableField("case_type")
    private String caseType;
}

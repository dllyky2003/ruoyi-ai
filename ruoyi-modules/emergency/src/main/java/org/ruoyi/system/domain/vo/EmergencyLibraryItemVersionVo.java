package org.ruoyi.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.system.domain.EmergencyLibraryItemVersion;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 公共库冻结定义；published 后禁止 UPDATE 视图对象 emergency_library_item_version
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyLibraryItemVersion.class)
public class EmergencyLibraryItemVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 主键 ====================
    @ExcelProperty(value = "版本ID")
    private Long id;

    // ==================== 租户/部门/用户 ====================
    @ExcelProperty(value = "租户编号")
    private String tenantId;

    @ExcelProperty(value = "部门ID")
    private Long deptId;

    @ExcelProperty(value = "用户ID")
    private Long userId;

    @ExcelProperty(value = "排序号")
    private Integer orderNum;

    @ExcelProperty(value = "创建部门")
    private Long createDept;

    // ==================== 审计字段 ====================
    @ExcelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @ExcelProperty(value = "创建人")
    private Long createBy;

    @ExcelProperty(value = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @ExcelProperty(value = "更新人")
    private Long updateBy;

    @ExcelProperty(value = "删除标志")
    private Integer delFlag;

    // ==================== 业务字段 ====================
    @ExcelProperty(value = "姓名")
    private String name;

    @ExcelProperty(value = "性别")
    private String sex;

    @ExcelProperty(value = "工作流类型")
    private String workflowType;
    @ExcelProperty(value = "难度等级")
    private String difficultyLevel;
    @ExcelProperty(value = "年龄")
    private int age;
    @ExcelProperty(value = "预计时长（分钟）")
    private float estimatedDurationMinutes;
    @ExcelProperty(value = "学习目标")
    private String  learningObjectives;



    @ExcelProperty(value = "模式")
    private String mode;

    @ExcelProperty(value = "标签")
    private String tags;

    @ExcelProperty(value = "库条目ID")
    private Long libraryItemId;

    @ExcelProperty(value = "版本号")
    private Integer versionNo;          // 修正为 Integer

    @ExcelProperty(value = "结构版本")
    private String schemaVersion;

    @ExcelProperty(value = "状态")
    private String status;              // draft/reviewing/published/archived

    // 该字段不需要 @TableField，VO 仅用于展示
    private EmergencyLibraryItemDefinition definitionJson;

    @ExcelProperty(value = "定义哈希")
    private String definitionHash;

    @ExcelProperty(value = "指南来源")
    private String sourceGuideline;

    @ExcelProperty(value = "指南版本")
    private String sourceVersion;

    @ExcelProperty(value = "生效开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveFrom;

    @ExcelProperty(value = "生效结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveTo;

    @ExcelProperty(value = "变更原因")
    private String changeReason;

    @ExcelProperty(value = "审核人")
    private Long reviewedBy;

    @ExcelProperty(value = "审核时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewedAt;
}

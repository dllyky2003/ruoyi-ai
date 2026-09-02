package org.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.ruoyi.system.domain.EmergencyTeachingCaseVersion;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.ruoyi.common.excel.annotation.ExcelDictFormat;
import org.ruoyi.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.system.domain.definition.EmergencyTeachingCaseDefinition;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 病例定义版本；published 后禁止 UPDATE视图对象 emergency_teaching_case_version
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyTeachingCaseVersion.class)
public class EmergencyTeachingCaseVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所有版本列表（用于父子表联合查询）
     */
    private List<EmergencyTeachingCaseVersionVo> versions;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "")
    private Long id;

    /**
     * 部门id
     */
    @ExcelProperty(value = "部门id")
    private Long deptId;

    /**
     * 用户id
     */
    @ExcelProperty(value = "用户id")
    private Long userId;

    /**
     * 排序号
     */
    @ExcelProperty(value = "排序号")
    private Long orderNum;

    /**
     * 关联病例ID
     */
    @ExcelProperty(value = "")
    private Long caseId;

    /**
     * 版本号
     */
    @ExcelProperty(value = "")
    private Long versionNo;

    /**
     * Schema版本
     */
    @ExcelProperty(value = "")
    private String schemaVersion;

    /**
     * draft/reviewing/published/archived
     */
    @ExcelProperty(value = "draft/reviewing/published/archived")
    private String status;

    /**
     * 病例方案唯一事实源（超大JSON），详情页使用
     */
    private EmergencyTeachingCaseDefinition definitionJson;

    /**
     * 定义哈希值
     */
    @ExcelProperty(value = "")
    private String definitionHash;

    // ========== 以下为从 definition_json 提取的概览字段（用于列表展示，提升性能） ==========

    /**
     * 病例标题（对应 caseMeta.title）
     */
    @ExcelProperty(value = "病例标题")
    private String title;

    /**
     * 患者年龄（岁），对应 clinicalContext.patient.demographics.age
     */
    @ExcelProperty(value = "患者年龄")
    private Integer age;

    /**
     * 患者性别（male/female/other）
     */
    @ExcelProperty(value = "患者性别")
    private String sex;

    /**
     * 难度等级（初级/中级/高级）
     */
    @ExcelProperty(value = "难度等级")
    private String difficulty;

    /**
     * 阶段总数（phases 数组长度）
     */
    @ExcelProperty(value = "阶段总数")
    private Integer phasesCount;

    /**
     * 预计时长（分钟），对应 caseMeta.estimatedDurationMin
     */
    @ExcelProperty(value = "预计时长(min)")
    private Integer duration;

    /**
     * 标签（逗号分隔字符串）
     */
    @ExcelProperty(value = "标签")
    private String tags;

    /**
     * 简短描述（caseMeta.description）
     */
    @ExcelProperty(value = "描述")
    private String description;

    /**
     * 引用指南版本（如 "2025"），对应 governance.guidelineProfileRef.version
     */
    @ExcelProperty(value = "指南版本")
    private String guidelineProfile;

    // ========== 原有字段 ==========

    @ExcelProperty(value = "")
    private Long basedOnVersionId;

    @ExcelProperty(value = "")
    private String changeSummary;

    @ExcelProperty(value = "")
    private Long reviewedBy;

    @ExcelProperty(value = "")
    private Date reviewedAt;

    @ExcelProperty(value = "")
    private Long publishedBy;

    @ExcelProperty(value = "")
    private Date publishedAt;

    // ========== 关联子表集合（用于详情查询） ==========

    /**
     * 引用的公共库列表
     */
    private List<EmergencyCaseVersionLibraryRefVo> libraryRefs;

    /**
     * 资源引用列表
     */
    private List<EmergencyCaseVersionResourceRefVo> resourceRefs;

    /**
     * 规则列表
     */
    private List<EmergencyCaseVersionRuleIndexVo> rules;

    /**
     * 评分项列表
     */
    private List<EmergencyCaseVersionScoreIndexVo> scores;

    /**
     * 阶段列表
     */
    private List<EmergencyCaseVersionStageIndexVo> stages;
}

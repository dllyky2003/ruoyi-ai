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
     *
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
     *
     */
    @ExcelProperty(value = "")
    private Long caseId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long versionNo;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String schemaVersion;

    /**
     * draft/reviewing/published/archived
     */
    @ExcelProperty(value = "draft/reviewing/published/archived")
    private String status;


    private EmergencyTeachingCaseDefinition definitionJson;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String definitionHash;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long basedOnVersionId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String changeSummary;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long reviewedBy;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Date reviewedAt;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long publishedBy;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Date publishedAt;
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

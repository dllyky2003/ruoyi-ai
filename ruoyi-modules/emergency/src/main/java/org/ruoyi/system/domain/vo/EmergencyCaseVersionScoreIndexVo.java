package org.ruoyi.system.domain.vo;

import org.ruoyi.system.domain.EmergencyCaseVersionScoreIndex;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.ruoyi.common.excel.annotation.ExcelDictFormat;
import org.ruoyi.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;



/**
 * 病例版本评分项索引视图对象 emergency_case_version_score_index
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyCaseVersionScoreIndex.class)
public class EmergencyCaseVersionScoreIndexVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private Long caseVersionId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String metricConfigId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long libraryItemId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long libraryVersionId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long weight;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long enabled;

    String libraryName;
    private EmergencyLibraryItemDefinition libraryDefinitionJson;
    private Integer libraryVersionNo;



    public void setLibraryDefinitionJson(EmergencyLibraryItemDefinition libraryDefinitionJson) {
        this.libraryDefinitionJson = libraryDefinitionJson;
    }

    public EmergencyLibraryItemDefinition getLibraryDefinitionJson() {
        return libraryDefinitionJson;
    }

    public void setLibraryName(String libraryName) {
        this.libraryName = libraryName;
    }

    public String getLibraryName() {

        return libraryName;
    }

    public void setLibraryVersionNo(Integer libraryVersionNo) {
        this.libraryVersionNo = libraryVersionNo;
    }

    public Integer getLibraryVersionNo() {
        return libraryVersionNo;
    }
}

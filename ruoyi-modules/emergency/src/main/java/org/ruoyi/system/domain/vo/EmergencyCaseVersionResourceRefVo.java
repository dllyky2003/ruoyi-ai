package org.ruoyi.system.domain.vo;

import org.ruoyi.system.domain.EmergencyCaseVersionResourceRef;
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
 * 病例版本引用的资源配置与公共库版本映射视图对象 emergency_case_version_resource_ref
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyCaseVersionResourceRef.class)
public class EmergencyCaseVersionResourceRefVo implements Serializable {

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
    private String resourceConfigId;

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
    private String resourceType;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long enabled;


    private String libraryName;

    private  EmergencyLibraryItemDefinition libraryDefinitionJson;

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

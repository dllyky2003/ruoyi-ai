package org.ruoyi.system.domain.vo;

import org.ruoyi.system.domain.EmergencyCaseVersionLibraryRef;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.ruoyi.common.excel.annotation.ExcelDictFormat;
import org.ruoyi.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;



/**
 * 病例引用了哪些公共库版本；用于影响分析与发布校验视图对象 emergency_case_version_library_ref
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyCaseVersionLibraryRef.class)
public class EmergencyCaseVersionLibraryRefVo implements Serializable {

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
    private String refType;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String jsonPath;


}

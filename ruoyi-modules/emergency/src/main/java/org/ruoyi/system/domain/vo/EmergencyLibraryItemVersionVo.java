package org.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.ruoyi.system.domain.EmergencyLibraryItemVersion;
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
 * 公共库冻结定义；published 后禁止 UPDATE视图对象 emergency_library_item_version
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
    private Long libraryItemId;

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

    /**
     *
     */

    private EmergencyLibraryItemDefinition definitionJson;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String definitionHash;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String sourceGuideline;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String sourceVersion;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Date effectiveFrom;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Date effectiveTo;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String changeReason;

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


}

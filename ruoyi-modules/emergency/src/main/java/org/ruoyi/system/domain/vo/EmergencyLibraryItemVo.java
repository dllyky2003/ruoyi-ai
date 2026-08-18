package org.ruoyi.system.domain.vo;

import org.ruoyi.system.domain.EmergencyLibraryItem;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.ruoyi.common.excel.annotation.ExcelDictFormat;
import org.ruoyi.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;


/**
 * 公共库对象身份视图对象 emergency_library_item
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyLibraryItem.class)
public class EmergencyLibraryItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 所有版本列表（用于父子表联合查询）
     */
    private List<EmergencyLibraryItemVersionVo> versions;
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
     * 0=平台标准库(所有用户可见)，非0=机构级公共库
     */
    @ExcelProperty(value = "0=平台标准库(所有用户可见)，非0=机构级公共库")
    private Long organizationId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String libraryType;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String itemCode;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String name;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String status;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long currentPublishedVersionId;


}

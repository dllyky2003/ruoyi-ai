package org.ruoyi.system.domain.vo;

import org.ruoyi.system.domain.EmergencyMediaAsset;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.ruoyi.common.excel.annotation.ExcelDictFormat;
import org.ruoyi.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.system.domain.definition.EmergencyMediaAssetMetadataDefinition;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;



/**
 * 文件仅存对象存储，数据库保存元数据视图对象 emergency_media_asset
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyMediaAsset.class)
public class EmergencyMediaAssetVo implements Serializable {

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
    private Long organizationId;

    /**
     * image/audio/video/ecg/pdf/waveform
     */
    @ExcelProperty(value = "image/audio/video/ecg/pdf/waveform")
    private String assetType;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String storageKey;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String originalFilename;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String mimeType;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long byteSize;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String sha256;


    private EmergencyMediaAssetMetadataDefinition metadataJson;


}

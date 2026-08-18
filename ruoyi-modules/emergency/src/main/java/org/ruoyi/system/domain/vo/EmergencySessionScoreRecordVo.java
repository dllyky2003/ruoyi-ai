package org.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.ruoyi.system.domain.EmergencySessionScoreRecord;
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
 * 会话评分记录（追加式）视图对象 emergency_session_score_record
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencySessionScoreRecord.class)
public class EmergencySessionScoreRecordVo implements Serializable {

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
    private Long sessionId;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String metricCode;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private Long score;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private Long maxScore;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String result;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String evidenceJson;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String explanation;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private Date determinedAt;


}

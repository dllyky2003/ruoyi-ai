package org.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.ruoyi.system.domain.EmergencySessionStateSnapshot;
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
 * 会话状态快照（追加式）视图对象 emergency_session_state_snapshot
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencySessionStateSnapshot.class)
public class EmergencySessionStateSnapshotVo implements Serializable {

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
    private Long sequenceNo;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private Date occurredAt;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String stateJson;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String stateHash;


}

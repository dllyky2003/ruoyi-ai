package org.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.ruoyi.system.domain.EmergencyTrainingSession;
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
 * 训练会话视图对象 emergency_training_session
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EmergencyTrainingSession.class)
public class EmergencyTrainingSessionVo implements Serializable {

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
    private String sessionCode;

    /**
     * 机构/单位隔离维度
     */
    @ExcelProperty(value = "机构/单位隔离维度")
    private Long organizationId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long caseVersionId;

    /**
     *
     */
    @ExcelProperty(value = "")
    private String caseDefinitionHash;

    /**
     * created/running/paused/completed/aborted
     */
    @ExcelProperty(value = "created/running/paused/completed/aborted")
    private String status;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Date startedAt;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Date endedAt;
    /**
     * 事件日志列表
     */
    private List<EmergencySessionEventLogVo> eventLogs;

    /**
     * 评分记录列表
     */
    private List<EmergencySessionScoreRecordVo> scoreRecords;

    /**
     * 状态快照列表
     */
    private List<EmergencySessionStateSnapshotVo> stateSnapshots;

}

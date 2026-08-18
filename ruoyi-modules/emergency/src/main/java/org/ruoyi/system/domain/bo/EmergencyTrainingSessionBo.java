package org.ruoyi.system.domain.bo;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.system.domain.EmergencyTrainingSession;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 训练会话业务对象 emergency_training_session
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EmergencyTrainingSession.class, reverseConvertGenerate = false)
public class EmergencyTrainingSessionBo extends BaseEntity {

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 部门id
     */
    private Long deptId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 排序号
     */
    private Long orderNum;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private String sessionCode;

    /**
     * 机构/单位隔离维度
     */
    @NotNull(message = "机构/单位隔离维度不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long organizationId;

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long caseVersionId;

    /**
     *
     */
    @NotBlank(message = "不能为空", groups = { AddGroup.class, EditGroup.class })
    private String caseDefinitionHash;

    /**
     * created/running/paused/completed/aborted
     */
    @NotBlank(message = "created/running/paused/completed/aborted不能为空", groups = { AddGroup.class, EditGroup.class })
    private String status;

    /**
     *
     */
    private Date startedAt;

    /**
     *
     */
    private Date endedAt;


}

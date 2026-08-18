package org.ruoyi.system.domain;
import com.baomidou.mybatisplus.annotation.*;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.generator.constant.GenConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_teaching_case")
public class EmergencyTeachingCase extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long deptId;
    private Long userId;
    private Integer orderNum;
    private Long createDept;
    private String caseCode;
    private Long organizationId;
    private String name;
    private String status; // draft/reviewing/published/archived
    private Long currentPublishedVersionId;
    @TableLogic
    private Integer delFlag;
}

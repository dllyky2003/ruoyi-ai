package org.ruoyi.system.domain;
import com.baomidou.mybatisplus.annotation.*;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.generator.constant.GenConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.system.domain.definition.EmergencyTeachingCaseDefinition;

import java.io.Serializable;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_teaching_case_version")
public class EmergencyTeachingCaseVersion extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long deptId;
    private Long userId;
    private Integer orderNum;
    private Long createDept;
    private Long caseId;
    private Integer versionNo;
    private String schemaVersion;
    private String status;
    private EmergencyTeachingCaseDefinition definitionJson; // 存超大JSON
    private String definitionHash;
    private Long basedOnVersionId;
    private String changeSummary;
    private Long reviewedBy;
    private Date reviewedAt;
    private Long publishedBy;
    private Date publishedAt;
    private Integer delFlag;
}

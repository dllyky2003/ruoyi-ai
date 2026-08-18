package org.ruoyi.system.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.system.domain.definition.*;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;

import java.util.Date;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_library_item_version")
public class EmergencyLibraryItemVersion extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long libraryItemId;
    private Integer versionNo;
    private String schemaVersion;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class, value = "definition_json")
    private EmergencyLibraryItemDefinition definitionJson;

    private String definitionHash;
    private String sourceGuideline;
    private String sourceVersion;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String changeReason;
    private Long reviewedBy;
    private Date reviewedAt;
}

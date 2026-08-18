package org.ruoyi.system.domain;
import com.baomidou.mybatisplus.annotation.*;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.generator.constant.GenConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.system.domain.definition.EmergencyMediaAssetMetadataDefinition;

import java.io.Serializable;
import java.util.Date;


@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_media_asset")
public class EmergencyMediaAsset extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long deptId;
    private Long userId;
    private Integer orderNum;
    private Long createDept;
    private Long organizationId;
    private String assetType; // image/audio/video/ecg/pdf/waveform
    private String storageKey;
    private String originalFilename;
    private String mimeType;
    private Long byteSize;
    private String sha256;
    private EmergencyMediaAssetMetadataDefinition metadataJson;
    @TableLogic
    private Integer delFlag;
}

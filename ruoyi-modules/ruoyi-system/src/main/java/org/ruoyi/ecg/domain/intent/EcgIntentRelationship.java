package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;

/**
 * 意图关系表 ecg_intent_relationship
 */
public class EcgIntentRelationship implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 源意图编码 */
    private String sourceIntentId;

    /** 目标意图编码 */
    private String targetIntentId;

    /** 关系类型：synonym / hierarchy / excludes / implies / suggests */
    private String relationshipType;

    /** 关系优先级 */
    private Integer priority;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private String createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private String updateTime;

    /** 备注 */
    private String remark;

    // ====== 非表字段 ======
    /** 源意图定义 */
    private EcgIntentDefinition sourceIntent;

    /** 目标意图定义 */
    private EcgIntentDefinition targetIntent;

    // ====== Getter / Setter ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceIntentId() { return sourceIntentId; }
    public void setSourceIntentId(String sourceIntentId) { this.sourceIntentId = sourceIntentId; }

    public String getTargetIntentId() { return targetIntentId; }
    public void setTargetIntentId(String targetIntentId) { this.targetIntentId = targetIntentId; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public EcgIntentDefinition getSourceIntent() { return sourceIntent; }
    public void setSourceIntent(EcgIntentDefinition sourceIntent) { this.sourceIntent = sourceIntent; }

    public EcgIntentDefinition getTargetIntent() { return targetIntent; }
    public void setTargetIntent(EcgIntentDefinition targetIntent) { this.targetIntent = targetIntent; }
}

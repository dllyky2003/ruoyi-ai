package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.util.List;

/**
 * 复合指令模板表 ecg_compound_intent_template
 */
public class EcgCompoundIntentTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 模板编码 */
    private String templateId;

    /** 模板名称 */
    private String templateName;

    /** 触发关键词（JSON数组） */
    private String triggerKeywords;

    /** 子意图编码列表（JSON有序数组） */
    private String childIntents;

    /** 组合逻辑：sequential / parallel / conditional */
    private String separationLogic;

    /** 描述 */
    private String description;

    /** 是否启用：0禁用 1启用 */
    private Integer isActive;

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
    /** 子意图完整对象列表 */
    private List<EcgIntentDefinition> childIntentObjects;

    // ====== Getter / Setter ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getTriggerKeywords() { return triggerKeywords; }
    public void setTriggerKeywords(String triggerKeywords) { this.triggerKeywords = triggerKeywords; }

    public String getChildIntents() { return childIntents; }
    public void setChildIntents(String childIntents) { this.childIntents = childIntents; }

    public String getSeparationLogic() { return separationLogic; }
    public void setSeparationLogic(String separationLogic) { this.separationLogic = separationLogic; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

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

    public List<EcgIntentDefinition> getChildIntentObjects() { return childIntentObjects; }
    public void setChildIntentObjects(List<EcgIntentDefinition> childIntentObjects) { this.childIntentObjects = childIntentObjects; }
}

package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.util.List;

/**
 * 意图定义表 ecg_intent_definition
 */
public class EcgIntentDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 意图编码（主键） */
    private String intentId;

    /** 引擎标准动作 */
    private String actionType;

    /** 大类：electrotherapy / medication / cpr / ... */
    private String category;

    /** 子类 */
    private String subCategory;

    /** 默认参数（JSON字符串） */
    private String defaultParams;

    /** 必填槽位名（分号分隔） */
    private String requiresSlots;

    /** 可选槽位名（分号分隔） */
    private String optionalSlots;

    /** 优先级 */
    private Integer priority;

    /** 是否启用：0禁用 1启用 */
    private Integer isActive;

    /** 中文业务描述 */
    private String description;

    /** 临床注意事项 */
    private String clinicalNote;

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
    /** 绑定的槽位列表（用于前端展示） */
    private List<org.ruoyi.ecg.domain.intent.EcgIntentSlotBinding> slotBindings;

    // ====== Getter / Setter ======
    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    public String getDefaultParams() { return defaultParams; }
    public void setDefaultParams(String defaultParams) { this.defaultParams = defaultParams; }

    public String getRequiresSlots() { return requiresSlots; }
    public void setRequiresSlots(String requiresSlots) { this.requiresSlots = requiresSlots; }

    public String getOptionalSlots() { return optionalSlots; }
    public void setOptionalSlots(String optionalSlots) { this.optionalSlots = optionalSlots; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getClinicalNote() { return clinicalNote; }
    public void setClinicalNote(String clinicalNote) { this.clinicalNote = clinicalNote; }

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

    public List<EcgIntentSlotBinding> getSlotBindings() { return slotBindings; }
    public void setSlotBindings(List<EcgIntentSlotBinding> slotBindings) { this.slotBindings = slotBindings; }
}

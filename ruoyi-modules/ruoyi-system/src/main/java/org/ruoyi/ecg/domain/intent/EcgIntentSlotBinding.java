package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;

/**
 * 意图-槽位绑定表 ecg_intent_slot_binding
 */
public class EcgIntentSlotBinding implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 意图编码 */
    private String intentId;

    /** 槽位名称 */
    private String slotName;

    /** 绑定类型：required / optional / conditional */
    private String bindingType;

    /** 条件表达式（JSON字符串） */
    private String bindingCondition;

    /** 参数提取顺序 */
    private Integer orderIndex;

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
    /** 槽位定义（关联对象） */
    private EcgSlotDefinition slotDefinition;

    /** 意图定义（关联对象） */
    private EcgIntentDefinition intentDefinition;

    /** 槽位显示名称（便于前端展示） */
    private String slotDisplayName;

    // ====== Getter / Setter ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }

    public String getSlotName() { return slotName; }
    public void setSlotName(String slotName) { this.slotName = slotName; }

    public String getBindingType() { return bindingType; }
    public void setBindingType(String bindingType) { this.bindingType = bindingType; }

    public String getBindingCondition() { return bindingCondition; }
    public void setBindingCondition(String bindingCondition) { this.bindingCondition = bindingCondition; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

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

    public EcgSlotDefinition getSlotDefinition() { return slotDefinition; }
    public void setSlotDefinition(EcgSlotDefinition slotDefinition) { this.slotDefinition = slotDefinition; }

    public EcgIntentDefinition getIntentDefinition() { return intentDefinition; }
    public void setIntentDefinition(EcgIntentDefinition intentDefinition) { this.intentDefinition = intentDefinition; }

    public String getSlotDisplayName() { return slotDisplayName; }
    public void setSlotDisplayName(String slotDisplayName) { this.slotDisplayName = slotDisplayName; }
}

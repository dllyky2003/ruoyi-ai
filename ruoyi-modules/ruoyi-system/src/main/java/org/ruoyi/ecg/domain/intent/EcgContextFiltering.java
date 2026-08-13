package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.util.List;

/**
 * 上下文过滤表 ecg_context_filtering
 */
public class EcgContextFiltering implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 流程阶段编码 */
    private String phaseId;

    /** 阶段名称 */
    private String phaseName;

    /** 允许的意图编码列表（JSON数组） */
    private String allowedIntents;

    /** 优先推荐意图列表（JSON数组） */
    private String priorityIntents;

    /** 禁用意图列表（JSON数组） */
    private String disabledIntents;

    /** 附加条件规则（JSON字符串） */
    private String conditionRule;

    /** 排序权重 */
    private Integer orderIndex;

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
    /** 允许的意图完整对象列表 */
    private List<EcgIntentDefinition> allowedIntentObjects;

    /** 优先意图完整对象列表 */
    private List<EcgIntentDefinition> priorityIntentObjects;

    // ====== Getter / Setter ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhaseId() { return phaseId; }
    public void setPhaseId(String phaseId) { this.phaseId = phaseId; }

    public String getPhaseName() { return phaseName; }
    public void setPhaseName(String phaseName) { this.phaseName = phaseName; }

    public String getAllowedIntents() { return allowedIntents; }
    public void setAllowedIntents(String allowedIntents) { this.allowedIntents = allowedIntents; }

    public String getPriorityIntents() { return priorityIntents; }
    public void setPriorityIntents(String priorityIntents) { this.priorityIntents = priorityIntents; }

    public String getDisabledIntents() { return disabledIntents; }
    public void setDisabledIntents(String disabledIntents) { this.disabledIntents = disabledIntents; }

    public String getConditionRule() { return conditionRule; }
    public void setConditionRule(String conditionRule) { this.conditionRule = conditionRule; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

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

    public List<EcgIntentDefinition> getAllowedIntentObjects() { return allowedIntentObjects; }
    public void setAllowedIntentObjects(List<EcgIntentDefinition> allowedIntentObjects) { this.allowedIntentObjects = allowedIntentObjects; }

    public List<EcgIntentDefinition> getPriorityIntentObjects() { return priorityIntentObjects; }
    public void setPriorityIntentObjects(List<EcgIntentDefinition> priorityIntentObjects) { this.priorityIntentObjects = priorityIntentObjects; }
}

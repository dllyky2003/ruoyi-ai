package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 槽位定义表 ecg_slot_definition
 */
public class EcgSlotDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 槽位名称（主键） */
    private String slotName;

    /** 显示名称 */
    private String displayName;

    /** 数据类型：number / string / enum / boolean / integer */
    private String dataType;

    /** 物理单位 */
    private String unit;

    /** 单位别名映射（JSON字符串） */
    private String unitAliases;

    /** 枚举值列表（JSON数组） */
    private String allowedValues;

    /** 值别名映射（JSON字符串） */
    private String valueAliases;

    /** 数值最小值 */
    private BigDecimal minValue;

    /** 数值最大值 */
    private BigDecimal maxValue;

    /** 默认值 */
    private String defaultValue;

    /** 正则校验表达式 */
    private String regexPattern;

    /** 参数缺失时的引导话术 */
    private String promptIfMissing;

    /** 参数非法时的提示话术 */
    private String promptIfInvalid;

    /** 是否必填：0可选 1必填 */
    private Integer isRequired;

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
    /** 已绑定的意图数量（列表展示用） */
    private Integer bindingCount;

    // ====== Getter / Setter ======
    public String getSlotName() { return slotName; }
    public void setSlotName(String slotName) { this.slotName = slotName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getUnitAliases() { return unitAliases; }
    public void setUnitAliases(String unitAliases) { this.unitAliases = unitAliases; }

    public String getAllowedValues() { return allowedValues; }
    public void setAllowedValues(String allowedValues) { this.allowedValues = allowedValues; }

    public String getValueAliases() { return valueAliases; }
    public void setValueAliases(String valueAliases) { this.valueAliases = valueAliases; }

    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }

    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getRegexPattern() { return regexPattern; }
    public void setRegexPattern(String regexPattern) { this.regexPattern = regexPattern; }

    public String getPromptIfMissing() { return promptIfMissing; }
    public void setPromptIfMissing(String promptIfMissing) { this.promptIfMissing = promptIfMissing; }

    public String getPromptIfInvalid() { return promptIfInvalid; }
    public void setPromptIfInvalid(String promptIfInvalid) { this.promptIfInvalid = promptIfInvalid; }

    public Integer getIsRequired() { return isRequired; }
    public void setIsRequired(Integer isRequired) { this.isRequired = isRequired; }

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

    public Integer getBindingCount() { return bindingCount; }
    public void setBindingCount(Integer bindingCount) { this.bindingCount = bindingCount; }
}

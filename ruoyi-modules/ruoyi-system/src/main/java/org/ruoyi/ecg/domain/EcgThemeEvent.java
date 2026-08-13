package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.util.List;

public class EcgThemeEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;           // 事件ID
    private String themeId;           // 教学方案ID
    private String eventName;         // 事件名称
    private Integer iconIndex;        // 图标索引
    private Integer sortNum;          // 排序

    /** 非数据库字段：关联的步骤分类列表 */
    private List<EcgStepCategory> stepCategories;

    // Getter and Setter
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public Integer getIconIndex() { return iconIndex; }
    public void setIconIndex(Integer iconIndex) { this.iconIndex = iconIndex; }
    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
    public List<EcgStepCategory> getStepCategories() { return stepCategories; }
    public void setStepCategories(List<EcgStepCategory> stepCategories) { this.stepCategories = stepCategories; }
}

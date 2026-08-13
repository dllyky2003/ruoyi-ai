package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.util.List;

public class EcgStepCategory implements Serializable {
    private static final long serialVersionUID = 1L;

    private String stepCategoryId;    // 步骤分类ID
    private String eventId;           // 事件ID
    private String stepCategoryName;  // 步骤分类名称
    private Integer sortNum;          // 排序

    /** 非数据库字段：关联的操作列表 */
    private List<EcgEventStepFavorite> favorites;

    // Getter and Setter
    public String getStepCategoryId() { return stepCategoryId; }
    public void setStepCategoryId(String stepCategoryId) { this.stepCategoryId = stepCategoryId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getStepCategoryName() { return stepCategoryName; }
    public void setStepCategoryName(String stepCategoryName) { this.stepCategoryName = stepCategoryName; }
    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
    public List<EcgEventStepFavorite> getFavorites() { return favorites; }
    public void setFavorites(List<EcgEventStepFavorite> favorites) { this.favorites = favorites; }
}

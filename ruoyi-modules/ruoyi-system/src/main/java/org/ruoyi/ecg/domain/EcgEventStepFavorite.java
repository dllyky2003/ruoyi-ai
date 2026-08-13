package org.ruoyi.ecg.domain;

import java.io.Serializable;

public class EcgEventStepFavorite implements Serializable {
    private static final long serialVersionUID = 1L;

    private String favoriteId;        // 操作ID
    private String stepCategoryId;    // 步骤分类ID
    private String favoriteName;      // 操作名称
    private Integer doActionCount;    // 执行次数
    private Integer sortNum;          // 排序

    // Getter and Setter
    public String getFavoriteId() { return favoriteId; }
    public void setFavoriteId(String favoriteId) { this.favoriteId = favoriteId; }
    public String getStepCategoryId() { return stepCategoryId; }
    public void setStepCategoryId(String stepCategoryId) { this.stepCategoryId = stepCategoryId; }
    public String getFavoriteName() { return favoriteName; }
    public void setFavoriteName(String favoriteName) { this.favoriteName = favoriteName; }
    public Integer getDoActionCount() { return doActionCount; }
    public void setDoActionCount(Integer doActionCount) { this.doActionCount = doActionCount; }
    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
}

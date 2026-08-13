package org.ruoyi.ecg.domain;

import java.io.Serializable;

public class EcgThemeSource implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sourceId;            // 资源关系ID
    private String themeId;             // 教学方案ID
    private String sourceBusinessId;    // 原始资源ID
    private String sourceName;          // 资源名称
    private String sourceUrl;           // 资源地址
    private Integer positionNum;        // 位置
    private Integer sourceType;         // 资源类型
    private String localPath;           // 本地路径

    // Getter and Setter
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    public String getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(String sourceBusinessId) { this.sourceBusinessId = sourceBusinessId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public Integer getPositionNum() { return positionNum; }
    public void setPositionNum(Integer positionNum) { this.positionNum = positionNum; }
    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
}

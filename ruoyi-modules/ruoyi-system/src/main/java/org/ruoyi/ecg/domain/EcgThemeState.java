package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class EcgThemeState implements Serializable {
    private static final long serialVersionUID = 1L;

    private String stateId;           // 状态ID
    private String themeId;           // 教学方案ID
    private String stateName;         // 状态名称
    private String rhythmName;        // 心律名称
    private Integer hr;               // 心率
    private Integer hrChangeTime;     // 心率变化时间
    private String hrChangeType;      // 心率变化类型
    private Integer spo2;             // 血氧
    private Integer spo2ChangeTime;   // 血氧变化时间
    private String spo2ChangeType;    // 血氧变化类型
    private Integer co2;              // 二氧化碳
    private Integer co2ChangeTime;    // 二氧化碳变化时间
    private String co2ChangeType;     // 二氧化碳变化类型
    private Integer nbpLow;           // 无创血压低值
    private Integer nbpHigh;          // 无创血压高值
    private Integer nbpChangeTime;    // 无创血压变化时间
    private String nbpChangeType;     // 无创血压变化类型
    private BigDecimal bodyTemperature; // 体温
    private Integer breathRate;       // 呼吸频率
    private Integer autoPlayTime;     // 自动播放时间
    private Integer sortNum;          // 排序

    // Getter and Setter
    public String getStateId() { return stateId; }
    public void setStateId(String stateId) { this.stateId = stateId; }
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }
    public String getRhythmName() { return rhythmName; }
    public void setRhythmName(String rhythmName) { this.rhythmName = rhythmName; }
    public Integer getHr() { return hr; }
    public void setHr(Integer hr) { this.hr = hr; }
    public Integer getHrChangeTime() { return hrChangeTime; }
    public void setHrChangeTime(Integer hrChangeTime) { this.hrChangeTime = hrChangeTime; }
    public String getHrChangeType() { return hrChangeType; }
    public void setHrChangeType(String hrChangeType) { this.hrChangeType = hrChangeType; }
    public Integer getSpo2() { return spo2; }
    public void setSpo2(Integer spo2) { this.spo2 = spo2; }
    public Integer getSpo2ChangeTime() { return spo2ChangeTime; }
    public void setSpo2ChangeTime(Integer spo2ChangeTime) { this.spo2ChangeTime = spo2ChangeTime; }
    public String getSpo2ChangeType() { return spo2ChangeType; }
    public void setSpo2ChangeType(String spo2ChangeType) { this.spo2ChangeType = spo2ChangeType; }
    public Integer getCo2() { return co2; }
    public void setCo2(Integer co2) { this.co2 = co2; }
    public Integer getCo2ChangeTime() { return co2ChangeTime; }
    public void setCo2ChangeTime(Integer co2ChangeTime) { this.co2ChangeTime = co2ChangeTime; }
    public String getCo2ChangeType() { return co2ChangeType; }
    public void setCo2ChangeType(String co2ChangeType) { this.co2ChangeType = co2ChangeType; }
    public Integer getNbpLow() { return nbpLow; }
    public void setNbpLow(Integer nbpLow) { this.nbpLow = nbpLow; }
    public Integer getNbpHigh() { return nbpHigh; }
    public void setNbpHigh(Integer nbpHigh) { this.nbpHigh = nbpHigh; }
    public Integer getNbpChangeTime() { return nbpChangeTime; }
    public void setNbpChangeTime(Integer nbpChangeTime) { this.nbpChangeTime = nbpChangeTime; }
    public String getNbpChangeType() { return nbpChangeType; }
    public void setNbpChangeType(String nbpChangeType) { this.nbpChangeType = nbpChangeType; }
    public BigDecimal getBodyTemperature() { return bodyTemperature; }
    public void setBodyTemperature(BigDecimal bodyTemperature) { this.bodyTemperature = bodyTemperature; }
    public Integer getBreathRate() { return breathRate; }
    public void setBreathRate(Integer breathRate) { this.breathRate = breathRate; }
    public Integer getAutoPlayTime() { return autoPlayTime; }
    public void setAutoPlayTime(Integer autoPlayTime) { this.autoPlayTime = autoPlayTime; }
    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
}

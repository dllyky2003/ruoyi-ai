package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class EcgAlarmSetup implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long alarmId;              // 报警配置ID
    private String themeId;            // 教学方案ID
    private Integer hrLow;             // 心率低限
    private Integer hrHigh;            // 心率高限
    private Integer spo2Low;           // 血氧低限
    private Integer spo2High;          // 血氧高限
    private Integer co2Low;            // 二氧化碳低限
    private Integer co2High;           // 二氧化碳高限
    private Integer nibpLow;           // 无创血压低限
    private Integer nibpHigh;          // 无创血压高限
    private BigDecimal bodyTemAlarmLow;   // 体温报警低限
    private BigDecimal bodyTemAlarmHigh;  // 体温报警高限
    private Integer breathRateLow;     // 呼吸频率低限
    private Integer breathRateHigh;    // 呼吸频率高限

    // Getter and Setter
    public Long getAlarmId() { return alarmId; }
    public void setAlarmId(Long alarmId) { this.alarmId = alarmId; }
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    public Integer getHrLow() { return hrLow; }
    public void setHrLow(Integer hrLow) { this.hrLow = hrLow; }
    public Integer getHrHigh() { return hrHigh; }
    public void setHrHigh(Integer hrHigh) { this.hrHigh = hrHigh; }
    public Integer getSpo2Low() { return spo2Low; }
    public void setSpo2Low(Integer spo2Low) { this.spo2Low = spo2Low; }
    public Integer getSpo2High() { return spo2High; }
    public void setSpo2High(Integer spo2High) { this.spo2High = spo2High; }
    public Integer getCo2Low() { return co2Low; }
    public void setCo2Low(Integer co2Low) { this.co2Low = co2Low; }
    public Integer getCo2High() { return co2High; }
    public void setCo2High(Integer co2High) { this.co2High = co2High; }
    public Integer getNibpLow() { return nibpLow; }
    public void setNibpLow(Integer nibpLow) { this.nibpLow = nibpLow; }
    public Integer getNibpHigh() { return nibpHigh; }
    public void setNibpHigh(Integer nibpHigh) { this.nibpHigh = nibpHigh; }
    public BigDecimal getBodyTemAlarmLow() { return bodyTemAlarmLow; }
    public void setBodyTemAlarmLow(BigDecimal bodyTemAlarmLow) { this.bodyTemAlarmLow = bodyTemAlarmLow; }
    public BigDecimal getBodyTemAlarmHigh() { return bodyTemAlarmHigh; }
    public void setBodyTemAlarmHigh(BigDecimal bodyTemAlarmHigh) { this.bodyTemAlarmHigh = bodyTemAlarmHigh; }
    public Integer getBreathRateLow() { return breathRateLow; }
    public void setBreathRateLow(Integer breathRateLow) { this.breathRateLow = breathRateLow; }
    public Integer getBreathRateHigh() { return breathRateHigh; }
    public void setBreathRateHigh(Integer breathRateHigh) { this.breathRateHigh = breathRateHigh; }
}

package org.ruoyi.ecg.domain;

import org.ruoyi.common.tenant.core.TenantEntity;

/**
 * ECG状态库对象 ecg_state_library
 *
 * @author ruoyi
 * @date 2026-08-06
 */


public class EcgStateLibrary extends TenantEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态唯一标识（主键） */
    private String stateId;

    /** 状态大类：BRADY(缓慢性), TACHY(快速性), ARREST(骤停), ROSC(复苏后), OTHER(其他) */
    private String categoryCode;

    /** 状态名称（简明扼要，供导师选择） */

    private String stateName;

    /** 状态简称/标签 */

    private String stateAlias;

    /** 波形代码（系统渲染标识） */

    private String waveformCode;

    /** 监护仪显示的波形名称 */

    private String waveformDisplay;

    /** 临床心电图特征详述 */

    private String rhythmDescription;

    /** 严重等级：1-低风险，2-一般，3-警惕，4-危重，5-濒死/停搏 */

    private Integer severityLevel;

    /** 推荐心率-下限 */

    private Integer defaultHrMin;

    /** 推荐心率-上限 */

    private Integer defaultHrMax;

    /** 推荐收缩压（mmHg） */

    private Integer defaultNbpSystolic;

    /** 推荐舒张压（mmHg） */

    private Integer defaultNbpDiastolic;

    /** 推荐血氧饱和度（%） */

    private Integer defaultSpo2;

    /** 推荐呼末二氧化碳（mmHg） */

    private Integer defaultEtco2;

    /** 推荐呼吸频率（次/分） */

    private Integer defaultRr;

    /** 推荐体温（℃） */

    private Double defaultTemp;

    /** 推荐血糖（mmol/L） */

    private Double defaultBloodGlucose;

    /** 参考案例来源 */

    private String referenceCases;

    /** 排序号 */

    private Integer sortNum;

    /** 启用状态（0-停用，1-启用） */
    private String isActive;

    // 以下字段继承自 BaseEntity（remark, createBy, createTime, updateBy, updateTime）
    // 但需手动增加 del_flag 以支持若依逻辑删除（BaseEntity中默认不含此字段）
    /** 逻辑删除标志（0-未删，2-已删） */
    private String delFlag;

    // -------------------- Getter and Setter --------------------
    public String getStateId() { return stateId; }
    public void setStateId(String stateId) { this.stateId = stateId; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }

    public String getStateAlias() { return stateAlias; }
    public void setStateAlias(String stateAlias) { this.stateAlias = stateAlias; }

    public String getWaveformCode() { return waveformCode; }
    public void setWaveformCode(String waveformCode) { this.waveformCode = waveformCode; }

    public String getWaveformDisplay() { return waveformDisplay; }
    public void setWaveformDisplay(String waveformDisplay) { this.waveformDisplay = waveformDisplay; }

    public String getRhythmDescription() { return rhythmDescription; }
    public void setRhythmDescription(String rhythmDescription) { this.rhythmDescription = rhythmDescription; }

    public Integer getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(Integer severityLevel) { this.severityLevel = severityLevel; }

    public Integer getDefaultHrMin() { return defaultHrMin; }
    public void setDefaultHrMin(Integer defaultHrMin) { this.defaultHrMin = defaultHrMin; }

    public Integer getDefaultHrMax() { return defaultHrMax; }
    public void setDefaultHrMax(Integer defaultHrMax) { this.defaultHrMax = defaultHrMax; }

    public Integer getDefaultNbpSystolic() { return defaultNbpSystolic; }
    public void setDefaultNbpSystolic(Integer defaultNbpSystolic) { this.defaultNbpSystolic = defaultNbpSystolic; }

    public Integer getDefaultNbpDiastolic() { return defaultNbpDiastolic; }
    public void setDefaultNbpDiastolic(Integer defaultNbpDiastolic) { this.defaultNbpDiastolic = defaultNbpDiastolic; }

    public Integer getDefaultSpo2() { return defaultSpo2; }
    public void setDefaultSpo2(Integer defaultSpo2) { this.defaultSpo2 = defaultSpo2; }

    public Integer getDefaultEtco2() { return defaultEtco2; }
    public void setDefaultEtco2(Integer defaultEtco2) { this.defaultEtco2 = defaultEtco2; }

    public Integer getDefaultRr() { return defaultRr; }
    public void setDefaultRr(Integer defaultRr) { this.defaultRr = defaultRr; }

    public Double getDefaultTemp() { return defaultTemp; }
    public void setDefaultTemp(Double defaultTemp) { this.defaultTemp = defaultTemp; }

    public Double getDefaultBloodGlucose() { return defaultBloodGlucose; }
    public void setDefaultBloodGlucose(Double defaultBloodGlucose) { this.defaultBloodGlucose = defaultBloodGlucose; }

    public String getReferenceCases() { return referenceCases; }
    public void setReferenceCases(String referenceCases) { this.referenceCases = referenceCases; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }

    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }


}

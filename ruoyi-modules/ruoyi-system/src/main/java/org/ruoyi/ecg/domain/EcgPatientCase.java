package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class EcgPatientCase implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    // ========== 原有字段 ==========
    private String patientCaseId;     // 病例ID (关联 ecg_patient_info.patient_id)
    private String themeId;           // 教学方案ID

    // ========== 联查字段（来自 ecg_patient_info） ==========
    private String patientCode;       // 患者编码
    private String patientName;       // 患者姓名
    private String gender;            // 性别 (M:男 F:女 U:未知)
    private Integer age;              // 年龄
    private String ageUnit;           // 年龄单位 (Y:岁 M:月 D:日)
    private String triageLevel;       // 分诊级别 (R:红色危重 Y:黄色紧急 G:绿色轻伤 B:黑色死亡 N:未分类)
    private String chiefComplaint;    // 主诉
    private String consciousness;     // 意识状态
    private Integer gcsScore;         // GCS评分
    private Integer respiratoryRate;  // 呼吸频率
    private Integer heartRate;        // 心率
    private Integer systolicBp;       // 收缩压
    private Integer diastolicBp;      // 舒张压
    private Integer spo2;             // 血氧饱和度
    private BigDecimal temperature;   // 体温
    private BigDecimal bloodGlucose;  // 血糖
    private String pupilResponse;     // 瞳孔反应

    // ========== Getter and Setter ==========
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPatientCaseId() { return patientCaseId; }
    public void setPatientCaseId(String patientCaseId) { this.patientCaseId = patientCaseId; }

    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }

    public String getPatientCode() { return patientCode; }
    public void setPatientCode(String patientCode) { this.patientCode = patientCode; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getAgeUnit() { return ageUnit; }
    public void setAgeUnit(String ageUnit) { this.ageUnit = ageUnit; }

    public String getTriageLevel() { return triageLevel; }
    public void setTriageLevel(String triageLevel) { this.triageLevel = triageLevel; }

    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }

    public String getConsciousness() { return consciousness; }
    public void setConsciousness(String consciousness) { this.consciousness = consciousness; }

    public Integer getGcsScore() { return gcsScore; }
    public void setGcsScore(Integer gcsScore) { this.gcsScore = gcsScore; }

    public Integer getRespiratoryRate() { return respiratoryRate; }
    public void setRespiratoryRate(Integer respiratoryRate) { this.respiratoryRate = respiratoryRate; }

    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }

    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer systolicBp) { this.systolicBp = systolicBp; }

    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer diastolicBp) { this.diastolicBp = diastolicBp; }

    public Integer getSpo2() { return spo2; }
    public void setSpo2(Integer spo2) { this.spo2 = spo2; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public BigDecimal getBloodGlucose() { return bloodGlucose; }
    public void setBloodGlucose(BigDecimal bloodGlucose) { this.bloodGlucose = bloodGlucose; }

    public String getPupilResponse() { return pupilResponse; }
    public void setPupilResponse(String pupilResponse) { this.pupilResponse = pupilResponse; }
}

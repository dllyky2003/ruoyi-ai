package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.ruoyi.common.tenant.core.TenantEntity;


/**
 * 病人信息对象 ecg_patient_info
 *
 * @author ruoyi
 * @date 2026-08-04
 */
public class EcgPatientInfo extends TenantEntity
{
    private static final long serialVersionUID = 1L;

    /** 病人ID */
    private Long patientId;

    /** 病人编号 (格式: P+YYYYMMDD+流水号) */

    private String patientCode;

    /** 姓名 */

    private String patientName;

    /** 性别 (M:男 F:女 U:未知) */

    private String gender;

    /** 出生日期 */

    private Date birthDate;

    /** 年龄 */

    private Long age;

    /** 年龄单位 (Y:岁 M:月 D:日) */
    private String ageUnit;

    /** 身份证号 */

    private String idCardNumber;

    /** 联系电话 */

    private String phoneNumber;

    /** 紧急联系人 */

    private String emergencyContact;

    /** 紧急联系人电话 */

    private String emergencyPhone;

    /** 详细地址 */

    private String address;

    /** 区域/街道 */

    private String district;

    /** 病人照片URL (支持多张用分号分隔) */

    private String patientPhotoUrl;

    /** 检伤级别 (R:红色危重 Y:黄色紧急 G:绿色轻伤 B:黑色死亡 N:未分类) */

    private String triageLevel;

    /** 检伤分类时间 */

    private Date triageTime;

    /** 检伤人员 */

    private String triagePerson;

    /** 检伤分类说明 */

    private String triageDescription;

    /** 过敏史 (药物/食物等) */

    private String allergies;

    /** 既往史 (慢性病、手术史等) */

    private String pastHistory;

    /** 慢性病史 (高血压/糖尿病/心脏病等) */

    private String chronicDiseases;

    /** 当前用药 */

    private String currentMedication;

    /** 职业 */

    private String occupation;

    /** 婚姻状况 (未婚/已婚/离异/丧偶/未知) */

    private String maritalStatus;

    /** 主诉 */

    private String chiefComplaint;

    /** 现病史 */

    private String presentIllness;

    /** 意识状态 */

    private String consciousness;

    /** GCS评分 (3-15) */

    private Long gcsScore;

    /** 呼吸频率 (次/分) */

    private Long respiratoryRate;

    /** 心率 (次/分) */

    private Long heartRate;

    /** 收缩压 (mmHg) */

    private Long systolicBp;

    /** 舒张压 (mmHg) */

    private Long diastolicBp;

    /** 血氧饱和度 (%) */

    private Long spo2;

    /** 体温 (℃) */

    private BigDecimal temperature;

    /** 血糖 (mmol/L) */

    private BigDecimal bloodGlucose;

    /** 瞳孔反应 */

    private String pupilResponse;

    /** 初步诊断 */

    private String preliminaryDiagnosis;

    /** ICD-10编码 */

    private String diagnosisCode;

    /** 转归 (好转/稳定/恶化/死亡/未知) */

    private String outcomeStatus;

    /** 删除标志 (0:存在 2:删除) */
    private String delFlag;

    public void setPatientId(Long patientId)
    {
        this.patientId = patientId;
    }

    public Long getPatientId()
    {
        return patientId;
    }

    public void setPatientCode(String patientCode)
    {
        this.patientCode = patientCode;
    }

    public String getPatientCode()
    {
        return patientCode;
    }

    public void setPatientName(String patientName)
    {
        this.patientName = patientName;
    }

    public String getPatientName()
    {
        return patientName;
    }

    public void setGender(String gender)
    {
        this.gender = gender;
    }

    public String getGender()
    {
        return gender;
    }

    public void setBirthDate(Date birthDate)
    {
        this.birthDate = birthDate;
    }

    public Date getBirthDate()
    {
        return birthDate;
    }

    public void setAge(Long age)
    {
        this.age = age;
    }

    public Long getAge()
    {
        return age;
    }

    public void setAgeUnit(String ageUnit)
    {
        this.ageUnit = ageUnit;
    }

    public String getAgeUnit()
    {
        return ageUnit;
    }

    public void setIdCardNumber(String idCardNumber)
    {
        this.idCardNumber = idCardNumber;
    }

    public String getIdCardNumber()
    {
        return idCardNumber;
    }

    public void setPhoneNumber(String phoneNumber)
    {
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneNumber()
    {
        return phoneNumber;
    }

    public void setEmergencyContact(String emergencyContact)
    {
        this.emergencyContact = emergencyContact;
    }

    public String getEmergencyContact()
    {
        return emergencyContact;
    }

    public void setEmergencyPhone(String emergencyPhone)
    {
        this.emergencyPhone = emergencyPhone;
    }

    public String getEmergencyPhone()
    {
        return emergencyPhone;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getAddress()
    {
        return address;
    }

    public void setDistrict(String district)
    {
        this.district = district;
    }

    public String getDistrict()
    {
        return district;
    }

    public void setPatientPhotoUrl(String patientPhotoUrl)
    {
        this.patientPhotoUrl = patientPhotoUrl;
    }

    public String getPatientPhotoUrl()
    {
        return patientPhotoUrl;
    }

    public void setTriageLevel(String triageLevel)
    {
        this.triageLevel = triageLevel;
    }

    public String getTriageLevel()
    {
        return triageLevel;
    }

    public void setTriageTime(Date triageTime)
    {
        this.triageTime = triageTime;
    }

    public Date getTriageTime()
    {
        return triageTime;
    }

    public void setTriagePerson(String triagePerson)
    {
        this.triagePerson = triagePerson;
    }

    public String getTriagePerson()
    {
        return triagePerson;
    }

    public void setTriageDescription(String triageDescription)
    {
        this.triageDescription = triageDescription;
    }

    public String getTriageDescription()
    {
        return triageDescription;
    }

    public void setAllergies(String allergies)
    {
        this.allergies = allergies;
    }

    public String getAllergies()
    {
        return allergies;
    }

    public void setPastHistory(String pastHistory)
    {
        this.pastHistory = pastHistory;
    }

    public String getPastHistory()
    {
        return pastHistory;
    }

    public void setChronicDiseases(String chronicDiseases)
    {
        this.chronicDiseases = chronicDiseases;
    }

    public String getChronicDiseases()
    {
        return chronicDiseases;
    }

    public void setCurrentMedication(String currentMedication)
    {
        this.currentMedication = currentMedication;
    }

    public String getCurrentMedication()
    {
        return currentMedication;
    }

    public void setOccupation(String occupation)
    {
        this.occupation = occupation;
    }

    public String getOccupation()
    {
        return occupation;
    }

    public void setMaritalStatus(String maritalStatus)
    {
        this.maritalStatus = maritalStatus;
    }

    public String getMaritalStatus()
    {
        return maritalStatus;
    }

    public void setChiefComplaint(String chiefComplaint)
    {
        this.chiefComplaint = chiefComplaint;
    }

    public String getChiefComplaint()
    {
        return chiefComplaint;
    }

    public void setPresentIllness(String presentIllness)
    {
        this.presentIllness = presentIllness;
    }

    public String getPresentIllness()
    {
        return presentIllness;
    }

    public void setConsciousness(String consciousness)
    {
        this.consciousness = consciousness;
    }

    public String getConsciousness()
    {
        return consciousness;
    }

    public void setGcsScore(Long gcsScore)
    {
        this.gcsScore = gcsScore;
    }

    public Long getGcsScore()
    {
        return gcsScore;
    }

    public void setRespiratoryRate(Long respiratoryRate)
    {
        this.respiratoryRate = respiratoryRate;
    }

    public Long getRespiratoryRate()
    {
        return respiratoryRate;
    }

    public void setHeartRate(Long heartRate)
    {
        this.heartRate = heartRate;
    }

    public Long getHeartRate()
    {
        return heartRate;
    }

    public void setSystolicBp(Long systolicBp)
    {
        this.systolicBp = systolicBp;
    }

    public Long getSystolicBp()
    {
        return systolicBp;
    }

    public void setDiastolicBp(Long diastolicBp)
    {
        this.diastolicBp = diastolicBp;
    }

    public Long getDiastolicBp()
    {
        return diastolicBp;
    }

    public void setSpo2(Long spo2)
    {
        this.spo2 = spo2;
    }

    public Long getSpo2()
    {
        return spo2;
    }

    public void setTemperature(BigDecimal temperature)
    {
        this.temperature = temperature;
    }

    public BigDecimal getTemperature()
    {
        return temperature;
    }

    public void setBloodGlucose(BigDecimal bloodGlucose)
    {
        this.bloodGlucose = bloodGlucose;
    }

    public BigDecimal getBloodGlucose()
    {
        return bloodGlucose;
    }

    public void setPupilResponse(String pupilResponse)
    {
        this.pupilResponse = pupilResponse;
    }

    public String getPupilResponse()
    {
        return pupilResponse;
    }

    public void setPreliminaryDiagnosis(String preliminaryDiagnosis)
    {
        this.preliminaryDiagnosis = preliminaryDiagnosis;
    }

    public String getPreliminaryDiagnosis()
    {
        return preliminaryDiagnosis;
    }

    public void setDiagnosisCode(String diagnosisCode)
    {
        this.diagnosisCode = diagnosisCode;
    }

    public String getDiagnosisCode()
    {
        return diagnosisCode;
    }

    public void setOutcomeStatus(String outcomeStatus)
    {
        this.outcomeStatus = outcomeStatus;
    }

    public String getOutcomeStatus()
    {
        return outcomeStatus;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("patientId", getPatientId())
            .append("patientCode", getPatientCode())
            .append("patientName", getPatientName())
            .append("gender", getGender())
            .append("birthDate", getBirthDate())
            .append("age", getAge())
            .append("ageUnit", getAgeUnit())
            .append("idCardNumber", getIdCardNumber())
            .append("phoneNumber", getPhoneNumber())
            .append("emergencyContact", getEmergencyContact())
            .append("emergencyPhone", getEmergencyPhone())
            .append("address", getAddress())
            .append("district", getDistrict())
            .append("patientPhotoUrl", getPatientPhotoUrl())
            .append("triageLevel", getTriageLevel())
            .append("triageTime", getTriageTime())
            .append("triagePerson", getTriagePerson())
            .append("triageDescription", getTriageDescription())
            .append("allergies", getAllergies())
            .append("pastHistory", getPastHistory())
            .append("chronicDiseases", getChronicDiseases())
            .append("currentMedication", getCurrentMedication())
            .append("occupation", getOccupation())
            .append("maritalStatus", getMaritalStatus())
            .append("chiefComplaint", getChiefComplaint())
            .append("presentIllness", getPresentIllness())
            .append("consciousness", getConsciousness())
            .append("gcsScore", getGcsScore())
            .append("respiratoryRate", getRespiratoryRate())
            .append("heartRate", getHeartRate())
            .append("systolicBp", getSystolicBp())
            .append("diastolicBp", getDiastolicBp())
            .append("spo2", getSpo2())
            .append("temperature", getTemperature())
            .append("bloodGlucose", getBloodGlucose())
            .append("pupilResponse", getPupilResponse())
            .append("preliminaryDiagnosis", getPreliminaryDiagnosis())
            .append("diagnosisCode", getDiagnosisCode())
            .append("outcomeStatus", getOutcomeStatus())
            .append("delFlag", getDelFlag())
            .toString();
    }
}

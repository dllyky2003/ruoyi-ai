package org.ruoyi.ecg.domain;
import org.ruoyi.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;



public class EcgTheme extends TenantEntity {
    private static final long serialVersionUID = 1L;

    private String themeId;          // 教学方案ID
    private String themeName;        // 教学方案名称
    private String memo;             // 教学方案说明
    private String category;         // 教学方案分类
    private String aesKeys;          // AES加密密钥
    private String themeIntro;       // 教学方案介绍
    private String patientInfo;      // 病情描述
    private String currentStateId;   // 当前状态ID
    private String currentEventId;   // 当前事件ID

    /** 非数据库字段：关联的子表数据 */
    private List<EcgThemeState> states;
    private List<EcgThemeEvent> events;
    private List<EcgThemeSource> sources;
    private List<EcgPatientCase> patientCases;
    private EcgAlarmSetup alarmSetup;

    // Getter and Setter
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    public String getThemeName() { return themeName; }
    public void setThemeName(String themeName) { this.themeName = themeName; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAesKeys() { return aesKeys; }
    public void setAesKeys(String aesKeys) { this.aesKeys = aesKeys; }
    public String getThemeIntro() { return themeIntro; }
    public void setThemeIntro(String themeIntro) { this.themeIntro = themeIntro; }
    public String getPatientInfo() { return patientInfo; }
    public void setPatientInfo(String patientInfo) { this.patientInfo = patientInfo; }
    public String getCurrentStateId() { return currentStateId; }
    public void setCurrentStateId(String currentStateId) { this.currentStateId = currentStateId; }
    public String getCurrentEventId() { return currentEventId; }
    public void setCurrentEventId(String currentEventId) { this.currentEventId = currentEventId; }
    public List<EcgThemeState> getStates() { return states; }
    public void setStates(List<EcgThemeState> states) { this.states = states; }
    public List<EcgThemeEvent> getEvents() { return events; }
    public void setEvents(List<EcgThemeEvent> events) { this.events = events; }
    public List<EcgThemeSource> getSources() { return sources; }
    public void setSources(List<EcgThemeSource> sources) { this.sources = sources; }
    public List<EcgPatientCase> getPatientCases() { return patientCases; }
    public void setPatientCases(List<EcgPatientCase> patientCases) { this.patientCases = patientCases; }
    public EcgAlarmSetup getAlarmSetup() { return alarmSetup; }
    public void setAlarmSetup(EcgAlarmSetup alarmSetup) { this.alarmSetup = alarmSetup; }
}

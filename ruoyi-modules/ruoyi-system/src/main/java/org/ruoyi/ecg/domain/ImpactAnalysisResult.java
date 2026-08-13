package org.ruoyi.ecg.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 影响分析结果
 *
 * @author ecg-system
 */
public class ImpactAnalysisResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 受影响的意图编码列表 */
    private List<String> affectedIntents;

    /** 受影响的槽位名称列表 */
    private List<String> affectedSlots;

    /** 影响描述信息 */
    private String message;

    /** 操作建议 */
    private String suggestion;

    public ImpactAnalysisResult() {
        this.affectedIntents = new ArrayList<>();
        this.affectedSlots = new ArrayList<>();
    }

    public List<String> getAffectedIntents() {
        return affectedIntents;
    }

    public void setAffectedIntents(List<String> affectedIntents) {
        this.affectedIntents = affectedIntents;
    }

    public List<String> getAffectedSlots() {
        return affectedSlots;
    }

    public void setAffectedSlots(List<String> affectedSlots) {
        this.affectedSlots = affectedSlots;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}




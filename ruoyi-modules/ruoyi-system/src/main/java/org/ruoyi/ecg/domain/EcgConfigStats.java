package org.ruoyi.ecg.domain;

import java.io.Serializable;

/**
 * 配置统计信息
 *
 * @author ecg-system
 */
public class EcgConfigStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer totalIntents;
    private Integer activeIntents;
    private Integer totalSlots;
    private Integer totalUtterances;
    private Integer totalVariants;
    private Integer totalContexts;          // ★ 新增
    private Integer totalRelationships;
    private Integer totalCompounds;

    // ====== Getter / Setter ======

    public Integer getTotalIntents() {
        return totalIntents;
    }

    public void setTotalIntents(Integer totalIntents) {
        this.totalIntents = totalIntents;
    }

    public Integer getActiveIntents() {
        return activeIntents;
    }

    public void setActiveIntents(Integer activeIntents) {
        this.activeIntents = activeIntents;
    }

    public Integer getTotalSlots() {
        return totalSlots;
    }

    public void setTotalSlots(Integer totalSlots) {
        this.totalSlots = totalSlots;
    }

    public Integer getTotalUtterances() {
        return totalUtterances;
    }

    public void setTotalUtterances(Integer totalUtterances) {
        this.totalUtterances = totalUtterances;
    }

    public Integer getTotalVariants() {
        return totalVariants;
    }

    public void setTotalVariants(Integer totalVariants) {
        this.totalVariants = totalVariants;
    }

    public Integer getTotalContexts() {
        return totalContexts;
    }

    public void setTotalContexts(Integer totalContexts) {
        this.totalContexts = totalContexts;
    }

    public Integer getTotalRelationships() {
        return totalRelationships;
    }

    public void setTotalRelationships(Integer totalRelationships) {
        this.totalRelationships = totalRelationships;
    }

    public Integer getTotalCompounds() {
        return totalCompounds;
    }

    public void setTotalCompounds(Integer totalCompounds) {
        this.totalCompounds = totalCompounds;
    }
}

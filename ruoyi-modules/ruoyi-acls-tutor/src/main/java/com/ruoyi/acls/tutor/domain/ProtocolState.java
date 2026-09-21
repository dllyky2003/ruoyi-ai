package com.ruoyi.acls.tutor.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * ACLS 流程状态。表示学员在 ACLS 算法中的周期与关键节点。
 */
@Data
public class ProtocolState {
    private String currentAlgorithm;    // adult_cardiac_arrest
    private int cprCycleIndex;
    private boolean rhythmCheckDue;
    private long lastRhythmAnalysisAt;
    private int defibrillationCount;
    private List<String> medicationHistory = new ArrayList<>();
    private String airwayStrategy;      // basic / advanced / not_started
}

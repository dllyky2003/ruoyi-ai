package com.ruoyi.ems.agent.dto;

import lombok.Data;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;   // 修正导入

@Data
public class ActionRuntime {
    private String intentCode;
    private int currentStepIndex = -1;
    private Map<String, String> stepStatus = new ConcurrentHashMap<>();
    private Map<String, Object> stepResults = new ConcurrentHashMap<>();
    private List<String> stepIds = Arrays.asList(
        "prep_sedation", "set_sync", "select_energy", "charge", "safety_clear", "discharge", "reassess"
    );

    public boolean isStepDone(String stepId) {
        return "DONE".equals(stepStatus.get(stepId));
    }

    public void advance() {
        if (currentStepIndex < stepIds.size() - 1) {
            currentStepIndex++;
            stepStatus.put(stepIds.get(currentStepIndex), "DONE");
        }
    }

    public void abort(String stepId) {
        stepStatus.put(stepId, "ABORTED");
        int idx = stepIds.indexOf(stepId);
        if (idx >= 0) currentStepIndex = idx - 1;
    }

    // 修正：补全 getCurrentStepId()
    public String getCurrentStepId() {
        if (currentStepIndex >= 0 && currentStepIndex < stepIds.size()) {
            return stepIds.get(currentStepIndex);
        }
        return null;
    }
}

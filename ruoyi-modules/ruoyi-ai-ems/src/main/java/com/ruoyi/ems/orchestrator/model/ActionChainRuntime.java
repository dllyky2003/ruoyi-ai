package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作链运行时状态
 * 管理多步骤动作链的进度和状态
 */
@Data
public class ActionChainRuntime {
    private final String intentCode;
    private final List<String> stepIds;          // 顺序步骤ID列表
    private int currentStepIndex = -1;            // -1表示未开始
    private final Map<String, StepStatus> stepStatusMap; // 步骤ID → 状态
    private final Map<String, Object> stepResults;       // 步骤结果数据

    public ActionChainRuntime(String intentCode, List<String> stepIds) {
        this.intentCode = intentCode;
        this.stepIds = stepIds;
        this.stepStatusMap = new LinkedHashMap<>();
        this.stepResults = new LinkedHashMap<>();
        for (String stepId : stepIds) {
            stepStatusMap.put(stepId, StepStatus.PENDING);
        }
    }

    /**
     * 检查指定步骤是否已完成
     */
    public boolean isStepDone(String stepId) {
        return stepStatusMap.getOrDefault(stepId, StepStatus.PENDING) == StepStatus.DONE;
    }

    /**
     * 获取当前正在执行的步骤ID（如果当前步骤状态为IN_PROGRESS或DONE）
     */
    public String getCurrentStepId() {
        if (currentStepIndex >= 0 && currentStepIndex < stepIds.size()) {
            return stepIds.get(currentStepIndex);
        }
        return null;
    }

    /**
     * 获取当前步骤状态
     */
    public StepStatus getCurrentStepStatus() {
        String current = getCurrentStepId();
        return current != null ? stepStatusMap.get(current) : StepStatus.PENDING;
    }

    /**
     * 推进到下一步（将当前步骤标记为DONE，并将索引移至下一步）
     * 如果当前步骤尚未完成，会先标记为DONE
     */
    public void advance() {
        if (currentStepIndex < stepIds.size() - 1) {
            // 如果当前步骤存在且不是DONE，强制标记为DONE（通常advance前应已通过外部确认）
            if (currentStepIndex >= 0) {
                String currentId = stepIds.get(currentStepIndex);
                stepStatusMap.put(currentId, StepStatus.DONE);
            }
            currentStepIndex++;
            String nextId = stepIds.get(currentStepIndex);
            stepStatusMap.put(nextId, StepStatus.IN_PROGRESS);
        } else {
            // 已经是最后一步，标记为完成
            if (currentStepIndex >= 0) {
                String currentId = stepIds.get(currentStepIndex);
                stepStatusMap.put(currentId, StepStatus.DONE);
            }
        }
    }

    /**
     * 更新指定步骤状态（通常由异步回调调用）
     */
    public void updateStepStatus(String stepId, StepStatus status) {
        stepStatusMap.put(stepId, status);
    }

    /**
     * 检查所有步骤是否均为DONE
     */
    public boolean isAllStepsDone() {
        return stepStatusMap.values().stream().allMatch(s -> s == StepStatus.DONE);
    }

    /**
     * 重置所有步骤为PENDING
     */
    public void reset() {
        for (String stepId : stepIds) {
            stepStatusMap.put(stepId, StepStatus.PENDING);
        }
        currentStepIndex = -1;
        stepResults.clear();
    }

    /**
     * 获取步骤状态摘要（用于LLM上下文）
     */
    public Map<String, String> getStepStatusSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        for (String stepId : stepIds) {
            StepStatus status = stepStatusMap.get(stepId);
            summary.put(stepId, status != null ? status.name() : "PENDING");
        }
        return summary;
    }
}

package com.ruoyi.ems.agent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Plan {
    private List<Action> plan;
    private String finalMessage;
    /**
     * 将计划当前情况格式化为字符串输出
     * @return 格式化的计划状态描述
     */
    public String formatPlanStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Plan Status:\n");

        if (plan == null || plan.isEmpty()) {
            sb.append("  No actions in plan.\n");
        } else {
            sb.append("  Actions:\n");
            int index = 1;
            for (Action action : plan) {
                sb.append("    ").append(index++).append(". ");
                sb.append("op: ").append(action.getOp()).append(", ");
                sb.append(action.toFormateString());
            }
        }

        sb.append("Final Message: ")
            .append(finalMessage != null ? finalMessage : "(none)");

        return sb.toString();
    }
}

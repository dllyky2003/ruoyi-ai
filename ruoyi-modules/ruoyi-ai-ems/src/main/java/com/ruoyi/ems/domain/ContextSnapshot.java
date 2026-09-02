package com.ruoyi.ems.domain;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 上下文只读快照 - 用于 NLU 分类器
 *
 * 不可变对象，所有字段为 final 且无 setter。
 * 只包含分类器 Prompt 构建所需的最小信息集，
 * 不包含任何可修改的变量引用，防止分类器误操作。
 *
 * 由 SnapshotProvider 生成，IntentClassifier 消费。
 */
@Getter
@Builder
@AllArgsConstructor
@ToString
public class ContextSnapshot {

    /**
     * 当前状态机状态
     */
    private final String currentState;

    /**
     * 案例类型编码
     */
    private final String caseType;

    /**
     * 当前状态下可用的意图编码列表（白名单）
     * 用于约束 LLM 输出范围
     */
    @Builder.Default
    private final List<String> allowedIntents = new ArrayList<>();

    /**
     * 关键上下文变量（仅分类器推理必需的）
     * 如：adenosine_dose_count, current_rhythm, systolic_bp
     *
     * 注意：返回的是不可变视图，防止外部修改
     */
    @Builder.Default
    private final Map<String, Object> keyVariables = Collections.emptyMap();

    /**
     * 案例阶段描述（用于 Prompt 增强理解）
     * 如 "正在评估QRS宽度", "准备推注腺苷"
     */
    private final String phaseDescription;

    /**
     * 距离上次干预的秒数（用于判断是否超时）
     */
    private final Integer secondsSinceLastIntervention;

    /**
     * 获取当前状态的友好名称（用于 Prompt）
     * 可调用外部服务转换，或从配置读取
     */
    public String getCurrentStateDisplayName() {
        // 可通过 StateConstants 或 配置 转换
        return currentState != null ? currentState.replace("_", " ") : "未知";
    }

    /**
     * 检查是否允许某意图
     */
    public boolean isIntentAllowed(String intentCode) {
        return allowedIntents != null && allowedIntents.contains(intentCode);
    }

    /**
     * 获取关键变量的只读副本
     */
    public Map<String, Object> getKeyVariables() {
        return keyVariables != null ? Collections.unmodifiableMap(keyVariables) : Collections.emptyMap();
    }

    /**
     * 获取关键变量的字符串表示（供 Prompt 使用）
     */
    public String getKeyVariablesAsString() {
        if (keyVariables == null || keyVariables.isEmpty()) {
            return "（无关键变量）";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : keyVariables.entrySet()) {
            sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取可用意图的字符串表示（供 Prompt 使用）
     */
    public String getAllowedIntentsAsString() {
        if (allowedIntents == null || allowedIntents.isEmpty()) {
            return "（无可用意图，请选择全局意图）";
        }
        return String.join(", ", allowedIntents);
    }

    /**
     * 检查快照是否为空（无可用数据）
     */
    public boolean isEmpty() {
        return currentState == null && caseType == null;
    }

    /**
     * 构建一个空的快照（用于异常降级）
     */
    public static ContextSnapshot empty() {
        return ContextSnapshot.builder()
            .currentState("UNKNOWN")
            .caseType("UNKNOWN")
            .allowedIntents(Collections.emptyList())
            .keyVariables(Collections.emptyMap())
            .build();
    }
}

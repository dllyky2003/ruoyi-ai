package com.ruoyi.ems.context;

import com.ruoyi.ems.domain.IntentCommand;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 意图分类上下文
 * 用于向分类器提供当前场景信息，提升分类准确率
 */
@Data
@Builder
public class ClassificationContext {

    // ==================== 基础信息 ====================
    private String caseId;
    private String caseDisplayName;
    private String sessionId;
    private String currentState;
    private String currentStateDescription;
    private String sessionMode;
    private String traineeName;

    // ==================== 动态数据 ====================
    private Map<String, Object> variables;
    private String variablesDescription;
    private String availableIntentsDescription;

    @Singular("intentHistoryItem")
    private List<String> intentHistory;
    private String lastIntent;
    private IntentCommand lastIntentResult;
    private String scenario;

    // ==================== 状态约束 ====================
    private Set<String> availableIntents;
    private Set<String> globalEventIntents;

    // ==================== 时间信息 ====================
    private int elapsedSeconds;
    private String clockStatus;

    // ==================== 对话历史 ====================
    @Singular("dialogTurn")
    private List<DialogTurn> recentDialogs;

    // ==================== 扩展字段 ====================
    private Map<String, Object> extra;

    // ==================== 展示字段（用于LLM） ====================

    /**
     * 意图历史（中文名称列表，用于LLM展示）
     */
    private List<String> intentHistoryDisplay;

    /**
     * 上一个操作（中文名称）
     */
    private String lastIntentDisplay;

    /**
     * ★ 虚拟团队状态摘要（用于 LLM 上下文）
     * 例如："张伟（医生）：空闲；王芳（护士）：⏳ 推注腺苷 60%"
     */
    private String teamStatusSummary;

    // ==================== 静态工厂 ====================
    public static ClassificationContext empty() {
        return ClassificationContext.builder().build();
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取最近 N 轮对话
     */
    public List<DialogTurn> getRecentDialogs(int n) {
        if (recentDialogs == null || recentDialogs.isEmpty()) {
            return new ArrayList<>();
        }
        int size = recentDialogs.size();
        int start = Math.max(0, size - n);
        return new ArrayList<>(recentDialogs.subList(start, size));
    }

    /**
     * 获取最近一轮对话
     */
    public DialogTurn getLastDialog() {
        if (recentDialogs == null || recentDialogs.isEmpty()) {
            return null;
        }
        return recentDialogs.get(recentDialogs.size() - 1);
    }


    /**
     * 构建用于 LLM 用户提示词（User Prompt）的上下文字符串
     * <p>
     * 与 {@link #toDebugString()} 的区别：
     * - toDebugString() 面向开发者调试，含装饰符号（───、缩进等）
     * - buildContextPrompt() 面向 LLM，输出干净、结构化的键值对文本，适合直接拼接到提示词中
     *
     * @return 格式化的上下文字符串，若所有字段均为空则返回空字符串
     */
    public String buildContextPrompt() {
        StringBuilder sb = new StringBuilder();

        // 1. 案例类型（优先显示名称，其次编码）
        if (caseDisplayName != null && !caseDisplayName.isEmpty()) {
            sb.append("- 案例类型：").append(caseDisplayName).append("\n");
        } else if (caseId != null && !caseId.isEmpty()) {
            sb.append("- 案例类型：").append(caseId).append("\n");
        }

        // 2. 当前阶段（优先描述，其次状态码）
        if (currentStateDescription != null && !currentStateDescription.isEmpty()) {
            sb.append("- 当前阶段：").append(currentStateDescription).append("\n");
        } else if (currentState != null && !currentState.isEmpty()) {
            sb.append("- 当前阶段：").append(currentState).append("\n");
        }

        // 3. 动态变量描述（如心率、血压等，直接透传）
        if (variablesDescription != null && !variablesDescription.isEmpty()) {
            // 按行追加，保持原有格式（可能是多行）
            String trimmed = variablesDescription.trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed);
                if (!trimmed.endsWith("\n")) {
                    sb.append("\n");
                }
            }
        }

        // 4. 可用意图描述（直接透传）
        if (availableIntentsDescription != null && !availableIntentsDescription.isEmpty()) {
            String trimmed = availableIntentsDescription.trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed);
                if (!trimmed.endsWith("\n")) {
                    sb.append("\n");
                }
            }
        }

        // 5. 上一个操作（优先使用展示字段，其次使用编码）
        String lastDisplay = lastIntentDisplay;
        if (lastDisplay == null || lastDisplay.isEmpty()) {
            lastDisplay = lastIntent;
        }
        if (lastDisplay != null && !lastDisplay.isEmpty()) {
            sb.append("- 上一个操作：").append(lastDisplay).append("\n");
        }

        // 6. 意图历史 + 对话原文（优先使用 recentDialogs，保留对话原文对 LLM 更友好）
        List<DialogTurn> recent = getRecentDialogs(5);
        if (!recent.isEmpty()) {
            sb.append("- 意图历史（含对话原文）：\n");
            for (int i = 0; i < recent.size(); i++) {
                DialogTurn turn = recent.get(i);
                String userMsg = turn.getUserMessage();
                String intentName = getIntentDisplayName(turn.getIntentCode());
                sb.append("    ").append(i + 1).append(". 学员：\"").append(userMsg).append("\"");
                if (intentName != null && !intentName.isEmpty()) {
                    sb.append(" → ").append(intentName);
                }
                sb.append("\n");
            }
        } else if (intentHistoryDisplay != null && !intentHistoryDisplay.isEmpty()) {
            // 降级：使用预先转换好的中文名称列表
            sb.append("- 意图历史：").append(String.join(" → ", intentHistoryDisplay)).append("\n");
        } else if (intentHistory != null && !intentHistory.isEmpty()) {
            // 再降级：使用原始编码列表
            sb.append("- 意图历史：").append(String.join(" → ", intentHistory)).append("\n");
        }

        // 7. 累计时间
        sb.append("- 累计时间：").append(formatTime(elapsedSeconds)).append("\n");

        // 8. 时钟状态（如暂停、运行中）
        if (clockStatus != null && !clockStatus.isEmpty()) {
            sb.append("- 时钟状态：").append(clockStatus).append("\n");
        }

        // 9. 虚拟团队状态（新增字段，对 LLM 理解团队协作非常有帮助）
        if (teamStatusSummary != null && !teamStatusSummary.isEmpty()) {
            sb.append("- 团队状态：").append(teamStatusSummary).append("\n");
        }

        return sb.toString();
    }


    /**
     * 生成用于调试的格式化字符串（聚焦于 LLM 实际收到的上下文）
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClassificationContext {\n");

        // 核心上下文（LLM 实际看到的内容）
        sb.append("  ─── 给 LLM 的上下文 ───\n");

        // 1. 案例类型
        if (caseDisplayName != null && !caseDisplayName.isEmpty()) {
            sb.append("  案例类型：").append(caseDisplayName).append("\n");
        } else if (caseId != null && !caseId.isEmpty()) {
            sb.append("  案例类型：").append(caseId).append("\n");
        }

        // 2. 当前状态
        if (currentStateDescription != null && !currentStateDescription.isEmpty()) {
            sb.append("  当前阶段：").append(currentStateDescription).append("\n");
        } else if (currentState != null) {
            sb.append("  当前状态：").append(currentState).append("\n");
        }

        // 3. 变量描述
        if (variablesDescription != null && !variablesDescription.isEmpty()) {
            String[] lines = variablesDescription.split("\n");
            for (String line : lines) {
                sb.append("  ").append(line).append("\n");
            }
        }

        // 4. 可用意图描述
        if (availableIntentsDescription != null && !availableIntentsDescription.isEmpty()) {
            sb.append("  ").append(availableIntentsDescription).append("\n");
        }

        // 5. 上一个操作（使用展示字段）
        if (lastIntentDisplay != null && !lastIntentDisplay.isEmpty()) {
            sb.append("  上一个操作：").append(lastIntentDisplay).append("\n");
        } else if (lastIntent != null) {
            sb.append("  上一个操作：").append(lastIntent).append("\n");
        }

        // 6. ★ 意图历史 + 对话原文（合并展示，优先使用 recentDialogs）
        List<DialogTurn> recentDialogs = getRecentDialogs(5);
        if (!recentDialogs.isEmpty()) {
            sb.append("  意图历史（含对话原文）：\n");
            for (int i = 0; i < recentDialogs.size(); i++) {
                DialogTurn turn = recentDialogs.get(i);
                String displayName = getIntentDisplayName(turn.getIntentCode());
                sb.append("    ").append(i + 1).append(". 学员：\"").append(turn.getUserMessage()).append("\"");
                if (displayName != null) {
                    sb.append(" → ").append(displayName);
                }
                sb.append("\n");
            }
        } else if (intentHistoryDisplay != null && !intentHistoryDisplay.isEmpty()) {
            sb.append("  意图历史：").append(intentHistoryDisplay).append("\n");
        } else if (intentHistory != null && !intentHistory.isEmpty()) {
            sb.append("  意图历史：").append(intentHistory).append("\n");
        }

        // ★ 7. 团队状态
        if (teamStatusSummary != null && !teamStatusSummary.isEmpty()) {
            sb.append("  【虚拟团队状态】\n");
            String[] lines = teamStatusSummary.split("；");
            for (String line : lines) {
                sb.append("    ").append(line.trim()).append("\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 获取意图的显示名称（编码 + 中文名称）
     */
    private String getIntentDisplayName(String intentCode) {
        // 注意：此处简化，实际可注入 CaseIntentDefinitionsLoader
        return intentCode;
    }

    /**
     * 格式化时间
     */
    private String formatTime(int seconds) {
        if (seconds < 60) return seconds + "秒";
        int minutes = seconds / 60;
        int remain = seconds % 60;
        if (minutes < 60) return minutes + "分" + remain + "秒";
        int hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "小时" + minutes + "分" + remain + "秒";
    }

    /**
     * 格式化 Set，限制长度
     */
    private String formatSet(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        if (set.size() <= 15) {
            return set.toString();
        }
        List<String> list = new ArrayList<>(set);
        List<String> first = list.subList(0, 15);
        return first + "... (" + (set.size() - 15) + " more)";
    }

    // ==================== 内部类：对话轮次 ====================
    @Data
    @Builder
    public static class DialogTurn {
        private String userMessage;
        private String systemResponse;
        private String intentCode;
        private long timestamp;
    }
}

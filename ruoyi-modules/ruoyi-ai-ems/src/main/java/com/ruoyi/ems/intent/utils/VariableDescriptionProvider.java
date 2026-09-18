package com.ruoyi.ems.intent.utils;

import com.ruoyi.ems.config.loader.VariableMetaLoader;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.VariableMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
@Slf4j
@Component
public class VariableDescriptionProvider {

    @Autowired
    private VariableMetaLoader variableMetaManager;

    /**
     * 构建LLM可读的变量描述（分组格式）
     */
    public String buildDescription(String caseId, CaseContext context) {
        Map<String, VariableMeta> metaMap = variableMetaManager.getVariablesForCase(caseId);
        Map<String, Object> values = context.getVariablesSnapshot();

        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("【生命体征（客观事实）】", new ArrayList<>());
        groups.put("【学员评估（主观判断）】", new ArrayList<>());
        groups.put("【操作进度】", new ArrayList<>());
        groups.put("【患者特征】", new ArrayList<>());
        groups.put("【系统状态】", new ArrayList<>());

        for (Map.Entry<String, VariableMeta> entry : metaMap.entrySet()) {
            String name = entry.getKey();
            VariableMeta meta = entry.getValue();
            Object value = values.get(name);
            if (value == null) continue;
            if (isInternalVariable(name)) continue;

            try {
                String displayName = getDisplayName(meta);
                String displayValue = formatValue(value, meta);
                String group = getGroup(meta);
                groups.get(group).add(displayName + "：" + displayValue);
            } catch (Exception e) {
                log.warn("处理变量 {} 描述时出错: {}", name, e.getMessage());
                // 跳过该变量
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            sb.append(entry.getKey()).append("\n");
            for (String desc : entry.getValue()) {
                sb.append("  - ").append(desc).append("\n");
            }
        }

        sb.append("【系统时间】\n");
        sb.append("  - 累计时间：").append(formatTime(context.getElapsedSeconds())).append("\n");
        sb.append("  - 时钟状态：").append(context.isClockRunning() ? "▶ 运行中" : "⏸ 已暂停").append("\n");

        return sb.toString();
    }

    /**
     * 获取变量显示名：优先从 description 中提取核心名称（去除括号内容）
     */
    private String getDisplayName(VariableMeta meta) {
        String desc = meta.getDescription();
        if (desc != null && !desc.isEmpty()) {
            // 去除括号内容
            String cleaned = desc.replaceAll("[（(][^）)]*[）)]", "").trim();
            if (!cleaned.isEmpty()) {
                // 如果太长，截取逗号前的部分
                if (cleaned.length() > 20) {
                    String[] parts = cleaned.split("[，,、；;]");
                    return parts[0].trim();
                }
                return cleaned;
            }
        }
        // 兜底：使用变量名
        return meta.getName();
    }

    /**
     * 格式化变量值
     */
    private String formatValue(Object value, VariableMeta meta) {
        if (value == null) return "未设置";

        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? "是" : "否";
        }

        if (value instanceof String str) {
            // 优先使用枚举配置的标签
            if (meta.getEnumOptions() != null) {
                String label = meta.getEnumLabel(str);
                if (label != null) return label;
            }
            // 兜底返回原值
            return str;
        }

        if (value instanceof Number num) {
            String unit = meta.getUnit() != null ? meta.getUnit() : "";

            // 特殊处理：腺苷次数
            if ("adenosine_dose_count".equals(meta.getName())) {
                int count = num.intValue();
                if (count == 0) return "未推注";
                if (count == 1) return "6mg 已推";
                if (count == 2) return "12mg 已推";
                return count + "次";
            }
            // 特殊处理：电复律次数
            if ("cardioversion_attempts".equals(meta.getName())) {
                int count = num.intValue();
                return count == 0 ? "未尝试" : count + "次";
            }
            return num + unit;
        }

        return value.toString();
    }

    /**
     * 判断是否为内部变量（不展示给LLM）
     */
    private boolean isInternalVariable(String varName) {
        Set<String> internal = new HashSet<>(Arrays.asList(
            "case_type", "current_state", "session_start_time",
            "last_error", "time_elapsed_since_last_intervention",
            "session_clock_status"
        ));
        return internal.contains(varName);
    }

    /**
     * 根据变量类别返回分组名称
     */
    private String getGroup(VariableMeta meta) {
        String category = meta.getCategory();
        if (category == null) return "系统状态";
        switch (category) {
            case "PHYSIOLOGICAL": return "【生命体征（客观事实）】";
            case "LEARNER_STATEMENT": return "【学员评估（主观判断）】";
            case "PROCESS_MARKER": return "【操作进度】";
            case "CLINICAL": return "【患者特征】";
            default: return "【系统状态】";
        }
    }

    /**
     * 将秒数格式化为可读时间
     */
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        }
        int minutes = seconds / 60;
        int remainSeconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "分" + remainSeconds + "秒";
        }
        int hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "小时" + minutes + "分" + remainSeconds + "秒";
    }
}

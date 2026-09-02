package org.ruoyi.system.domain.vo.schemaInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 临床上下文信息（合并了 InitialState、Monitor、LabResult）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClinicalContextInfo {

    @JsonProperty("initialState")
    private InitialState initialState;

    @JsonProperty("monitors")
    private List<Monitor> monitors;

    @JsonProperty("labResults")
    private List<LabResult> labResults;

    /**
     * 生成用于 AI 重点查体（心肺血管）配置页面的精简背景信息
     * @return 结构化描述字符串，若无相关数据则返回空字符串
     */
    public String generateCardioPulmonaryContext() {
        StringBuilder sb = new StringBuilder();

        // ========== 1. 初始状态（呼吸、循环、心电图） ==========
        if (initialState != null) {
            if (StringUtils.hasText(initialState.getBreathing())) {
                sb.append("呼吸状态: ").append(initialState.getBreathing()).append("; ");
            }
            if (StringUtils.hasText(initialState.getCirculation())) {
                sb.append("循环状态: ").append(initialState.getCirculation()).append("; ");
            }
            if (StringUtils.hasText(initialState.getEcg())) {
                sb.append("心电图: ").append(initialState.getEcg()).append("; ");
            }
        }

        // ========== 2. 生命体征（心率、血氧、呼吸频率） ==========
        if (monitors != null) {
            for (Monitor monitor : monitors) {
                String key = monitor.getKey();
                String item = monitor.getItem();
                String identifier = (key != null) ? key : item;
                if (identifier == null) continue;

                String lowerId = identifier.toLowerCase();
                Double value = monitor.getValue();
                String unit = monitor.getUnit() != null ? monitor.getUnit() : "";
                String valueStr = (value != null) ? value + (unit.isEmpty() ? "" : " " + unit) : "";

                if (lowerId.contains("heart") || lowerId.contains("hr") || lowerId.contains("pulse")) {
                    sb.append("心率: ").append(valueStr).append("; ");
                } else if (lowerId.contains("spo2") || lowerId.contains("oxygen") || lowerId.contains("o2")) {
                    sb.append("血氧饱和度: ").append(valueStr).append("; ");
                } else if (lowerId.contains("resp") || lowerId.contains("rr")) {
                    sb.append("呼吸频率: ").append(valueStr).append("; ");
                }
                // 血压因字段类型限制暂不处理（可后续扩展）
            }
        }

        // ========== 3. 实验室检查（心肌标志物） ==========
        if (labResults != null) {
            for (LabResult lab : labResults) {
                String name = lab.getName();
                if (name == null) continue;
                String lowerName = name.toLowerCase();
                String valStr = (lab.getValue() != null) ? lab.getValue() + (lab.getUnit() != null ? " " + lab.getUnit() : "") : "";
                String flag = (lab.getFlag() != null && !lab.getFlag().isEmpty()) ? " (" + lab.getFlag() + ")" : "";

                if (lowerName.contains("troponin") || lowerName.contains("cni")) {
                    sb.append("肌钙蛋白: ").append(valStr).append(flag).append("; ");
                } else if (lowerName.contains("bnp") || lowerName.contains("probnp")) {
                    sb.append("BNP/NT-proBNP: ").append(valStr).append(flag).append("; ");
                }
            }
        }

        if (sb.length() == 0) {
            return "";
        }
        String result = sb.toString();
        if (result.endsWith("; ")) {
            result = result.substring(0, result.length() - 2);
        }
        return "【心肺血管重点信息】" + result;
    }

    // ==================== 内部静态类 ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitialState {
        @JsonProperty("consciousness")
        private String consciousness;

        @JsonProperty("airway")
        private String airway;

        @JsonProperty("breathing")
        private String breathing;

        @JsonProperty("circulation")
        private String circulation;

        @JsonProperty("ecg")
        private String ecg;

        @JsonProperty("appearance")
        private List<String> appearance;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Monitor {
        @JsonProperty("key")
        private String key;

        @JsonProperty("item")
        private String item;

        @JsonProperty("value")
        private Double value;

        @JsonProperty("unit")
        private String unit;

        @JsonProperty("hint")
        private String hint;

        @JsonProperty("locked")
        private Boolean locked;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LabResult {
        @JsonProperty("id")
        private String id;

        @JsonProperty("category")
        private String category;

        @JsonProperty("name")
        private String name;

        @JsonProperty("value")
        private Double value;

        @JsonProperty("unit")
        private String unit;

        @JsonProperty("referenceRange")
        private String referenceRange;

        @JsonProperty("flag")
        private String flag;

        @JsonProperty("visibility")
        private Boolean visibility;

        @JsonProperty("delaySeconds")
        private Integer delaySeconds;

        @JsonProperty("releaseCondition")
        private String releaseCondition;

        @JsonProperty("note")
        private String note;

        @JsonProperty("locked")
        private Boolean locked;
    }
}

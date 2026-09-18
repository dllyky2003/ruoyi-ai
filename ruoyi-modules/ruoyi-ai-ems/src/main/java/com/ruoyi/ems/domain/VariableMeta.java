package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariableMeta {

    private String name;

    @JsonProperty("name_cn")
    private String nameCn = "";

    private String type;

    /**
     * 枚举选项列表（新格式，包含 value 和 label）
     * 对应 JSON 中的 "enum": [{"value": "...", "label": "..."}, ...]
     */
    @JsonProperty("enum")
    private List<EnumOption> enumOptions;

    private Integer min;
    private Integer max;
    private String unit;
    private String description;
    private String category;
    private String source;

    @JsonProperty("writable_by")
    private String writableBy;

    @JsonProperty("default_value")
    private Object defaultValue;

    private Boolean persistence;
    private Boolean scoring;

    @JsonProperty("scoring_rules")
    private ScoringRules scoringRules;

    private List<String> applicableTo;
    private Map<String, Object> items;

    /** 通用变量：专属变量 */
    private String variableType;
    /**
     * object 类型变量的必需键集合。用于 object 类型的写入校验。
     * 非 object 类型忽略此字段。
     */
    private List<String> requiredKeys;
    /**
     * 布尔/特殊值的可读标签
     * 对应 JSON: "value_labels": { "true": "可触及", "false": "未触及", "unknown": "待判读" }
     */
    @JsonProperty("value_labels")
    private Map<String, String> valueLabels;

    /**
     * 响应模板覆盖（可选）
     * 对应 JSON: "value_template": "{name_cn}：{value} {unit}"
     */
    @JsonProperty("value_template")
    private String valueTemplate;

    /**
     * 根据枚举值获取对应的显示标签。
     *
     * <p>使用 {@link Objects#equals} 兼容 {@code opt.getValue() == null} 的脏数据
     * （部分变量的 enum 列表里存在 {@code { "value": null, "label": null }} 项）。
     *
     * @param value 枚举值
     * @return 显示标签，未匹配返回 {@code null}
     */
    public String getEnumLabel(String value) {
        if (enumOptions != null) {
            for (EnumOption opt : enumOptions) {
                if (Objects.equals(opt.getValue(), value)) {
                    return opt.getLabel();
                }
            }
        }
        return null;
    }

    /**
     * 统一入口：根据变量元数据自动生成"可读文本"。
     *
     * <p>优先级（从高到低）：
     * <ol>
     *   <li>自定义 {@link #valueTemplate}（若配置）</li>
     *   <li>{@code enum} 类型 → 查 {@code enum[].label}</li>
     *   <li>{@code boolean} 类型 → 查 {@link #valueLabels}</li>
     *   <li>{@code null} / {@code unknown} → "待判读"</li>
     *   <li>数值/其他 → {@code name_cn：value unit}</li>
     * </ol>
     *
     * @param rawValue 原始值（可为 null）
     * @return 完整可读文本，如 "当前心律：室上性心动过速"
     */
    public String renderReadable(Object rawValue) {
        String valueStr = (rawValue != null) ? String.valueOf(rawValue) : "unknown";

        // 显示名：优先 name_cn → name → "变量"
        String displayName = StringUtils.hasText(nameCn) ? nameCn
            : StringUtils.hasText(name)   ? name
            : "变量";

        // 1. 自定义模板
        if (StringUtils.hasText(valueTemplate)) {
            String unitStr = (unit != null && !unit.isEmpty()) ? unit : "";
            String result = valueTemplate
                .replace("{name_cn}", displayName)
                .replace("{value}", valueStr)
                .replace("{unit}", unitStr);
            // 去掉因空 unit 造成的尾部空格；压缩多空格
            return result.replaceAll("\\s+$", "").replaceAll("\\s{2,}", " ");
        }

        // 2. enum 类型
        if (enumOptions != null && !enumOptions.isEmpty()) {
            String label = getEnumLabel(valueStr);
            if (label != null) {
                return displayName + "：" + label;
            }
        }

        // 3. boolean 类型
        if ("boolean".equalsIgnoreCase(type) && valueLabels != null) {
            String label = valueLabels.get(valueStr);
            if (label != null) {
                return displayName + "：" + label;
            }
            return displayName + "：" + ("true".equals(valueStr) ? "是" : "否");
        }

        // 4. 通用：null / unknown → 中文兜底
        if ("unknown".equals(valueStr) || "null".equals(valueStr)) {
            return displayName + "：待判读";
        }

        // 5. 数值/其他：name_cn + value + unit
        String unitSuffix = (unit != null && !unit.isEmpty()) ? " " + unit : "";
        return displayName + "：" + valueStr + unitSuffix;
    }

    // ====================================================================
    // 嵌套类
    // ====================================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnumOption {
        private String value;
        private String label;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoringRules {
        @JsonProperty("compare_with")
        private String compareWith;

        private Integer tolerance;
        private Map<String, Object> mapping;
    }
}

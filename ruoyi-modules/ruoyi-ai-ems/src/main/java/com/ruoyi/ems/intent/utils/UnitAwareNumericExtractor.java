package com.ruoyi.ems.intent.utils;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单位感知数值提取器
 * 从原始文本中提取数值，并拼接指定单位，同时支持 ASR 异常值回退。
 */
@Component("numericExtractor")
public class UnitAwareNumericExtractor {

    /**
     * 从上下文中提取数值，若提取失败或数值超出最大允许值，则返回默认值。
     *
     * @param ctx         抽取上下文（含 rawText）
     * @param unit        单位字符串（如 "ml"、"J"、"mg"）
     * @param defaultVal  默认值（如 "20ml"）
     * @param maxVal      最大允许数值（超过则视为 ASR 错误）
     * @return 拼接了单位的字符串，如 "20ml"
     */
    public String extractWithDefault(ExtractionContext ctx, String unit, String defaultVal, int maxVal) {
        String raw = ctx.getRawText();
        if (raw == null || raw.isEmpty()) {
            return defaultVal;
        }

        // 提取数字（支持整数和小数）
        Pattern p = Pattern.compile("(\\d+(\\.\\d+)?)");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            try {
                double numeric = Double.parseDouble(m.group(1));
                // 如果数值超出最大允许值，视为 ASR 错误，回退默认值
                if (numeric > maxVal) {
                    return defaultVal;
                }
                // 如果数值是整数，去掉 .0 显示
                String numStr;
                if (numeric == Math.floor(numeric)) {
                    numStr = String.valueOf((int) numeric);
                } else {
                    numStr = String.valueOf(numeric);
                }
                return numStr + unit;
            } catch (NumberFormatException e) {
                // 提取失败，回退
                return defaultVal;
            }
        }
        return defaultVal;
    }
}

package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ThresholdParser {

    /**
     * 解析 QRS 宽度判读
     */
    public Map<String, Object> parseQrsWidth(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        // 尝试提取数值
        Pattern p = Pattern.compile("(\\d+\\.\\d+)");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            double val = Double.parseDouble(m.group(1));
            result.put("assertedQrsWidth", val > 0.12 ? "wide" : "narrow");
        } else if (raw.contains("窄") || raw.contains("<0.12") || raw.contains("不宽")) {
            result.put("assertedQrsWidth", "narrow");
        } else if (raw.contains("宽") || raw.contains(">0.12") || raw.contains("超过")) {
            result.put("assertedQrsWidth", "wide");
        } else if (raw.contains("不是窄") || raw.contains("不窄")) {
            result.put("assertedQrsWidth", "wide");
        }
        return result;
    }
}

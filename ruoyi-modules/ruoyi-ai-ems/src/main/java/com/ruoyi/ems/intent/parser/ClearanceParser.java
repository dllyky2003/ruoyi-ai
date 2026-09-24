package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 清场类参数解析器：所有人离开患者、我接触、clear。
 * 供 S-102-2-5 意图的 processor 调用。
 */
@Component
public class ClearanceParser {

    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw == null) {
            result.put("allClearForShock", null);
            return result;
        }
        // 清场口令
        if (raw.contains("所有人离开") || raw.contains("所有人让开")
            || raw.contains("都离开") || raw.contains("clear")
            || raw.contains("我接触") || raw.contains("离开患者")) {
            result.put("allClearForShock", true);
        } else if (raw.contains("还没") || raw.contains("未清场")) {
            result.put("allClearForShock", false);
        } else {
            result.put("allClearForShock", null);
        }
        return result;
    }
}

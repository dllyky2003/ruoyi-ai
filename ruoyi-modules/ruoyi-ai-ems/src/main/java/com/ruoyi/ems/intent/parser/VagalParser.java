package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class VagalParser {
    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        result.put("procedure", "vagal");
        String raw = ctx.getRawText();
        if (raw.contains("完成") || raw.contains("好了")) result.put("operation_status", "done");
        else if (raw.contains("准备") || raw.contains("开始")) result.put("operation_status", "start");
        else result.put("operation_status", "pending");
        return result;
    }
}

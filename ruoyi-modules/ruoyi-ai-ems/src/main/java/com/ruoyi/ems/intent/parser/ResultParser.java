package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class ResultParser {
    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("转复") || raw.contains("成功") || raw.contains("恢复")) {
            result.put("vagalEffectObserved", "success");
        } else if (raw.contains("未转复") || raw.contains("无效") || raw.contains("失败")) {
            result.put("vagalEffectObserved", "failure");
        } else {
            result.put("vagalEffectObserved", "failure");
        }
        // 心律类型
        if (raw.contains("窦性")) result.put("rhythmConfirmed", "sinus");
        else if (raw.contains("房颤")) result.put("rhythmConfirmed", "afib");
        else if (raw.contains("房扑")) result.put("rhythmConfirmed", "aflutter");
        else if (raw.contains("室上速")) result.put("rhythmConfirmed", "svt");
        else if (raw.contains("室速")) result.put("rhythmConfirmed", "vt");
        else result.put("rhythmConfirmed", "unknown");
        return result;
    }
}

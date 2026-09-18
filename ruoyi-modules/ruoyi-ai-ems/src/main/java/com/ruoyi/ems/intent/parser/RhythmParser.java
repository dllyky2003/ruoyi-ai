package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class RhythmParser {

    public Map<String, Object> parseRegularity(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("规则") || raw.contains("齐")) {
            result.put("assertedRhythmRegular", true);
        } else if (raw.contains("不规则") || raw.contains("不齐") || raw.contains("乱的") || raw.contains("房颤")) {
            result.put("assertedRhythmRegular", false);
        } else {
            result.put("assertedRhythmRegular", null);
        }
        return result;
    }

    public Map<String, Object> parseGuess(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("房颤")) result.put("suspectedRhythm", "afib");
        else if (raw.contains("房扑")) result.put("suspectedRhythm", "aflutter");
        else if (raw.contains("室上速") || raw.contains("室上性")) result.put("suspectedRhythm", "svt");
        else if (raw.contains("室速") || raw.contains("室性")) result.put("suspectedRhythm", "vt");
        else result.put("suspectedRhythm", "unknown");
        return result;
    }
}

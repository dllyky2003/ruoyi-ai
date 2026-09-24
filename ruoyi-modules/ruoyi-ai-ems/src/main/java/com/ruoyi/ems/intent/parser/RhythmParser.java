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

    /**
     * 复律后心律：窦性/仍为室上速/未转复。
     * 供 S-102-2-7 意图的 processor 调用。
     */
    public Map<String, Object> parsePostShock(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw == null) {
            result.put("rhythmPostCardioversion", null);
            return result;
        }

        if (raw.contains("窦性") || raw.contains("转复") || raw.contains("恢复窦性")) {
            result.put("rhythmPostCardioversion", "sinus");
        } else if (raw.contains("未转复") || raw.contains("仍为") || raw.contains("还是")) {
            result.put("rhythmPostCardioversion", "unchanged");
        } else {
            result.put("rhythmPostCardioversion", null);
        }
        return result;
    }
}

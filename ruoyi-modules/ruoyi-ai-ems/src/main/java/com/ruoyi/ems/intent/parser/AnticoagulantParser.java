package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class AnticoagulantParser {
    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("肝素")) result.put("anticoagulant", "heparin");
        else if (raw.contains("华法林")) result.put("anticoagulant", "warfarin");
        else if (raw.contains("新型口服") || raw.contains("NOAC") || raw.contains("达比加群")) result.put("anticoagulant", "NOAC");
        else if (raw.contains("不") || raw.contains("无需")) result.put("anticoagulant", "none");
        else result.put("anticoagulant", "none");
        // 决策
        if (raw.contains("准备") || raw.contains("开始")) result.put("decision", "start");
        else if (raw.contains("暂缓") || raw.contains("等待")) result.put("decision", "defer");
        else if (raw.contains("禁忌") || raw.contains("不能用")) result.put("decision", "contraindicated");
        else result.put("decision", "start");
        return result;
    }
}

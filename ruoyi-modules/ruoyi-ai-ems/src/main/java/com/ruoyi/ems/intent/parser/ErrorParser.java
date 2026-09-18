package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ErrorParser {
    public Map<String, Object> parseDrugInduced(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("室颤")) result.put("eventType", "VF");
        else if (raw.contains("无脉搏室速") || raw.contains("室速无脉")) result.put("eventType", "pulseless_VT");
        else if (raw.contains("宽QRS") || raw.contains("宽QRS室速")) result.put("eventType", "wide_complex_tachycardia");
        else result.put("eventType", "wide_complex_tachycardia");
        // 触发药物
        Map<String, String> drugMap = Map.of("腺苷", "adenosine", "地尔硫卓", "diltiazem", "维拉帕米", "verapamil", "美托洛尔", "metoprolol", "地高辛", "digoxin");
        String trigger = null;
        for (Map.Entry<String, String> entry : drugMap.entrySet()) {
            if (raw.contains(entry.getKey())) {
                trigger = entry.getValue();
                break;
            }
        }
        result.put("triggerDrug", trigger != null ? trigger : "unknown");
        return result;
    }

    public Map<String, Object> parseShockInduced(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("室颤")) result.put("eventType", "VF");
        else if (raw.contains("尖端扭转") || raw.contains("Torsades")) result.put("eventType", "torsades");
        else result.put("eventType", "VF");
        // 提取能量
        Pattern p = Pattern.compile("(\\d+)\\s*[J焦]");
        Matcher m = p.matcher(raw);
        result.put("energy", m.find() ? m.group(1) + "J" : null);
        return result;
    }

    public Map<String, Object> parsePEA(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("PEA") || raw.contains("无脉电活动")) result.put("eventType", "PEA");
        else if (raw.contains("停搏") || raw.contains("直线")) result.put("eventType", "asystole");
        else if (raw.contains("测不到") || raw.contains("严重低血压")) result.put("eventType", "profound_hypotension");
        else result.put("eventType", "PEA");
        return result;
    }
}

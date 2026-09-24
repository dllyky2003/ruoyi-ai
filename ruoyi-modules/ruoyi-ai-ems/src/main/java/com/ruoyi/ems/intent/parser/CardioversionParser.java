package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CardioversionParser {
    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        result.put("procedure", "cardioversion");
        String raw = ctx.getRawText();
        // 提取能量
        Pattern p = Pattern.compile("(\\d+)\\s*[J焦]");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            int energy = Integer.parseInt(m.group(1));
            if (energy > 200) {
                result.put("energy", null); // ASR错误
            } else {
                result.put("energy", energy + "J");
            }
        } else {
            result.put("energy", null);
        }
        // 状态
        if (raw.contains("充电")) result.put("operation_status", "charging");
        else if (raw.contains("准备") || raw.contains("给镇静")) result.put("operation_status", "prep");
        else if (raw.contains("放电") || raw.contains("电击")) result.put("operation_status", "done");
        else result.put("operation_status", "prep");
        // 镇静
        if (raw.contains("咪达唑仑")) result.put("sedation", "midazolam");
        else if (raw.contains("依托咪酯")) result.put("sedation", "etomidate");
        else if (raw.contains("芬太尼")) result.put("sedation", "fentanyl");
        else result.put("sedation", "none");
        return result;
    }

    public Map<String, Object> parseEmergency(ExtractionContext ctx) {
        Map<String, Object> result = parse(ctx);
        result.put("procedure", "cardioversion_emergency");
        return result;
    }

    /**
     * 放电：提取电复律次数、紧急电复律标记。
     * 供 S-102-2-6 意图的 processor 调用。
     */
    public Map<String, Object> parseDischarge(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw == null) {
            result.put("cardioversionCount", null);
            result.put("emergencyCardioversionPerformed", null);
            return result;
        }

        // 放电动作
        if (raw.contains("放电") || raw.contains("电击") || raw.contains("shock")) {
            result.put("cardioversionCount", 1);           // 单次放电
            result.put("emergencyCardioversionPerformed", true);
        } else {
            result.put("cardioversionCount", null);
            result.put("emergencyCardioversionPerformed", null);
        }

        // 二次放电："再放一次"/"第二次"
        if (raw.contains("再放") || raw.contains("第二次") || raw.contains("再次")) {
            result.put("cardioversionCount", 2);
        }
        return result;
    }
}

package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class ErrorInterceptorParser {
    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw.contains("按压") || raw.contains("CPR") || raw.contains("心肺复苏")) {
            result.put("forbiddenAction", "cpr");
        } else if (raw.contains("非同步") || (raw.contains("除颤") && !raw.contains("同步"))) {
            result.put("forbiddenAction", "unsynchronized_shock");
        } else if (raw.contains("肾上腺素") || raw.contains("阿托品") || raw.contains("利多卡因")) {
            result.put("forbiddenAction", "wrong_drug");
            if (raw.contains("肾上腺素")) result.put("specificDrug", "epinephrine");
            else if (raw.contains("阿托品")) result.put("specificDrug", "atropine");
            else if (raw.contains("利多卡因")) result.put("specificDrug", "lidocaine");
        } else {
            result.put("forbiddenAction", "unsynchronized_shock"); // 默认
        }
        return result;
    }
}

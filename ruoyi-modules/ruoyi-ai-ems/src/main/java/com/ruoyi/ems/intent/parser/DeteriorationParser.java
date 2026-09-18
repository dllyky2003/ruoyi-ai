package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeteriorationParser {
    private static final Map<String, String> SYMPTOM_MAP = Map.of(
        "胸痛", "chest_pain",
        "气紧", "shortness_of_breath",
        "意识模糊", "altered_consciousness",
        "意识不清", "altered_consciousness",
        "肺水肿", "pulmonary_edema",
        "湿啰音", "pulmonary_edema",
        "休克", "shock",
        "湿冷", "shock"
    );

    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        List<String> symptoms = new ArrayList<>();
        for (Map.Entry<String, String> entry : SYMPTOM_MAP.entrySet()) {
            if (raw.contains(entry.getKey())) {
                symptoms.add(entry.getValue());
            }
        }
        result.put("symptoms", symptoms);
        result.put("assertedStable", false); // 强制不稳定
        // 提取血压
        Pattern p = Pattern.compile("(\\d{2,3})\\s*[/\\-]\\s*(\\d{2,3})");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            int sbp = Integer.parseInt(m.group(1));
            if (sbp >= 30 && sbp <= 200) {
                result.put("reportedSystolicBp", sbp);
            } else {
                result.put("reportedSystolicBp", null);
            }
        } else {
            result.put("reportedSystolicBp", null);
        }
        return result;
    }
}

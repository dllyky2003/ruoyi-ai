package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SymptomParser {

    private static final Set<String> NEGATION_WORDS = new HashSet<>(Arrays.asList("没有", "无", "未", "不", "否认"));

    /**
     * 提取症状列表并推断稳定性
     */
    public Map<String, Object> extractSymptoms(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        List<String> symptoms = new ArrayList<>();

        // 简化的症状关键词映射
        Map<String, String> symptomMap = Map.of(
            "胸痛", "chest_pain",
            "气紧", "shortness_of_breath",
            "意识不清", "altered_consciousness",
            "肺水肿", "pulmonary_edema",
            "休克", "shock"
        );

        for (Map.Entry<String, String> entry : symptomMap.entrySet()) {
            String keyword = entry.getKey();
            String symptomCode = entry.getValue();
            // 检查否定词
            boolean negated = false;
            for (String neg : NEGATION_WORDS) {
                if (raw.contains(neg + keyword)) {
                    negated = true;
                    break;
                }
            }
            if (!negated && raw.contains(keyword)) {
                symptoms.add(symptomCode);
            }
        }

        result.put("symptoms", symptoms);
        // 稳定性：无任何阳性症状则为稳定
        result.put("assertedStable", symptoms.isEmpty());
        return result;
    }
}

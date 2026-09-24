package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 镇静类参数解析器：咪达唑仑 / 依托咪酯 / 芬太尼 + 剂量。
 * 供 S-102-2-3 意图的 processor 调用。
 *
 * <p>返回扁平短名结构，直接对应 {@code _intents.json} 的 outputs 映射：
 * <pre>
 *   outputs: {
 *     "sedationGiven": "intervention.sedation.given",
 *     "sedationDrug":  "intervention.sedation.drug",
 *     "sedationDose":  "intervention.sedation.dose"
 *   }
 * </pre>
 *
 * <p>返回示例：
 * <ul>
 *   <li>给咪达唑仑 2mg → {@code {sedationGiven=true, sedationDrug=midazolam, sedationDose=2mg}}</li>
 *   <li>无药物识别   → {@code {sedationGiven=false}}</li>
 * </ul>
 */
@Component
public class SedationParser {

    private static final Pattern DOSE_PATTERN =
        Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(mg|毫克|ml|毫升|ug|微克)?");

    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();

        String raw = ctx != null ? ctx.getRawText() : null;
        if (raw == null || raw.isBlank()) {
            result.put("sedationGiven", false);
            return result;
        }

        // 1. 判定药物
        String drug = null;
        if (raw.contains("咪达唑仑") || raw.contains("咪唑安定")) {
            drug = "midazolam";
        } else if (raw.contains("依托咪酯")) {
            drug = "etomidate";
        } else if (raw.contains("芬太尼")) {
            drug = "fentanyl";
        } else if (raw.contains("氯胺酮")) {
            drug = "ketamine";
        }

        // 2. 剂量抽取（仅在有药物识别时才有意义）
        String dose = null;
        if (drug != null) {
            Matcher m = DOSE_PATTERN.matcher(raw);
            if (m.find()) {
                String num = m.group(1);
                String unit = m.group(2) != null ? m.group(2) : "mg";
                dose = num + unit;
            }
        }

        // 3. 扁平输出
        if (drug != null) {
            result.put("sedationGiven", true);
            result.put("sedationAssessed", true);
            result.put("sedationDrug", drug);
            if (dose != null) {
                result.put("sedationDose", dose);
            }
        } else {
            result.put("sedationGiven", false);
        }

        return result;
    }
}

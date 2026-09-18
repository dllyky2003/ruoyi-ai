package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 否定词感知症状解析器。
 *
 * <p>职责单一：<b>只做症状抽取</b>。</p>
 *
 * <p>支持：</p>
 * <ul>
 *   <li>否定词作用域识别（"没有胸痛" → 不提取 chest_pain）</li>
 *   <li>症状关键词映射（中文 → 标准编码）</li>
 *   <li>输出 {@code assert.stable}（客观推导的稳定性）</li>
 *   <li>输出 {@code process.hemodynamicsAssessed}（评估动作标记）</li>
 *   <li>输出 {@code symptoms}（原始症状列表，供审计）</li>
 * </ul>
 *
 * <p><b>不负责：</b>句类识别（疑问/陈述/祈使）。该职责由分类层的
 * {@code actType}（PROBE / ANNOUNCEMENT / COMMAND）承担，
 * 抽取层通过 {@code ParameterExtractor} 的 PROBE 过滤实现"疑问句不写断言"。</p>
 *
 * <p>输出键名与 {@code _variables.json} 对齐：</p>
 * <ul>
 *   <li>{@code assert.stable} —— 稳定/不稳定判断（boolean）</li>
 *   <li>{@code process.hemodynamicsAssessed} —— 是否发起评估（boolean，总是 true）</li>
 *   <li>{@code symptoms} —— 症状列表（List&lt;String&gt;，原始编码，供审计）</li>
 * </ul>
 */
@Slf4j
@Component
public class NegationAwareSymptomParser {

    // 否定词集合（可扩展）
    private static final Set<String> NEGATION_WORDS = new HashSet<>(Arrays.asList(
        "没有", "无", "未", "不", "否认", "未曾", "未出现"
    ));

    // 症状关键词映射（中文 → 标准编码）
    private static final Map<String, String> SYMPTOM_MAP = new LinkedHashMap<>();
    static {
        SYMPTOM_MAP.put("胸痛", "chest_pain");
        SYMPTOM_MAP.put("胸闷", "chest_pain");
        SYMPTOM_MAP.put("气紧", "shortness_of_breath");
        SYMPTOM_MAP.put("呼吸困难", "shortness_of_breath");
        SYMPTOM_MAP.put("喘不上气", "shortness_of_breath");
        SYMPTOM_MAP.put("意识不清", "altered_consciousness");
        SYMPTOM_MAP.put("意识模糊", "altered_consciousness");
        SYMPTOM_MAP.put("肺水肿", "pulmonary_edema");
        SYMPTOM_MAP.put("湿啰音", "pulmonary_edema");
        SYMPTOM_MAP.put("休克", "shock");
        SYMPTOM_MAP.put("湿冷", "shock");
        SYMPTOM_MAP.put("末梢循环差", "shock");
    }

    /**
     * 从原始文本中抽取症状，并推导稳定性。
     *
     * <p>输出键名与 {@code _variables.json} 对齐，方便 {@code ParameterExtractor}
     * 在 PROBE 意图时按前缀过滤。</p>
     *
     * @param ctx 抽取上下文（含 rawText）
     * @return Map 包含：
     *         <ul>
     *           <li>{@code process.hemodynamicsAssessed} = true（总是）</li>
     *           <li>{@code assert.stable} = 是否稳定（boolean）</li>
     *           <li>{@code symptoms} = 症状列表（List&lt;String&gt;，仅在有症状时输出）</li>
     *         </ul>
     */
    public Map<String, Object> extractSymptoms(ExtractionContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        String raw = ctx.getRawText();

        // 1. 标记"发起评估"（无论是否有症状词，这句话都是在评估稳定性）
        result.put("process.hemodynamicsAssessed", true);

        if (raw == null || raw.isEmpty()) {
            result.put("assert.stable", true);
            return result;
        }

        // 2. 抽取正向症状（否定词作用域内的不算）
        List<String> positiveSymptoms = new ArrayList<>();
        for (Map.Entry<String, String> entry : SYMPTOM_MAP.entrySet()) {
            String keyword = entry.getKey();
            String symptomCode = entry.getValue();

            if (raw.contains(keyword)) {
                boolean negated = isNegated(raw, keyword);
                if (!negated) {
                    positiveSymptoms.add(symptomCode);
                    log.debug("检测到症状: {} (关键词: {})", symptomCode, keyword);
                } else {
                    log.debug("检测到否定词，忽略症状: {} (关键词: {})", symptomCode, keyword);
                }
            }
        }

        List<String> uniqueSymptoms = new ArrayList<>(new LinkedHashSet<>(positiveSymptoms));

        // 3. 推导稳定性
        //    有明确症状 → 不稳定；无症状词 → 稳定
        //    （"疑问句不该写断言"的责任由 ParameterExtractor 的 PROBE 过滤承担）
        boolean stable = uniqueSymptoms.isEmpty();
        result.put("assert.stable", stable);

        // 4. 症状列表（仅在有症状时输出，避免污染 Context）
        if (!uniqueSymptoms.isEmpty()) {
            result.put("process.symptoms", uniqueSymptoms);
        }

        log.info("症状提取结果: symptoms={}, stable={}, assessed=true",
            uniqueSymptoms, stable);
        return result;
    }

    /**
     * 判断指定关键词是否被否定词修饰。
     *
     * <p>规则：否定词 + 最多 4 个任意字符 + 关键词。</p>
     */
    private boolean isNegated(String text, String keyword) {
        String negPattern = NEGATION_WORDS.stream()
            .map(Pattern::quote)
            .reduce((a, b) -> a + "|" + b)
            .orElse("");
        if (negPattern.isEmpty()) {
            return false;
        }

        Pattern pattern = Pattern.compile(
            "(" + negPattern + ")\\s*.{0,4}" + Pattern.quote(keyword));
        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }
}

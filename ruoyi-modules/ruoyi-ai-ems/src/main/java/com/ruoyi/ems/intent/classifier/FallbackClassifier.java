package com.ruoyi.ems.intent.classifier;

import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 正则兜底分类器：基于标准话术（standard_script）进行关键词匹配
 * 当LLM分类失败或置信度低时使用
 *
 * 修改说明：匹配源从 examples 改为 standard_script，使匹配更贴近标准话术。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackClassifier implements IntentClassifier {

    private final CaseIntentDefinitionsLoader registry;


    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {

        return classify(asrText);
    }

    @Override
    public IntentCommand classify(String asrText) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.invalid(asrText, "语音文本为空");
        }

        String text = asrText.trim();

        // 1. 根据语气词确定 act_type 过滤值
        String actFilter = determineActType(text);
        log.debug("Fallback actFilter: {}", actFilter);

        IntentDefinition bestMatch = null;
        int bestScore = 0;

        // 2. 仅遍历与 actFilter 匹配的意图
        for (IntentDefinition def : registry.getAllIntentList()) {
            String defActType = def.getActType();
            if (defActType == null || !defActType.equals(actFilter)) {
                continue;
            }
            int score = calculateMatchScore(text, def);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = def;
            }
        }

        // 3. 如果过滤后没有匹配，回退到全量匹配（兜底）
        if (bestMatch == null || bestScore == 0) {
            log.warn("Fallback 在 act_type={} 下无匹配，尝试全量匹配", actFilter);
            for (IntentDefinition def : registry.getAllIntentList()) {
                int score = calculateMatchScore(text, def);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = def;
                }
            }
        }

        // 4. 返回结果
        if (bestMatch != null && bestScore > 0) {
            double confidence = Math.min(0.7, 0.3 + (bestScore / 10.0) * 0.4);
            log.debug("Fallback 匹配: code={}, score={}, confidence={}", bestMatch.getIntentCode(), bestScore, confidence);

            IntentCandidate candidate = IntentCandidate.builder()
                .intentCode(bestMatch.getIntentCode())
                .confidence(confidence)
                .score((double) bestScore)
                .reason("Fallback regex match based on standard_script, score=" + bestScore)
                .build();
            List<IntentCandidate> candidates = Collections.singletonList(candidate);

            return IntentCommand.builder()
                .intentCode(bestMatch.getIntentCode())
                .confidence(confidence)
                .candidates(candidates)
                .valid(true)
                .classifyMode("FALLBACK")
                .slots(Collections.emptyMap())
                .rawText(asrText)
                .build();
        } else {
            log.warn("Fallback 无匹配: {}", text);
            return IntentCommand.invalid(asrText, "无匹配意图");
        }
    }

    /**
     * 计算文本与意图定义中 standard_script 的匹配得分
     */
    private int calculateMatchScore(String text, IntentDefinition def) {
        String script = def.getStandardScript();
        if (script == null || script.isEmpty()) {
            return 0;
        }
        // 去除标点和占位符（如 [NUM]），分割为关键词
        String cleaned = script.replaceAll("[，。！？、；：”“！\\[\\]NUM]", " ");
        String[] keywords = cleaned.split("\\s+");
        int score = 0;
        for (String keyword : keywords) {
            if (keyword.length() >= 2 && text.contains(keyword)) {
                score += 2;
            }
        }
        // 完整短语匹配（去除标点和占位符后的核心字符串）
        String core = script.replaceAll("[，。！？、；：”“！\\[\\]NUM]", "").trim();
        if (core.length() >= 4 && text.contains(core)) {
            score += 5;
        }
        return score;
    }

    /**
     * 根据文本内容推断 act_type
     * 规则：
     * - 含疑问词（吗、多少、什么、几、是否） → "询问"
     * - 含“了”且不含“准备” → "宣告"（避免“准备...了”被误判）
     * - 其余 → "指令"
     */
    private String determineActType(String text) {
        if (text.contains("吗") || text.contains("多少") || text.contains("什么")
            || text.contains("几") || text.contains("是否")) {
            return "询问";
        }
        if (text.contains("了") && !text.contains("准备")) {
            return "宣告";
        }
        return "指令";
    }

    @Override
    public String getMode() {
        return "FALLBACK";
    }
}

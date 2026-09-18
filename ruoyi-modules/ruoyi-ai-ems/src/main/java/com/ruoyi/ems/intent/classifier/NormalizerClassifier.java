package com.ruoyi.ems.intent.classifier;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.dict.IntentNormalizer;
import com.ruoyi.ems.intent.model.IntentDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NormalizerClassifier implements IntentClassifier {

    private static final double MIN_CONFIDENCE = 0.75;

    private final IntentNormalizer normalizer;
    private final CaseIntentDefinitionsLoader intentRegistry;

    @Override
    public String getMode() {
        return "normalizer";
    }

    @Override
    public void warmUp() {
    }

    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.noMatch(getMode());
        }

        List<IntentNormalizer.NormalizedMatch> matches;
        try {
            matches = normalizer.normalize(asrText);
        } catch (Exception e) {
            log.warn("Normalizer 匹配异常: {}", e.getMessage());
            return IntentCommand.noMatch(getMode());
        }

        if (matches == null || matches.isEmpty()) {
            return IntentCommand.noMatch(getMode());
        }

        IntentNormalizer.NormalizedMatch top = matches.get(0);
        if (top.confidence() < MIN_CONFIDENCE) {
            log.debug("Normalizer 最高置信度 {} < {}，让给下游", top.confidence(), MIN_CONFIDENCE);
            return IntentCommand.noMatch(getMode());
        }

        // 歧义检查
        if (matches.size() > 1) {
            IntentNormalizer.NormalizedMatch second = matches.get(1);
            if (!second.intentCode().equals(top.intentCode())
                && top.confidence() - second.confidence() < 0.05) {
                log.debug("Normalizer 歧义: {} vs {}，让给下游",
                    top.intentCode(), second.intentCode());
                return IntentCommand.noMatch(getMode());
            }
        }

        IntentDefinition def = intentRegistry.getIntent(top.intentCode());

        log.debug("Normalizer 命中: surface={}, intent={}, mode={}, confidence={}",
            top.surface(), top.intentCode(), top.matchMode(), top.confidence());

        return IntentCommand.builder()
            .intentCode(top.intentCode())
            .confidence(top.confidence())
            .reason("词典命中: " + top.surface() + " (" + top.matchMode() + ")")
            .valid(true)
            .classifyMode("NORMALIZER")
            .source("normalizer")
            .slots(Collections.emptyMap())
            .matchedDefinition(def)
            .rawText(asrText)
            .build();
    }
}

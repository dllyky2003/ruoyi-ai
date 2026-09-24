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
import java.util.Set;
import java.util.stream.Collectors;

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

        // ★ 关键修复：按当前病例的意图白名单过滤，避免跨病例串场
        //   例如 unstable_narrow_svt 会话里不能被 stable_narrow 的 S-101-3-2 命中
        Set<String> allowed = resolveAllowedIntents(context);
        if (allowed != null && !allowed.isEmpty()) {
            int before = matches.size();
            matches = matches.stream()
                .filter(m -> allowed.contains(m.intentCode()))
                .collect(Collectors.toList());
            log.info("Normalizer 白名单: caseId={}, allowedSize={}, allowedSample={}",
                context.getCaseId(),
                allowed == null ? -1 : allowed.size(),
                allowed == null ? null : allowed.stream().limit(8).toList());

            log.info("Normalizer 候选: caseId={}, matchSize={}, matchSample={}",
                context.getCaseId(),
                matches.size(),
                matches.stream().limit(8).map(m -> m.intentCode()).toList());
            if (matches.isEmpty()) {
                return IntentCommand.noMatch(getMode());
            }
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

        log.debug("Normalizer 命中: surface={}, intent={}, mode={}, confidence={}, case={}",
            top.surface(), top.intentCode(), top.matchMode(), top.confidence(),
            context != null ? context.getCaseId() : "?");

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

    /**
     * 解析当前上下文允许的意图白名单。
     *
     * <p>优先用 {@link ClassificationContext#getAvailableIntents()}——
     * 上游若已按状态机预过滤，用它更精确。</p>
     *
     * <p>否则回退到按 caseId 从 {@link CaseIntentDefinitionsLoader} 查询。</p>
     *
     * <p>两者都拿不到时返回 null（不过滤，保持向后兼容）。</p>
     */
    private Set<String> resolveAllowedIntents(ClassificationContext context) {
        if (context == null) return null;

        // 优先：上游预过滤的可用意图（可能已按状态机约束）
        Set<String> available = context.getAvailableIntents();
        if (available != null && !available.isEmpty()) {
            return available;
        }

        // 回退：按 caseId 查"通用 + 该案例专属"
        String caseId = context.getCaseId();
        if (caseId == null || caseId.isBlank()) {
            return null;
        }
        Set<String> byCase = intentRegistry.getIntentCodesForCase(caseId);

        log.info("Normalizer 白名单: caseId={}, availableIntents={}, byCase={}",
            caseId,
            available != null ? available.size() : -1,
            byCase != null ? byCase.size() : -1);

        return (byCase == null || byCase.isEmpty()) ? null : byCase;
    }
}

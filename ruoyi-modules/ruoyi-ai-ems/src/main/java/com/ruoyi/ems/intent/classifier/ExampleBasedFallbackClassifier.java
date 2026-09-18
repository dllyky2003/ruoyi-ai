package com.ruoyi.ems.intent.classifier;

import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于意图示例的兜底分类器
 *
 * 当 LLM 分类失败或超时时，通过匹配 JSON 中定义的 examples 进行兜底。
 * 完全依赖配置文件，无需维护关键词列表。
 *
 * 支持两种匹配模式：
 * 1. 匹配最佳意图（用于 OneStage 降级）
 * 2. 匹配最佳域（用于 TwoStage 域路由降级），支持 act_type 约束
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExampleBasedFallbackClassifier implements IntentClassifier {

    private final CaseIntentDefinitionsLoader registry;

    /**
     * 匹配得分阈值，低于此值认为无匹配
     */
    private static final int SCORE_THRESHOLD = 3;

    /**
     * 关键词命中得分（keywords 字段）
     */
    private static final int KEYWORD_SCORE = 6;

    /**
     * 句式模板命中得分（pattern_hints 字段）
     */
    private static final int PATTERN_SCORE = 3;

    /**
     * 普通示例分词命中得分（examples 字段）
     */
    private static final int EXAMPLE_SCORE = 1;
    @Override
    public void warmUp()
    {

    }
    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {

        return classify(asrText);
    }
    @Override
    public IntentCommand classify(String asrText) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.invalid(asrText, "语音文本为空");
        }
        // 兜底分类器已禁用，直接返回无匹配
        log.info("ExampleBasedFallbackClassifier 已禁用，直接返回无匹配（原文本：{}）", asrText);
        return IntentCommand.invalid(asrText, "兜底分类器禁用，无匹配结果");
    }

    /**
     * 匹配最佳域（无 act_type 约束）
     * 兼容旧版本，内部调用重载方法，actType 传 null
     */
    public String findBestDomain(String asrText) {
        return findBestDomain(asrText, null);
    }

    /**
     * 匹配最佳域（带 act_type 约束）
     * 按域聚合所有意图的得分，但只考虑匹配指定 act_type 的意图
     *
     * @param asrText 语音文本
     * @param actTypeConstraint 约束的 act_type（如 "询问"、"指令"、"宣告"），传 null 表示不约束
     * @return 最佳域编码，若无匹配返回 null
     */
    public String findBestDomain(String asrText, String actTypeConstraint) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return null;
        }

        String text = asrText.trim();
        Map<String, Integer> domainScores = new HashMap<>();

        for (IntentDefinition def : registry.getAllIntentList()) {
            // 如果指定了 act_type 约束，过滤不匹配的意图
            if (actTypeConstraint != null && !actTypeConstraint.isEmpty()
                && def.getActType() != null && !def.getActType().equals(actTypeConstraint)) {
                continue;
            }

            int score = calculateMatchScore(text, def);
            if (score > 0) {
                String domain = def.getDomainCode();
                if (domain == null || domain.isEmpty()) {
                    domain = def.getSubDomain();
                }
                if (domain != null && !domain.isEmpty()) {
                    domainScores.put(domain, domainScores.getOrDefault(domain, 0) + score);
                }
            }
        }

        // 找出最高分域
        String bestDomain = null;
        int bestScore = 0;
        for (Map.Entry<String, Integer> entry : domainScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestDomain = entry.getKey();
            }
        }

        if (bestDomain != null && bestScore >= SCORE_THRESHOLD) {
            log.info("兜底匹配域(actType={}): domain={}, score={}", actTypeConstraint, bestDomain, bestScore);
            return bestDomain;
        }

        log.warn("兜底匹配域无结果, bestScore={}, actType={}", bestScore, actTypeConstraint);
        return null;
    }

    /**
     * 计算文本与意图示例的匹配得分
     * 权重策略：keywords（+6） > pattern_hints（+3） > examples 分词（+1）
     */
    public int calculateMatchScore(String text, IntentDefinition def) {
        if (def == null) return 0;
        int score = 0;

        // 1. 核心关键词：最高权重（+6）
        if (def.getKeywords() != null) {
            for (String keyword : def.getKeywords()) {
                if (keyword.length() >= 2 && text.contains(keyword)) {
                    score += KEYWORD_SCORE;
                    log.debug("命中关键词: {} +{}", keyword, KEYWORD_SCORE);
                }
            }
        }

        // 2. 句式模板：中权重（+3）
        if (def.getPatternHints() != null) {
            for (String pattern : def.getPatternHints()) {
                // 去除系统占位符 [NUM]、* 等，保留纯文本部分
                String core = pattern.replaceAll("[\\[\\]NUM*]", "").trim();
                if (core.length() >= 2 && text.contains(core)) {
                    score += PATTERN_SCORE;
                    log.debug("命中句式: {} +{}", pattern, PATTERN_SCORE);
                }
            }
        }

        // 3. 原有 examples 匹配：低权重（+1）
        if (def.getExamples() != null) {
            for (String example : def.getExamples()) {
                // 跳过负例
                if (example.startsWith("【负例】")) {
                    continue;
                }
                String cleaned = example.replaceAll("[，。！？、；：”“！\\[\\]（）]", " ");
                String[] keywords = cleaned.split("\\s+");
                for (String keyword : keywords) {
                    if (keyword.length() >= 2 && text.contains(keyword)) {
                        score += EXAMPLE_SCORE;
                        // 避免重复计数过多，但这里保持简单
                    }
                }
                // 完整短语匹配（略）
            }
        }

        return score;
    }

    @Override
    public String getMode() {
        return "FALLBACK";
    }
}

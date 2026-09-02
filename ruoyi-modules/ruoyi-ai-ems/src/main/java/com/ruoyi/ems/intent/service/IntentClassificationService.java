package com.ruoyi.ems.intent.service;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.intent.classifier.FallbackClassifier;
import com.ruoyi.ems.intent.classifier.IntentClassifier;
import com.ruoyi.ems.intent.classifier.OneStageClassifier;
import com.ruoyi.ems.intent.classifier.TwoStageClassifier;
import com.ruoyi.ems.intent.config.IntentClassifierProperties;

import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class IntentClassificationService {

    @Autowired
    private OneStageClassifier oneStageClassifier;

    @Autowired
    private TwoStageClassifier twoStageClassifier;

    @Autowired
    private FallbackClassifier fallbackClassifier;

    @Autowired
    private IntentClassifierProperties properties;

    @Autowired
    private CaseIntentDefinitionsLoader intentRegistry;

    @Autowired
    private EmsContextManager contextManager;   // 新增


    // ==================== 核心分类方法（带上下文） ====================


    /**
     * 意图分类（带上下文）
     */
    public boolean prepareForStart(String sessionId) {
        return sessionId != null && !sessionId.trim().isEmpty();
    }
    /**
     * 意图分类（带上下文）
     */
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.invalid("", "语音文本为空");
        }

        log.info("开始意图分类，分类模式: {}, 文本: {}",properties.getMode(), asrText);

        // 选择分类器（所有分类器需支持上下文）
        IntentClassifier classifier = selectClassifier();
        IntentCommand rawResult = classifier.classify(asrText, context);

        // 标记分类模式
        if (rawResult != null) {
            rawResult.setClassifyMode(properties.getMode());
        }

        // 增强处理（候选列表补全、排序、意图定义填充）
        IntentCommand enhancedResult = enrichResult(rawResult, asrText);

        // 若分类无效且启用兜底，使用兜底分类器
        if (!enhancedResult.isValid() && properties.isFallbackOnError()) {
            log.info("分类失败，启用兜底分类器: {}", enhancedResult.getErrorMessage());
            IntentCommand fallbackResult = fallbackClassifier.classify(asrText, context);
            if (fallbackResult != null) {
                fallbackResult.setClassifyMode("fallback");
            }
            enhancedResult = enrichResult(fallbackResult, asrText);
        }

        log.info("分类完成: code={}, mode={}, confidence={}, candidates={}",
            enhancedResult.getIntentCode(),
            enhancedResult.getClassifyMode(),
            enhancedResult.getConfidence(),
            enhancedResult.getCandidates() != null ? enhancedResult.getCandidates().size() : 0);

        return enhancedResult;
    }
    /**
     * 原有分类方法（兼容，不带上下文）
     */
    public IntentCommand classify(String asrText) {
        return classifyInternal(asrText, null);
    }

    /**
     * 内部分类逻辑
     */
    private IntentCommand classifyInternal(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.invalid("", "语音文本为空");
        }

        log.info("开始意图分类，模式: {}, 文本: {}, 上下文: {}",
            properties.getMode(), asrText, context != null ? context.getCurrentState() : "无");

        IntentClassifier classifier = selectClassifier();
        IntentCommand rawResult = classifier.classify(asrText, context);

        // 标记分类模式
        String mode = properties.getMode();
        if (rawResult != null) {
            rawResult.setClassifyMode(mode);
        }

        // 增强处理：补全候选列表 + 意图定义 + 排序
        IntentCommand enhancedResult = enrichResult(rawResult, asrText);

        // 启用兜底分类器（若需要）
        if (!enhancedResult.isValid() && properties.isFallbackOnError()) {
            log.info("分类失败，启用兜底分类器: {}", enhancedResult.getErrorMessage());
            IntentCommand fallbackResult = fallbackClassifier.classify(asrText, context);
            if (fallbackResult != null) {
                fallbackResult.setClassifyMode("fallback");
            }
            enhancedResult = enrichResult(fallbackResult, asrText);
        }

        log.info("分类完成: code={}, mode={}, confidence={}, candidates={}",
            enhancedResult.getIntentCode(),
            enhancedResult.getClassifyMode(),
            enhancedResult.getConfidence(),
            enhancedResult.getCandidates() != null ? enhancedResult.getCandidates().size() : 0);

        return enhancedResult;
    }

    // ==================== 原有方法（保持不变） ====================

    public void loadCaseConfig(String caseId) {
        log.info("🔄 开始全量热加载...");
        long startTime = System.currentTimeMillis();
        try {
            intentRegistry.loadIntentsForCase(caseId);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ 全量热加载完成！耗时 {}ms", elapsed);
        } catch (Exception e) {
            log.error("❌ 全量热加载失败", e);
            throw new RuntimeException("全量热加载失败", e);
        }
    }

    public void reloadIntentDictionary(String caseId) {
        log.info("🔄 开始热加载意图字典...");
        long startTime = System.currentTimeMillis();
        try {
            intentRegistry.loadIntentsForCase(caseId);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ 意图字典热加载完成！耗时 {}ms，共 {} 个意图",
                elapsed, intentRegistry.getAllIntentList().size());
        } catch (Exception e) {
            log.error("❌ 意图字典热加载失败", e);
            throw new RuntimeException("意图字典热加载失败", e);
        }
    }

    public String getLoadStatus() {
        return String.format("意图总数: %d, 子域数: %d, 模式: %s",
            intentRegistry.getAllIntentList().size(),
            intentRegistry.getSubDomainCount(),
            properties.getMode());
    }

    private IntentCommand enrichResult(IntentCommand raw, String rawText) {
        if (raw == null) {
            return IntentCommand.invalid(rawText, "分类器返回空结果");
        }

        raw.setRawText(rawText);

        List<IntentCandidate> candidates = raw.getCandidates();
        if ((candidates == null || candidates.isEmpty()) && raw.isValid() && raw.getIntentCode() != null) {
            Double conf = raw.getConfidence();
            if (conf == null) {
                conf = 0.5;
            }
            IntentCandidate single = IntentCandidate.builder()
                .intentCode(raw.getIntentCode())
                .confidence(conf)
                .score(conf)
                .build();
            raw.setCandidates(Collections.singletonList(single));
            candidates = raw.getCandidates();
        }

        if (candidates != null && candidates.size() > 1) {
            candidates.sort(Comparator
                .comparing((IntentCandidate c) -> {
                    Double conf = c.getConfidence();
                    if (conf != null) return conf;
                    Double score = c.getScore();
                    if (score != null) return score;
                    return 0.0;
                })
                .reversed());
            raw.setCandidates(candidates);

            IntentCandidate best = candidates.get(0);
            if (!Objects.equals(best.getIntentCode(), raw.getIntentCode())) {
                log.warn("候选排序后，最佳意图与原始 intentCode 不一致，自动修正为 {}", best.getIntentCode());
                raw.setIntentCode(best.getIntentCode());
                raw.setConfidence(best.getConfidence() != null ? best.getConfidence() : best.getScore());
            }
        }

        if (raw.isValid() && raw.getIntentCode() != null) {
            IntentDefinition definition = intentRegistry.getIntent(raw.getIntentCode());
            raw.setMatchedDefinition(definition);
            if (definition == null) {
                log.warn("意图码 {} 在注册表中未找到定义", raw.getIntentCode());
                raw.setMatchedSubDomain(null);
            } else {
                String subDomainName = definition.getSubDomain();
                if (subDomainName != null) {
                    SubDomain subDomain = intentRegistry.getSubDomainByName(subDomainName);
                    raw.setMatchedSubDomain(subDomain);
                    if (subDomain == null) {
                        log.warn("意图 {} 对应的子域 {} 未在注册表中找到", raw.getIntentCode(), subDomainName);
                    }
                } else {
                    raw.setMatchedSubDomain(null);
                }
            }
        } else {
            raw.setMatchedDefinition(null);
            raw.setMatchedSubDomain(null);
        }

        return raw;
    }

    private IntentClassifier selectClassifier() {
        String mode = properties.getMode();
        if ("two-stage".equalsIgnoreCase(mode)) {
            return twoStageClassifier;
        } else {
            return oneStageClassifier;
        }
    }

    public void setMode(String mode) {
        properties.setMode(mode);
        log.info("分类模式切换为: {}", mode);
    }
}

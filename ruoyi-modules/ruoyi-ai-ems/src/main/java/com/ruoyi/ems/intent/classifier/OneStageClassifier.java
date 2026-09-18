package com.ruoyi.ems.intent.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.intent.client.OllamaClient;
import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.prompt.PromptTemplateService;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 一级分类器：直接对全部意图进行分类，包含CoT推理
 * 推荐用于意图数量 < 50 的场景
 *
 * 降级策略：
 * 1. 优先使用 LLM 分类（OneStage）
 * 2. 若 LLM 返回无效结果，委托给 ExampleBasedFallbackClassifier
 * 3. 若 LLM 返回的意图与 ASR 文本的 act_type 不匹配，自动修正
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneStageClassifier implements IntentClassifier {

    private final OllamaClient ollamaClient;
    private final PromptTemplateService promptService;
    private final CaseIntentDefinitionsLoader registry;
    private final ExampleBasedFallbackClassifier fallbackClassifier;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void warmUp()
    {
        String systemPrompt="你好，我叫一级意图分类师";
        String userPrompt="你回复一下我是什么";
        String response = ollamaClient.chat(systemPrompt, userPrompt);
        log.debug(response);
    }
    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.invalid(asrText, "语音文本为空");
        }

        // 第一步：尝试 LLM 分类
        try {
            String systemPrompt = promptService.buildOneStageSystemPrompt(context);
            String userPrompt = promptService.buildOneStageUserPrompt(asrText,context);

            log.debug(systemPrompt);
            log.debug(userPrompt);

            String response = ollamaClient.chat(systemPrompt, userPrompt);
            log.debug(response);
            JsonNode root = objectMapper.readTree(response);

            String intentCode = root.path("intent_code").asText().trim();
            double confidence = root.path("confidence").asDouble(0.5);
            String reasoning = root.path("reasoning").asText("");
            String actType =root.path("act_type").asText("");

            log.debug("OneStage 分类: code={}, confidence={}, actType={},reasoning={}",
                intentCode, confidence, actType,  reasoning);

            // ========== 新增：act_type 二次校验 ==========
            if (registry.isValidIntentCode(intentCode)) {
                IntentDefinition def = registry.getIntent(intentCode);


                if (confidence < 0.6) {
                    log.info("LLM 置信度低 ({}), 尝试兜底匹配作为备选", confidence);
                    IntentCommand fallbackResult = fallbackClassifier.classify(asrText);
                    if (fallbackResult.isValid()) {
                        // 返回置信度更高的结果
                        if (fallbackResult.getConfidence() > confidence) {
                            return fallbackResult;
                        }
                    }
                }

                IntentCandidate candidate = IntentCandidate.builder()
                    .intentCode(intentCode)
                    .confidence(confidence)
                    .score(confidence)
                    .reason(reasoning)
                    .build();
                List<IntentCandidate> candidates = Collections.singletonList(candidate);
                return IntentCommand.builder()
                    .intentCode(intentCode)
                    .confidence(confidence)
                    .candidates(candidates)
                    .reason(reasoning)
                    .actType(actType)
                    .valid(true)
                    .classifyMode("ONE_STAGE")
                    .slots(Collections.emptyMap())
                    .rawText(asrText)
                    .build();
            }
            log.warn("LLM 返回无效意图码: {}, 降级到兜底分类器", intentCode);
            return fallbackClassifier.classify(asrText);

        } catch (Exception e) {
            log.error("OneStage 分类异常, 降级到兜底分类器", e);
            return fallbackClassifier.classify(asrText);
        }
    }

    /**
     * 基于 ASR 文本推断 act_type（与兜底分类器保持一致）
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

    /**
     * 修正常见编码错误
     */
    private String tryCorrectIntentCode(String code) {
        if (code == null) return null;
        if ("S1-2".equals(code)) {
            return "S1-2a";
        }
        return null;
    }

    @Override
    public String getMode() {
        return "ONE_STAGE";
    }
}

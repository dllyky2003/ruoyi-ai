package com.ruoyi.ems.intent.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.intent.client.OllamaClient;

import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.intent.prompt.PromptTemplateService;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 两级（域路由 + 细粒度）LLM 分类器。
 *
 * <p><b>失败诊断设计：</b>
 * 本类<b>不直接写失败日志</b>。失败时：
 * <ul>
 *   <li>把人类可读的诊断信息（含 domain / actType / llmIntentCode / confidence 等）
 *       放入 {@link IntentCommand#getErrorMessage()}</li>
 *   <li>把 LLM 返回的原始响应字符串放入 {@link IntentCommand#getLlmRawResponse()}</li>
 * </ul>
 * 由 {@code IntentClassificationService} 在最终分类失败时统一记录，
 * 避免一次 ASR 被拆成多条碎片化日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwoStageClassifier implements IntentClassifier {

    private final OllamaClient ollamaClient;
    private final PromptTemplateService promptService;
    private final CaseIntentDefinitionsLoader registry;
    private final ExampleBasedFallbackClassifier fallbackClassifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // 修正1：移除硬编码的 ACT_TYPE_DOMAIN_MAP，改用动态查询
    // ============================================================
    @Override
    public void warmUp() {
        String systemPrompt = "你好，我叫二级意图分类师";
        String userPrompt = "你回复一下我是什么";
        String response = ollamaClient.chat(systemPrompt, userPrompt);
        log.debug(response);
    }
    private String normalizeActType(String llmActType) {
        if (llmActType == null) return null;
        switch (llmActType.trim()) {
            case "否决": return ActType.VETO.name();
            case "策略纠偏": return ActType.STRATEGY_CORRECTION.name();
            case "闭环确认": return ActType.CLOSED_LOOP.name();
            case "发令性指令": return ActType.COMMAND.name();
            case "探查性询问": return ActType.PROBE.name();
            case "宣告": return ActType.ANNOUNCEMENT.name();
            default:
                // LLM 有时直接给枚举名
                try { return ActType.valueOf(llmActType).name(); }
                catch (Exception e) { return ActType.COMMAND.name(); }
        }
    }
    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        // ★ 提升作用域：供 catch 分支把已获得的 LLM 响应带出去
        String llmResponseForRecord = null;
        try {
            // 第一阶段：域路由（传入上下文）
            DomainResult domainResult = classifyDomain(asrText, context);
            if (domainResult == null || domainResult.getDomain() == null) {
                String reason = "域路由失败（LLM 未给出有效 domain，且 act_type 兜底也未命中）";
                log.warn("TwoStage 分类失败: {}", reason);
                // ★ 不再直接记录；诊断塞 errorMessage，交由 Service 统一记录
                IntentCommand invalid = IntentCommand.invalid(asrText, reason);
                invalid.setErrorMessage(reason);
                return invalid;
            }

            String domain = domainResult.getDomain();
            String domainActType = domainResult.getActType();
            log.debug("TwoStage 域路由结果: domain={}, actType={}", domain, domainActType);

            // 第二阶段：细粒度分类（传入上下文）
            List<IntentDefinition> candidates = registry.getIntentsByDomain(domain);
            if (candidates.isEmpty()) {
                String reason = "域 " + domain + " 下无候选意图";
                log.warn("TwoStage 分类失败: {}", reason);
                IntentCommand invalid = IntentCommand.invalid(asrText, reason);
                invalid.setErrorMessage(reason);
                invalid.setActType(domainActType);
                return invalid;
            }

            SubDomain subDomain = new SubDomain();
            subDomain.setIntents(candidates);

            String systemPrompt = promptService.buildFineClassifierSystemPrompt(subDomain);
            String userPrompt = promptService.buildFineClassifierUserPrompt(asrText, context);
            //     log.debug("系统提示词:\r\n {}", systemPrompt);
            //     log.debug("用户提示词:\r\n {}", userPrompt);
            String response = ollamaClient.chat(systemPrompt, userPrompt);
            llmResponseForRecord = response;   // ★ 记录到外层作用域
            log.debug("Ollama 细粒度响应: {}", response);
            JsonNode root = objectMapper.readTree(response);

            String intentCode = root.path("intent_code").asText().trim();

            if (intentCode.contains("[")) {
                intentCode = intentCode.substring(0, intentCode.indexOf("[")).trim();
            }
            if (intentCode.contains(" ")) {
                intentCode = intentCode.split("\\s+")[0];
            }
            double confidence = root.path("confidence").asDouble(0.5);
            String reasoning = root.path("reasoning").asText("");

            // ============================================================
            // 修正2：提取细粒度阶段的 act_type，优先使用
            // ============================================================
            String fineActType = root.path("act_type").asText("");
            String finalActType = (!fineActType.isEmpty()) ? fineActType : domainActType;
            finalActType = (finalActType == null) ? "发令性指令" : finalActType;
            finalActType = normalizeActType(fineActType);

            log.debug("TwoStage 细粒度结果: code={}, actType={}, confidence={}",
                intentCode, finalActType, confidence);

            // 校验意图是否在白名单中
            if (registry.isValidIntentCode(intentCode)) {
                IntentDefinition def = registry.getIntent(intentCode);

                IntentCandidate candidate = IntentCandidate.builder()
                    .intentCode(intentCode)
                    .confidence(confidence)
                    .score(confidence)
                    .reason(reasoning)
                    .build();

                return IntentCommand.builder()
                    .intentCode(intentCode)
                    .confidence(confidence)
                    .candidates(Collections.singletonList(candidate))
                    .actType(finalActType)
                    .reason(reasoning)
                    .valid(true)
                    .classifyMode("TWO_STAGE")
                    .slots(Collections.emptyMap())
                    .rawText(asrText)
                    .matchedDefinition(def)
                    .build();
            } else {
                // ★★★ 修正编码：既包括原 S1-2 之类的特例规则，也包括"短编码 → 完整编码"前缀补全 ★★★
                String corrected = tryCorrectIntentCode(intentCode);
                if (corrected != null && registry.isValidIntentCode(corrected)) {
                    log.warn("修正意图编码: {} -> {}", intentCode, corrected);
                    IntentDefinition correctedDef = registry.getIntent(corrected);

                    // 只对"特例规则修正"做置信度衰减；前缀补全属于"格式修复"，不衰减
                    double correctedConfidence = confidence;
                    if (!corrected.startsWith(intentCode + "_")) {
                        correctedConfidence = confidence * 0.8;
                    }

                    IntentCandidate candidate = IntentCandidate.builder()
                        .intentCode(corrected)
                        .confidence(correctedConfidence)
                        .score(correctedConfidence)
                        .reason("Corrected from: " + intentCode + " | Domain: " + domain)
                        .build();

                    return IntentCommand.builder()
                        .intentCode(corrected)
                        .confidence(correctedConfidence)
                        .candidates(Collections.singletonList(candidate))
                        .reason("Corrected from: " + intentCode + " | Domain: " + domain)
                        .actType(finalActType)
                        .valid(true)
                        .classifyMode("TWO_STAGE_CORRECTED")
                        .slots(Collections.emptyMap())
                        .rawText(asrText)
                        .matchedDefinition(correctedDef)
                        .build();
                }

                // ★ 修正失败：诊断放 errorMessage，LLM 响应放 llmRawResponse
                String reason = String.format(
                    "意图编码不在白名单:\n" +
                        "  llmIntentCode=%s\n" +
                        "  domain=%s\n" +
                        "  actType=%s\n" +
                        "  confidence=%.2f\n" +
                        " systemPrompts=%s \n" +
                        " userPrompts=%s",
                    intentCode, domain, finalActType, confidence,systemPrompt,userPrompt);
                log.warn("TwoStage 分类失败: {}", reason);

                IntentCommand invalid = IntentCommand.invalid(asrText,
                    "意图编码不在白名单中: " + intentCode);
                invalid.setErrorMessage(reason);
                invalid.setLlmRawResponse(response);
                invalid.setActType(finalActType);
                return invalid;
            }
        } catch (Exception e) {
            String reason = "分类异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("TwoStage 分类异常", e);
            IntentCommand invalid = IntentCommand.invalid(asrText, "分类异常: " + e.getMessage());
            invalid.setErrorMessage(reason);
            invalid.setLlmRawResponse(llmResponseForRecord);
            return invalid;
        }
    }

    // ========== 域路由（带上下文） ==========

    private DomainResult classifyDomain(String asrText, ClassificationContext context) {
        try {
            String systemPrompt = promptService.buildDomainRouterSystemPrompt();
            String userPrompt = promptService.buildDomainRouterUserPrompt(asrText, context);
            //  log.debug("系统提示词:\r\n {}", systemPrompt);
            //  log.debug("用户提示词:\r\n {}", userPrompt);
            String response = ollamaClient.chat(systemPrompt, userPrompt);
            log.debug("Ollama 域路由响应: {}", response);
            JsonNode root = objectMapper.readTree(response);

            // ============================================================
            // 修正4：移除无效的 reasoning_chain 路径，直接读取 act_type
            // ============================================================
            String domain = root.path("domain").asText().trim();
            String actType = root.path("act_type").asText("");
            double confidence = root.path("confidence").asDouble(0.5);

            log.info("LLM 域路由: domain={}, actType={}, confidence={}", domain, actType, confidence);

            Set<String> validDomains = registry.getIntentsByDomain().keySet();

            if (confidence >= 0.6 && validDomains.contains(domain)) {
                log.debug("LLM 域路由成功: domain={}, actType={}", domain, actType);
                return new DomainResult(domain, actType);
            } else {
                log.warn("LLM 域路由置信度低或无效域: domain={}, conf={}", domain, confidence);
            }

            // 利用 act_type 修正域（修正5：改用动态查询）
            if (!actType.isEmpty() && !domain.isEmpty() && !validDomains.contains(domain)) {
                String correctedDomain = correctDomainByActType(actType, asrText);
                if (correctedDomain != null && validDomains.contains(correctedDomain)) {
                    log.info("🔧 利用 act_type 修正域: {} → {}", domain, correctedDomain);
                    return new DomainResult(correctedDomain, actType);
                }
            }

        } catch (Exception e) {
            log.warn("LLM 域路由异常: {}", e.getMessage());
        }

        // 兜底策略
        log.info("使用兜底策略进行域路由: {}", asrText);
        String actTypeHint = determineActType(asrText);
        String fallbackDomain = fallbackClassifier.findBestDomain(asrText, actTypeHint);

        if (fallbackDomain != null) {
            return new DomainResult(fallbackDomain, actTypeHint);
        }

        return null;
    }

    // ============================================================
    // 修正5：动态获取域列表，替代硬编码映射
    // ============================================================

    /**
     * 根据 act_type 和语音文本，从动态域列表中匹配合适的域
     * 完全依赖注册表中的域定义和意图关键词，无硬编码域名称
     */
    private String correctDomainByActType(String actType, String asrText) {
        Set<String> validDomains = registry.getIntentsByDomain().keySet();

        String bestDomain = null;
        int bestScore = 0;

        for (String domain : validDomains) {
            List<IntentDefinition> intents = registry.getIntentsByDomain(domain);
            if (intents == null || intents.isEmpty()) {
                continue;
            }

            boolean hasMatchingActType = intents.stream()
                .anyMatch(def -> actType.equals(def.getActType()));

            if (!hasMatchingActType) {
                continue;
            }

            int domainScore = intents.stream()
                .mapToInt(def -> fallbackClassifier.calculateMatchScore(asrText, def))
                .max()
                .orElse(0);

            if (domainScore > bestScore) {
                bestScore = domainScore;
                bestDomain = domain;
            }
        }

        return (bestScore >= 3) ? bestDomain : null;
    }

    // ========== 辅助方法 ==========

    private String determineActType(String text) {
        if (text.contains("吗") || text.contains("多少") || text.contains("什么")
            || text.contains("几") || text.contains("是否") || text.contains("有没有")) {
            return "询问";
        }
        if (text.contains("了") && !text.contains("准备")) {
            return "宣告";
        }
        return "指令";
    }

    /**
     * 修正意图编码。
     * <p>处理两类 LLM 输出问题：
     * <ol>
     *   <li><b>特例规则</b>：如 {@code S1-2 → S1-2a}，属于业务特定映射；</li>
     *   <li><b>短编码补全</b>：LLM 常输出前缀（如 {@code IA_020}），而白名单中是完整编码
     *       （{@code IA_020_arm_drift_check}）。若 {@code code + "_"} 能唯一命中一个已注册意图，
     *       则补全为完整编码。</li>
     * </ol>
     *
     * @param code LLM 输出的原始编码
     * @return 修正后的编码；无匹配返回 {@code null}
     */
    private String tryCorrectIntentCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }

        // ---- 1. 特例规则（业务特定） ----
        if ("S1-2".equals(code)) {
            return "S1-2a";
        }

        // ---- 2. 短编码 → 完整编码 前缀补全 ----
        String prefix = code + "_";
        Map<String, Set<String>> byDomain = registry.getIntentsByDomain();
        List<String> matches = new ArrayList<>();
        for (Set<String> intents : byDomain.values()) {
            if (intents == null) {
                continue;
            }
            for (String fullCode : intents) {
                if (fullCode != null && fullCode.startsWith(prefix)) {
                    matches.add(fullCode);
                }
            }
        }

        if (matches.size() == 1) {
            log.info("🎯 短编码补全: {} -> {} (唯一前缀匹配)", code, matches.get(0));
            return matches.get(0);
        }
        if (matches.size() > 1) {
            log.warn("⚠️ 短编码 {} 前缀匹配到多个意图，无法唯一定位: {}", code, matches);
        } else {
            log.debug("短编码 {} 前缀无匹配", code);
        }
        return null;
    }

    @Override
    public String getMode() {
        return "TWO_STAGE";
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class DomainResult {
        private String domain;
        private String actType;
    }
}

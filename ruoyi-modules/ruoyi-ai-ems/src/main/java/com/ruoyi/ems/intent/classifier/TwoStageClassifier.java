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
                try { return ActType.valueOf(llmActType).name(); }
                catch (Exception e) { return ActType.COMMAND.name(); }
        }
    }

    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        // ★ 提升作用域：供 catch 分支把已获得的 LLM 响应带出去
        String llmResponseForRecord = null;
        // ★ 提取 caseId（可能为 null，此时不过滤，保持向后兼容）
        String caseId = context != null ? context.getCaseId() : null;

        try {
            // 第一阶段：域路由（传入上下文）
            DomainResult domainResult = classifyDomain(asrText, context);
            if (domainResult == null || domainResult.getDomain() == null) {
                String reason = "域路由失败（LLM 未给出有效 domain，且 act_type 兜底也未命中）";
                log.warn("TwoStage 分类失败: {}", reason);
                IntentCommand invalid = IntentCommand.invalid(asrText, reason);
                invalid.setErrorMessage(reason);
                return invalid;
            }

            String domain = domainResult.getDomain();
            String domainActType = domainResult.getActType();
            log.debug("TwoStage 域路由结果: domain={}, actType={}", domain, domainActType);

            // ============================================================
            // ★ 修复：候选意图列表按 caseId 过滤，避免跨病例串场
            //   例如 unstable_narrow_svt 会话里，候选列表不应包含 stable_narrow 的 S-101-3-2
            // ============================================================
            List<IntentDefinition> rawCandidates = registry.getIntentsByDomain(domain);
            List<IntentDefinition> candidates = filterCandidatesByCase(rawCandidates, caseId);

            if (log.isDebugEnabled()) {
                log.debug("TwoStage 候选意图 (domain={}, caseId={}): {} -> {}",
                    domain, caseId, rawCandidates == null ? 0 : rawCandidates.size(), candidates.size());
            }

            if (candidates.isEmpty()) {
                String reason = "域 " + domain + " 下无候选意图" +
                    (caseId != null ? "（已按病例 " + caseId + " 过滤）" : "");
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
            String response = ollamaClient.chat(systemPrompt, userPrompt);
            llmResponseForRecord = response;
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

            String fineActType = root.path("act_type").asText("");
            String finalActType = (!fineActType.isEmpty()) ? fineActType : domainActType;
            finalActType = (finalActType == null) ? "发令性指令" : finalActType;
            finalActType = normalizeActType(fineActType);

            log.debug("TwoStage 细粒度结果: code={}, actType={}, confidence={}",
                intentCode, finalActType, confidence);

            // ============================================================
            // ★ 修复：校验意图是否在"当前病例的白名单"中，而不只是全局白名单
            // ============================================================
            if (isValidIntentCodeForCase(intentCode, caseId)) {
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
                // ★ 修正编码时也按 caseId 过滤
                String corrected = tryCorrectIntentCode(intentCode, caseId);
                if (corrected != null && isValidIntentCodeForCase(corrected, caseId)) {
                    log.warn("修正意图编码: {} -> {}", intentCode, corrected);
                    IntentDefinition correctedDef = registry.getIntent(corrected);

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

                String reason = String.format(
                    "意图编码不在当前病例白名单:\n" +
                        "  llmIntentCode=%s\n" +
                        "  caseId=%s\n" +
                        "  domain=%s\n" +
                        "  actType=%s\n" +
                        "  confidence=%.2f\n" +
                        " systemPrompts=%s \n" +
                        " userPrompts=%s",
                    intentCode, caseId, domain, finalActType, confidence, systemPrompt, userPrompt);
                log.warn("TwoStage 分类失败: {}", reason);

                IntentCommand invalid = IntentCommand.invalid(asrText,
                    "意图编码不在当前病例白名单中: " + intentCode);
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

    // ============================================================
    // ★ 新增：按病例过滤候选意图
    // ============================================================

    /**
     * 按病例过滤候选意图列表。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code caseId} 为空 → 不过滤（保持向后兼容）</li>
     *   <li>{@code caseId} 非空 → 只保留"通用意图 + 该病例专属意图"</li>
     * </ul>
     */
    private List<IntentDefinition> filterCandidatesByCase(
        List<IntentDefinition> candidates, String caseId) {

        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();
        if (caseId == null || caseId.isBlank()) return candidates;

        Set<String> allowed = registry.getIntentCodesForCase(caseId);
        if (allowed == null || allowed.isEmpty()) {
            // 查不到白名单时不过滤，避免误杀
            return candidates;
        }

        List<IntentDefinition> filtered = new ArrayList<>(candidates.size());
        for (IntentDefinition def : candidates) {
            if (def != null && def.getIntentCode() != null && allowed.contains(def.getIntentCode())) {
                filtered.add(def);
            }
        }
        return filtered;
    }

    /**
     * 判断意图编码是否是"当前病例下有效"。
     *
     * <p>与 {@code registry.isValidIntentCode} 的区别：
     * 前者只校验全局白名单，本方法额外校验该意图是否属于当前病例（通用 + 专属）。
     */
    private boolean isValidIntentCodeForCase(String intentCode, String caseId) {
        if (intentCode == null || intentCode.isBlank()) return false;
        if (!registry.isValidIntentCode(intentCode)) return false;
        if (caseId == null || caseId.isBlank()) return true;

        Set<String> allowed = registry.getIntentCodesForCase(caseId);
        if (allowed == null || allowed.isEmpty()) return true;
        return allowed.contains(intentCode);
    }

    // ========== 域路由（带上下文） ==========

    private DomainResult classifyDomain(String asrText, ClassificationContext context) {
        try {
            String systemPrompt = promptService.buildDomainRouterSystemPrompt();
            String userPrompt = promptService.buildDomainRouterUserPrompt(asrText, context);
            String response = ollamaClient.chat(systemPrompt, userPrompt);
            log.debug("Ollama 域路由响应: {}", response);
            JsonNode root = objectMapper.readTree(response);

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

            // 利用 act_type 修正域（★ 传入 context，让过滤感知病例）
            if (!actType.isEmpty() && !domain.isEmpty() && !validDomains.contains(domain)) {
                String correctedDomain = correctDomainByActType(actType, asrText, context);
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

    /**
     * 根据 act_type 和语音文本，从动态域列表中匹配合适的域。
     * ★ 修改：按 caseId 过滤该域下的意图，避免把 case 无关的意图计入打分。
     */
    private String correctDomainByActType(String actType, String asrText, ClassificationContext context) {
        Set<String> validDomains = registry.getIntentsByDomain().keySet();
        String caseId = context != null ? context.getCaseId() : null;

        String bestDomain = null;
        int bestScore = 0;

        for (String domain : validDomains) {
            List<IntentDefinition> intents = filterCandidatesByCase(
                registry.getIntentsByDomain(domain), caseId);
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
     * ★ 修改：前缀补全时只在该 caseId 的白名单里查找。
     */
    private String tryCorrectIntentCode(String code, String caseId) {
        if (code == null || code.isEmpty()) {
            return null;
        }

        // ---- 1. 特例规则（业务特定） ----
        if ("S1-2".equals(code)) {
            return "S1-2a";
        }

        // ---- 2. 短编码 → 完整编码 前缀补全 ----
        String prefix = code + "_";

        // ★ 优先在 caseId 白名单里查
        Set<String> allowed = (caseId != null && !caseId.isBlank())
            ? registry.getIntentCodesForCase(caseId)
            : null;

        List<String> matches = new ArrayList<>();
        if (allowed != null && !allowed.isEmpty()) {
            for (String fullCode : allowed) {
                if (fullCode != null && fullCode.startsWith(prefix)) {
                    matches.add(fullCode);
                }
            }
        } else {
            // 无 caseId 时回退全局（保持向后兼容）
            Map<String, Set<String>> byDomain = registry.getIntentsByDomain();
            for (Set<String> intents : byDomain.values()) {
                if (intents == null) continue;
                for (String fullCode : intents) {
                    if (fullCode != null && fullCode.startsWith(prefix)) {
                        matches.add(fullCode);
                    }
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

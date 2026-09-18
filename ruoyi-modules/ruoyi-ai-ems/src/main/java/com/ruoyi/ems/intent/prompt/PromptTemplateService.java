package com.ruoyi.ems.intent.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.ExtractionConfig;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.ParamRule;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptTemplateService {

    private final CaseIntentDefinitionsLoader intentLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String oneStageSystemTemplate;
    private String oneStageUserTemplate;
    private String domainRouterSystemTemplate;
    private String domainRouterUserTemplate;
    private String fineClassifierSystemTemplate;
    private String fineClassifierUserTemplate;
    private String extractionSystemTemplate;
    private String extractionUserTemplate;

    @PostConstruct
    public void init() {
        try {
            oneStageSystemTemplate = loadTemplate("prompts/one_stage_system_prompt.txt");
            oneStageUserTemplate = loadTemplate("prompts/one_stage_user_prompt.txt");
            domainRouterSystemTemplate = loadTemplate("prompts/domain_router_system_prompt.txt");
            domainRouterUserTemplate = loadTemplate("prompts/domain_router_user_prompt.txt");
            fineClassifierSystemTemplate = loadTemplate("prompts/fine_classifier_system_prompt.txt");
            fineClassifierUserTemplate = loadTemplate("prompts/fine_classifier_user_prompt.txt");
            extractionSystemTemplate = loadTemplate("prompts/extraction_system_prompt.txt");
            extractionUserTemplate = loadTemplate("prompts/extraction_user_prompt.txt");
            log.info("✅ 所有提示词模板加载完成");
        } catch (IOException e) {
            log.error("❌ 加载提示词模板失败", e);
            throw new RuntimeException("提示词模板加载失败", e);
        }
    }

    private String loadTemplate(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("⚠️ 模板文件不存在: {}", path);
            return "";
        }
        if (!resource.isReadable()) {
            log.warn("⚠️ 模板文件不可读: {}", path);
            return "";
        }
        String content = Files.readString(resource.getFile().toPath(), StandardCharsets.UTF_8);
        log.debug("✅ 加载模板: {} ({} 字符)", path, content.length());
        return content;
    }

    // ============================================================
    // 第一步：域路由（Domain Router）
    // ============================================================

    public String buildDomainRouterSystemPrompt() {
        List<SubDomain> allDomains = intentLoader.getAllSubDomains();
        if (allDomains == null || allDomains.isEmpty()) {
            return "【警告】无可用域定义，请检查配置文件。";
        }

        String domainList = allDomains.stream()
            .map(domain -> {
                String code = getDomainCode(domain);
                String name = domain.getSubDomainName();
                String desc = domain.getSubDomainDescription();
                if (desc != null && desc.length() > 30) {
                    desc = desc.substring(0, 30) + "…";
                }
                return String.format("- %s | %s | %s", code, name, desc != null ? desc : "无描述");
            })
            .collect(Collectors.joining("\n"));

        String domainKeywordMap = allDomains.stream()
            .map(domain -> {
                String code = getDomainCode(domain);
                Set<String> keywords = domain.getIntents().stream()
                    .flatMap(def -> def.getKeywords().stream())
                    .filter(Objects::nonNull)
                    .limit(8)
                    .collect(Collectors.toSet());
                String kwStr = keywords.isEmpty() ? "无" : String.join("、", keywords);
                return String.format("- %s：%s", code, kwStr);
            })
            .collect(Collectors.joining("\n"));

        String domainDescriptions = allDomains.stream()
            .map(domain -> {
                String code = getDomainCode(domain);
                String desc = domain.getSubDomainDescription();
                if (desc == null || desc.isEmpty()) {
                    return String.format("- %s：无描述", code);
                }
                String brief = desc.length() > 40 ? desc.substring(0, 40) + "…" : desc;
                return String.format("- %s：%s", code, brief);
            })
            .collect(Collectors.joining("\n"));

        String allActTypes = allDomains.stream()
            .flatMap(domain -> domain.getIntents().stream())
            .map(IntentDefinition::getActType)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining("、"));

        String allCodes = allDomains.stream()
            .map(this::getDomainCode)
            .collect(Collectors.joining("、"));

        return domainRouterSystemTemplate
            .replace("{domainCount}", String.valueOf(allDomains.size()))
            .replace("{domainList}", domainList)
            .replace("{domainKeywordMap}", domainKeywordMap)
            .replace("{domainDescriptions}", domainDescriptions)
            .replace("{allActTypes}", allActTypes)
            .replace("{allCodes}", allCodes);
    }

    public String buildDomainRouterUserPrompt(String asrText, ClassificationContext context) {
        String contextPrompt = (context != null) ? context.buildContextPrompt() : "";
        return domainRouterUserTemplate
            .replace("{context}", contextPrompt)
            .replace("{asrText}", asrText);
    }

    // ============================================================
    // 第二步：细粒度分类（Fine Classifier）
    // ============================================================

    public String buildFineClassifierSystemPrompt(SubDomain domain) {
        if (domain == null || domain.getIntents() == null || domain.getIntents().isEmpty()) {
            return "【警告】当前域下无候选意图，请返回空结果。";
        }

        StringBuilder listSb = new StringBuilder();
        for (IntentDefinition def : domain.getIntents()) {
            listSb.append(formatIntentWithKeywords(def));
        }
        String intentList = listSb.toString();
        String domainCode = getDomainCode(domain);

        return fineClassifierSystemTemplate
            .replace("{domainCode}", domainCode != null ? domainCode : "")
            .replace("{intentList}", intentList);
    }

    public String buildFineClassifierUserPrompt(String asrText, ClassificationContext context) {
        String contextPrompt = (context != null) ? context.buildContextPrompt() : "";
        return fineClassifierUserTemplate
            .replace("{context}", contextPrompt)
            .replace("{asrText}", asrText);
    }

    // ============================================================
    // 兜底方案：全量一级分类
    // ============================================================

    private List<IntentDefinition> getCandidateIntents(ClassificationContext context) {
        if (context != null && context.getAvailableIntents() != null && !context.getAvailableIntents().isEmpty()) {
            return context.getAvailableIntents().stream()
                .map(intentLoader::getIntent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
        return intentLoader.getAllIntentList();
    }

    private String buildIntentList(List<IntentDefinition> intents) {
        if (intents == null || intents.isEmpty()) {
            return "（无候选意图）";
        }
        StringBuilder sb = new StringBuilder();
        for (IntentDefinition def : intents) {
            String code = def.getIntentCode() != null ? def.getIntentCode() : "-";
            String name = def.getNameCn() != null ? def.getNameCn() : "-";
            String actType = def.getActType() != null ? def.getActType() : "-";
            String keywords = "-";
            if (def.getKeywords() != null && !def.getKeywords().isEmpty()) {
                keywords = def.getKeywords().stream().limit(3).collect(Collectors.joining("、"));
            }
            String example = "-";
            if (def.getExamples() != null && !def.getExamples().isEmpty()) {
                example = def.getExamples().get(0);
                if (example.length() > 20) example = example.substring(0, 20) + "…";
            }
            sb.append(String.format("- %s | %s | [%s] | 关键词：%s | 示例：%s\n",
                code, name, actType, keywords, example));
        }
        return sb.toString();
    }

    public String buildOneStageSystemPrompt(ClassificationContext context) {
        List<IntentDefinition> candidates = getCandidateIntents(context);
        String intentList = buildIntentList(candidates);
        return oneStageSystemTemplate
            .replace("{intentCount}", String.valueOf(candidates.size()))
            .replace("{intentTable}", intentList);
    }

    public String buildOneStageUserPrompt(String asrText, ClassificationContext context) {
        if (context != null) {
            String contextPrompt = context.buildContextPrompt();
            return oneStageUserTemplate
                .replace("{context}", contextPrompt)
                .replace("{asrText}", asrText);
        }
        return "当前语音文本：\n" + asrText + "\n\n请根据上述规则进行意图分类，严格遵循三步推理。";
    }

    // ============================================================
    // 参数抽取提示词构建（新增）
    // ============================================================

    public String buildExtractionSystemPrompt(IntentCommand intent) {
        if (intent == null) {
            return getExtractionSystemTemplate();
        }

        IntentDefinition definition = intent.getMatchedDefinition();
        // 1. 意图级精准覆盖
        if (definition != null && definition.getExtraction() != null) {
            String override = definition.getExtraction().getOverrideInstruction();
            if (StringUtils.hasText(override)) {
                return override;
            }
        }

        // 2. 子域级共享提示
        SubDomain subDomain = intent.getMatchedSubDomain();
        if (subDomain != null && StringUtils.hasText(subDomain.getExtractionHint())) {
            return subDomain.getExtractionHint() + "\n\n" + getExtractionSystemTemplate();
        }

        // 3. 默认通用模板
        return getExtractionSystemTemplate();
    }

    public String buildExtractionUserPrompt(IntentCommand intent) {
        if (intent == null) {
            return null;
        }

        IntentDefinition definition = intent.getMatchedDefinition();
        if (definition == null || definition.getExtraction() == null) {
            return null;
        }

        ExtractionConfig extraction = definition.getExtraction();
        String rulesJson = extraction.getLlmPrompt();

        String template = getExtractionUserTemplate();
        // ✅ 修复：显式转为字符串，避免类型不匹配
        String rawText = intent.getRawText() != null ? intent.getRawText() : "";
        String targetParamsStr = extraction.getTargetParams() != null ? extraction.getTargetParams().toString() : "[]";
        String rulesStr = rulesJson != null ? rulesJson : "{}";

        return template
            .replace("{{rawText}}", rawText)
            .replace("{{targetParams}}", targetParamsStr)
            .replace("{{rules}}", rulesStr);
    }

    public Map<String, String> buildExtractionPromptPair(IntentCommand intent) {
        String system = buildExtractionSystemPrompt(intent);
        String user = buildExtractionUserPrompt(intent);
        if (system == null || user == null) {
            return null;
        }
        Map<String, String> pair = new LinkedHashMap<>();
        pair.put("system", system);
        pair.put("user", user);
        log.info(system);
        log.info(user);
        return pair;
    }

    // ==================== 私有辅助方法 ====================

    private String getExtractionSystemTemplate() {
        return extractionSystemTemplate;
    }

    private String getExtractionUserTemplate() {
        return extractionUserTemplate;
    }

    private String serializeRules(Map<String, ParamRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            log.warn("序列化规则失败，使用 toString: {}", e.getMessage());
            return rules.toString();
        }
    }

    // ============================================================
    // 公共辅助方法
    // ============================================================

    private String getDomainCode(SubDomain domain) {
        if (domain.getDomainCode() != null && !domain.getDomainCode().isEmpty()) {
            return domain.getDomainCode();
        }
        return domain.getSubDomainName();
    }

    private String formatIntentWithKeywords(IntentDefinition def) {
        String example = def.getExamples() != null && !def.getExamples().isEmpty()
            ? def.getExamples().get(0)
            : "无示例";
        if (example.length() > 20) {
            example = example.substring(0, 20) + "...";
        }
        String actType = def.getActType() != null ? def.getActType() : "未定义";

        String keywordsDisplay = "";
        if (def.getKeywords() != null && !def.getKeywords().isEmpty()) {
            List<String> kw = def.getKeywords();
            if (kw.size() > 5) {
                kw = kw.subList(0, 5);
                keywordsDisplay = String.join("、", kw) + "…";
            } else {
                keywordsDisplay = String.join("、", kw);
            }
        } else {
            keywordsDisplay = "无";
        }

        return String.format("- %s [%s] %s | 关键词：%s | 如：%s\n",
            def.getIntentCode(),
            actType,
            def.getNameCn(),
            keywordsDisplay,
            example);
    }

    // ============================================================
    // 调试方法
    // ============================================================

    public void printDomainRouterPrompt() {
        log.info("\n========== 域路由 System Prompt ==========\n{}\n==========================================",
            buildDomainRouterSystemPrompt());
    }
}

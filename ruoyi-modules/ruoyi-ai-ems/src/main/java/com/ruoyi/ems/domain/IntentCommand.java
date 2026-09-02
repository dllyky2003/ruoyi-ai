package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.intent.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentCommand {

    // ==================== 基础字段 ====================
    private String intentCode;
    private Map<String, Object> slots;
    private Double confidence;
    private String rawText;
    private boolean valid;
    private String reason;
    private String actType;
    private String errorMessage;
    private String classifyMode;

    // ==================== 增强字段 ====================
    private List<IntentCandidate> candidates;

    @JsonIgnore
    private IntentDefinition matchedDefinition;

    @JsonIgnore
    private SubDomain matchedSubDomain;   // 缓存子域对象

    private static final ObjectMapper MAPPER = new ObjectMapper();
// ================================================================
    //  参数抽取提示词生成方法（依赖 matchedDefinition 和 matchedSubDomain）
    // ================================================================
private static final String DEFAULT_SYSTEM_PROMPT = """
    你是一位擅长院前ACLS和Megacode场景的临床参数抽取专家。
    你的任务是从急救语音文本中，精准提取【结构化参数】，并以严格的JSON格式输出。

    ## 【抽取核心准则】
    1. **数值与单位分离**：对于剂量、时间、容量等参数，必须分别抽取 `数值(value)` 和 `单位(unit)`。严禁只提取数字而忽略单位（例如 "肾上腺素1mg" 应提取为 {"value": 1, "unit": "mg"}，而非 {"value": 1} ）。
    2. **症状阴阳性判断**：对于"疼痛"、"呼吸困难"等主观症状，若语音包含"没有"、"不痛"、"未缓解"等否定词，则布尔值抽取为 `false`；若为肯定描述，则为 `true`。若未提及，必须输出 `null`。
    3. **严格枚举匹配**：对于 `string` 类型参数，若配置了 `enum` 枚举列表，返回的值必须严格等于列表中的某一项，严禁自行发明新词。
    4. **如果某个字段无法提取，直接省略该字段。

    ## 【ASR 语音识别容错策略】⭐新增
    输入文本来自自动语音识别（ASR），可能存在以下问题：
    - 数字吞音（如"6mg"被识别为"mg"或"六"变成"零"）
    - 单位遗漏（只说"推6"而未说"毫克"）
    - 同音错字（如"腺苷"被识别为"线干"）
    - 数值异常（如将6识别为16或60）

    为应对上述情况，你必须遵循以下规则：
    - **缺失数值时**：若语音中明确提到了药物名称或操作，但数值被吞掉，请根据临床常规默认值补全（例如：腺苷首剂6mg，次剂12mg；盐水冲管20ml；若无法推断，则将该字段输出为 `null`）。
    - **数值合理性校验**：
        - 腺苷剂量只允许 6mg 或 12mg，若识别出的数字超出此范围（如16、60），视为ASR错误，应回退为基于 `dose_sequence` 的默认值（首剂6mg，次剂12mg）。
        - 血压收缩压合理范围 70~250 mmHg，舒张压 40~200 mmHg，超出范围则输出 `null`。
        - 心率合理范围 20~300 bpm，血氧 50~100%，呼吸频率 5~60 次/分，GCS 3~15 分，超出则输出 `null`。
    - **单位缺失时**：根据药物/操作类型自动补全单位（如腺苷→mg，盐水→ml，电复律能量→J）。

    ## 【输出约束】
    1. 仅输出一个合法的 JSON 对象。
    2. 不要添加任何解释、前缀（如 "结果是："）或 Markdown 代码块标记。
    3. Key 必须严格匹配【所需参数列表】。
    """;


    // ==================== 便捷构造方法 ====================
    public static IntentCommand success(String intentCode, Map<String, Object> parameters,
                                        Double confidence, List<IntentCandidate> candidates,
                                        IntentDefinition definition) {
        return IntentCommand.builder()
            .intentCode(intentCode)
            .slots(parameters != null ? parameters : Collections.emptyMap())
            .confidence(confidence)
            .candidates(candidates)
            .matchedDefinition(definition)
            .valid(true)
            .build();
    }

    public static IntentCommand invalid(String rawText, String errorMessage) {
        return IntentCommand.builder()
            .rawText(rawText)
            .errorMessage(errorMessage)
            .valid(false)
            .slots(Collections.emptyMap())
            .candidates(Collections.emptyList())
            .confidence(null)
            .build();
    }

    public String getTopCandidateCode() {
        return (candidates != null && !candidates.isEmpty()) ? candidates.get(0).getIntentCode() : null;
    }



    @JsonIgnore
    public String buildExtractionSystemPrompt() {
        if (matchedDefinition == null || matchedDefinition.getExtraction() == null) {
            return null;
        }

        ExtractionConfig ext = matchedDefinition.getExtraction();

        // 1. 意图级精准覆盖（最高优先级）
        if (StringUtils.hasText(ext.getOverrideInstruction())) {
            return ext.getOverrideInstruction();
        }

        // 2. 子域级共享提示（次优先级）—— 直接使用缓存对象
        if (matchedSubDomain != null && StringUtils.hasText(matchedSubDomain.getExtractionHint())) {
            return matchedSubDomain.getExtractionHint() + "\n\n" + DEFAULT_SYSTEM_PROMPT;
        }

        // 3. 默认通用模板
        return DEFAULT_SYSTEM_PROMPT;
    }

    @JsonIgnore
    public String buildExtractionUserPrompt() {
        if (matchedDefinition == null || matchedDefinition.getExtraction() == null) {
            return null;
        }

        ExtractionConfig extraction = matchedDefinition.getExtraction();
        String rulesJson = serializeRules(extraction.getRules());

        return String.format("""
        【原始语音文本】
        %s

        【所需参数列表】
        %s

        【参数抽取规则（JSON格式）】
        %s

        请根据上述规则，从语音文本中提取参数，仅输出 JSON 对象。
        """,
            rawText != null ? rawText : "",
            extraction.getTargetParams() != null ? extraction.getTargetParams() : "[]",
            rulesJson != null ? rulesJson : "{}"
        );
    }

    @JsonIgnore
    public Map<String, String> buildExtractionPromptPair() {
        String system = buildExtractionSystemPrompt();
        String user = buildExtractionUserPrompt();
        if (system == null || user == null) {
            return Collections.emptyMap();
        }
        Map<String, String> pair = new LinkedHashMap<>();
        pair.put("system", system);
        pair.put("user", user);
        return pair;
    }

    // ==================== 私有辅助方法 ====================
    private String serializeRules(Map<String, ParamRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            return rules.toString();
        }
    }

    @JsonIgnore
    public AiPromptPair getExtractionPrompts() {
        Map<String, String> pair = buildExtractionPromptPair();
        if (pair.isEmpty()) {
            return null;
        }
        return new AiPromptPair(pair.get("system"), pair.get("user"));
    }

    /**
     * 将命中的意图码和抽取的参数格式化为一个可读字符串。
     */
    public String formatIntentAndParameters() {
        if (!valid) {
            return "无效意图: " + (errorMessage != null ? errorMessage : "未知错误");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("意图代码: ").append(intentCode != null ? intentCode : "未知");
        sb.append("意图名称: ").append(matchedDefinition != null ? matchedDefinition.getNameCn() : "未知");
        sb.append("选择原因: ").append(reason != null ? reason : "?");
        sb.append(", 参数: ");
        if (slots != null && !slots.isEmpty()) {
            sb.append(slots.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}")));
        } else {
            sb.append("无");
        }

        if (confidence != null) {
            sb.append(", 置信度: ").append(String.format("%.2f", confidence));
        }

        return sb.toString();
    }

    /**
     * 安全设置参数（自动过滤 null 值）
     */
    public void setSlots(Map<String, Object> slots) {
        if (slots == null) {
            this.slots = new HashMap<>();
            return;
        }
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : slots.entrySet()) {
            if (entry.getValue() != null) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }
        this.slots = cleaned;
    }

    public Map<String, Object> getSlots() {
        return slots != null ? slots : Collections.emptyMap();
    }
}

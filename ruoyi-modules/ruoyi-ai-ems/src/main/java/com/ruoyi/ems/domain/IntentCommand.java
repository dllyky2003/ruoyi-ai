package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.SubDomain;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private String source;
    private Object matchDetail;

    /** 分类来源：meta-rule / keyword / vector / llm / default */
    private String classifySource;   // 分类来源
    private Integer sentenceIndex;   // 分句序号
    /** 分句序号，从 0 开始 */
    @JsonIgnore
    private String llmRawResponse;
    // ================================================================
    //  便捷构造方法（保持不变）
    // ================================================================

    public static IntentCommand keywordHit(String intentCode, String asrText, String source, IntentDefinition definition) {
        return IntentCommand.builder()
            .rawText(asrText)
            .intentCode(intentCode)
            .source(source != null ? source : "keyword")
            .classifyMode("keyword")
            .confidence(1.0)
            .valid(true)
            .slots(Collections.emptyMap())
            .matchedDefinition(definition)
            .build();
    }

    public static IntentCommand keywordHit(String intentCode, String asrText, String source) {
        return keywordHit(intentCode, asrText, source, null);
    }

    public static IntentCommand vectorHit(String intentCode, double score, String source, IntentDefinition definition) {
        return IntentCommand.builder()
            .intentCode(intentCode)
            .source(source != null ? source : "vector")
            .classifyMode("vector")
            .confidence(score)
            .matchDetail(String.format("向量相似度: %.3f", score))
            .valid(true)
            .reason(String.format("向量匹配成功 (score: %.3f)", score))
            .slots(Collections.emptyMap())
            .matchedDefinition(definition)
            .build();
    }

    public static IntentCommand vectorHit(String intentCode, double score, String source) {
        return vectorHit(intentCode, score, source, null);
    }

    public static IntentCommand llmResult(String intentCode, String source, IntentDefinition definition) {
        return IntentCommand.builder()
            .intentCode(intentCode)
            .source(source != null ? source : "llm")
            .classifyMode("llm")
            .confidence(0.85)
            .matchDetail("LLM 推理")
            .valid(true)
            .reason("LLM 分类决策")
            .slots(Collections.emptyMap())
            .matchedDefinition(definition)
            .build();
    }

    public static IntentCommand llmResult(String intentCode, String source) {
        return llmResult(intentCode, source, null);
    }

    public static IntentCommand noMatch(String source) {
        return IntentCommand.builder()
            .intentCode(null)
            .source(source != null ? source : "none")
            .classifyMode(source != null ? source : "none")
            .confidence(0.0)
            .valid(false)
            .reason("未匹配任何意图")
            .slots(Collections.emptyMap())
            .matchedDefinition(null)
            .errorMessage("无法识别该指令")
            .build();
    }

    public static IntentCommand noMatch(String source, String errorMessage) {
        return IntentCommand.builder()
            .intentCode(null)
            .source(source != null ? source : "none")
            .classifyMode(source != null ? source : "none")
            .confidence(0.0)
            .valid(false)
            .reason("未匹配任何意图")
            .slots(Collections.emptyMap())
            .matchedDefinition(null)
            .errorMessage(errorMessage != null ? errorMessage : "无法识别该指令")
            .build();
    }

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

    // ================================================================
    //  判断方法
    // ================================================================

    /**
     * ★ 显式覆盖 Lombok 为 {@code private boolean valid;} 自动生成的 {@code isValid()}。
     *
     * <p>之所以必须显式定义：{@code @Data} 默认生成的 getter 仅返回 {@code valid} 字段本身，
     * 并不能保证 {@code intentCode} 与 {@code matchedDefinition} 同时可用。而下游
     * （如 {@code SessionService.executeCommandSync} 的调试日志、
     * {@code IntelligentCommandProcessor} 的实现类）会直接调用
     * {@code command.getMatchedDefinition().getNameCn()}，一旦上游工厂方法
     * 传入 definition = null 却将 {@code valid} 置为 true，就会出现
     * "isValid() 通过但 matchedDefinition 为 null" 的半有效状态，进而抛 NPE。
     *
     * <p>只有同时满足以下三个条件才视为“真正有效”：
     * <ul>
     *   <li>{@code valid} 标记为 true</li>
     *   <li>{@code intentCode} 非空</li>
     *   <li>{@code matchedDefinition} 已成功绑定</li>
     * </ul>
     *
     * <p>注意：Lombok 在检测到同名方法已存在时不会重复生成，因此此处的方法会覆盖默认实现。
     * 同时，本方法未加 {@code @JsonIgnore}，以保持原本 {@code valid} 字段的序列化行为不变。
     */
    public boolean isValid() {
        return valid && intentCode != null && matchedDefinition != null;
    }

    @JsonIgnore
    public boolean isKeywordHit() {
        return valid && "keyword".equals(source);
    }

    @JsonIgnore
    public boolean isVectorHit() {
        return valid && "vector".equals(source);
    }

    @JsonIgnore
    public boolean isLlmResult() {
        return valid && "llm".equals(source);
    }

    @JsonIgnore
    public boolean isMatched() {
        return valid && intentCode != null;
    }

    public String getTopCandidateCode() {
        return (candidates != null && !candidates.isEmpty()) ? candidates.get(0).getIntentCode() : null;
    }

    // ================================================================
    //  参数访问方法（过滤 null）
    // ================================================================

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

    /**
     * 格式化意图和参数（仅用于日志/调试）
     */
    public String formatIntentAndParameters() {
        if (!valid) {
            return "无效意图: " + (errorMessage != null ? errorMessage : "未知错误");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("意图代码: ").append(intentCode != null ? intentCode : "未知");
        if (matchedDefinition != null) {
            sb.append(", 意图名称: ").append(matchedDefinition.getNameCn());
        }
        if (source != null) {
            sb.append(", 分类来源: ").append(source);
        }
        if (matchDetail != null) {
            sb.append(", 匹配详情: ").append(matchDetail);
        }
        if (confidence != null) {
            sb.append(", 置信度: ").append(String.format("%.2f", confidence));
        }
        sb.append(", 参数: ");
        if (slots != null && !slots.isEmpty()) {
            sb.append(slots.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}")));
        } else {
            sb.append("无");
        }
        return sb.toString();
    }
}

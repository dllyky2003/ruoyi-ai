package com.ruoyi.ems.intent.classifier;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.ActTypeRule;
import com.ruoyi.ems.intent.model.IntentDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 级联意图分类器：路由匹配 → 关键词 → 向量 → LLM 兜底
 *
 * <p><b>改造要点：</b>
 * <ul>
 *   <li>命中后统一走 {@link #resolveActType} 解析 actType</li>
 *   <li>优先级：元语用 > 意图 act_type_rules > act 字段映射 > 全局问句兜底 > COMMAND</li>
 *   <li>actType 统一存枚举名（VETO / STRATEGY_CORRECTION / CLOSED_LOOP /
 *       COMMAND / PROBE / ANNOUNCEMENT），下游按枚举名判断</li>
 *   <li>意图默认语用由代码内 {@link #ACT_DEFAULT_ACT_TYPE} 维护，不依赖配置文件</li>
 * </ul>
 *
 * <p><b>失败诊断设计：</b>
 * 本类不直接写失败日志。前三层全部未命中时，把各层诊断信息放入
 * {@link IntentCommand#getErrorMessage()} 并返回 {@code noMatch}，
 * 由 {@code IntentClassificationService} 在最终分类失败时统一记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CascadeIntentClassifier implements IntentClassifier {

    private final RouteIntentClassifier routeClassifier;
    private final KeywordIntentClassifier keywordClassifier;
    private final VectorIntentClassifier vectorClassifier;
    private final PragmaticClassifier classifier;
    private final CaseIntentDefinitionsLoader intentRegistry;
    private final NormalizerClassifier normalizerClassifier;
    private final SentencePragmaticAnalyzer sentencePragmaticAnalyzer;
    /** 全局问句兜底：用于意图无默认语用时判定 PROBE */
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "[?？]$|吗$|呢$|有没有|是否|多少|怎么样|对不对|如何"
    );

    /**
     * 意图动作类型 → 默认语用类型 的映射。
     * 代码内维护，不依赖配置文件。
     *
     * <p>映射依据：
     * <ul>
     *   <li>评估、检查、测量、操作、治疗类 → COMMAND（发令要求执行）</li>
     *   <li>询问病史类 → PROBE（探查性询问）</li>
     *   <li>记录、交接、报告类 → ANNOUNCEMENT（陈述已完成/进行中）</li>
     * </ul>
     */
    private static final Map<String, String> ACT_DEFAULT_ACT_TYPE = Map.ofEntries(
        Map.entry("assess_check",            ActType.COMMAND.name()),
        Map.entry("inquire_history",         ActType.PROBE.name()),
        Map.entry("measure_vital",           ActType.COMMAND.name()),
        Map.entry("prepare_equipment",       ActType.COMMAND.name()),
        Map.entry("device_operate",          ActType.COMMAND.name()),
        Map.entry("patient_manipulate",      ActType.COMMAND.name()),
        Map.entry("explain_communicate",     ActType.COMMAND.name()),
        Map.entry("record_document",         ActType.ANNOUNCEMENT.name()),
        Map.entry("request_call",            ActType.COMMAND.name()),
        Map.entry("safety_clear",            ActType.COMMAND.name()),
        Map.entry("treatment_intervention",  ActType.COMMAND.name()),
        Map.entry("interpret_report",        ActType.ANNOUNCEMENT.name()),
        Map.entry("transport_move",          ActType.COMMAND.name()),
        Map.entry("secure_fix",              ActType.COMMAND.name()),
        Map.entry("notify_activate",         ActType.COMMAND.name()),
        Map.entry("handoff_report",          ActType.ANNOUNCEMENT.name())
    );

    @Override
    public void warmUp() {
        try {
            routeClassifier.warmUp();
        } catch (Exception e) {
            log.warn("RouteIntentClassifier.warmUp 失败", e);
        }
    }

    @Override
    public String getMode() {
        return "cascade";
    }
    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {

        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.noMatch(getMode(), "语音文本为空");
        }

        // 元语用预测
        ActTypePrediction metaPrediction = classifier.predict(asrText);

        // ==================== 元语用短路 ====================
        if (metaPrediction != null
            && metaPrediction.actType() != null
            && metaPrediction.confidence() >= 0.85) {

            String metaCode = "META_" + metaPrediction.actType().name();
            String metaActType = metaPrediction.actType().name();

            log.debug("级联短路：元语用命中 {} ({}), 置信度={}",
                metaActType, metaPrediction.reason(), metaPrediction.confidence());

            IntentDefinition metaDef = new IntentDefinition();
            metaDef.setIntentCode(metaCode);
            metaDef.setNameCn("元语用: " + metaActType);
            metaDef.setActType(metaActType);

            return IntentCommand.builder()
                .intentCode(metaCode)
                .confidence(metaPrediction.confidence())
                .actType(metaActType)
                .reason(metaPrediction.reason())
                .valid(true)
                .classifyMode("PRAGMATIC")
                .source("pragmatic")
                .slots(java.util.Collections.emptyMap())
                .matchedDefinition(metaDef)
                .rawText(asrText)
                .build();
        }

        // ---- 用于失败汇总的诊断信息 ----
        String routeDiag      = null;
        String normalizerDiag = null;
        String keywordDiag    = null;
        String vectorDiag     = null;

        // ==================== 第 1 层：路由匹配 ====================
        try {
            IntentCommand routeResult = routeClassifier.classify(asrText, context);
            if (routeResult != null && routeResult.isMatched()) {
                routeResult.setActType(resolveActType(asrText, routeResult, metaPrediction));
                return routeResult;
            }
            routeDiag = summarize(routeResult);
        } catch (Exception e) {
            routeDiag = "异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("路由分类层异常: {}", e.getMessage());
        }
        log.debug("级联第 1 层（路由）未命中: {}", routeDiag);

        // ==================== 第 1.5 层：词典归一化匹配 ====================
        try {
            IntentCommand normalizerResult = normalizerClassifier.classify(asrText, context);
            if (normalizerResult != null && normalizerResult.isMatched()) {
                normalizerResult.setActType(resolveActType(asrText, normalizerResult, metaPrediction));
                return normalizerResult;
            }
            normalizerDiag = summarize(normalizerResult);
        } catch (Exception e) {
            normalizerDiag = "异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("词典归一化层异常: {}", e.getMessage());
        }
        log.debug("级联第 1.5 层（词典）未命中: {}", normalizerDiag);

        // ==================== 第 2 层：关键词匹配 ====================
        try {
            IntentCommand keywordResult = keywordClassifier.classify(asrText, context);
            if (keywordResult != null && keywordResult.isMatched()) {
                keywordResult.setActType(resolveActType(asrText, keywordResult, metaPrediction));
                return keywordResult;
            }
            keywordDiag = summarize(keywordResult);
        } catch (Exception e) {
            keywordDiag = "异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("关键词分类层异常: {}", e.getMessage());
        }
        log.debug("级联第 2 层（关键词）未命中: {}", keywordDiag);

        // ==================== 第 3 层：向量匹配 ====================
        try {
            IntentCommand vectorResult = vectorClassifier.classify(asrText, context);
            if (vectorResult != null && vectorResult.isMatched()) {
                vectorResult.setActType(resolveActType(asrText, vectorResult, metaPrediction));
                return vectorResult;
            }
            vectorDiag = summarize(vectorResult);
        } catch (Exception e) {
            vectorDiag = "异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("向量分类层异常: {}", e.getMessage());
        }
        log.debug("级联第 3 层（向量）未命中: {}", vectorDiag);

        // ==================== 全部未命中 ====================
        String diag = String.format(
            "路由/词典/关键词/向量均未命中:\n" +
                "  route=%s\n" +
                "  normalizer=%s\n" +
                "  keyword=%s\n" +
                "  vector=%s",
            safe(routeDiag), safe(normalizerDiag), safe(keywordDiag), safe(vectorDiag));

        IntentCommand noMatch = IntentCommand.noMatch(getMode(), diag);

        if (metaPrediction != null && metaPrediction.actType() != null) {
            noMatch.setActType(metaPrediction.actType().name());
        }
        return noMatch;
    }

    // ==========================================================
    //  actType 解析
    // ==========================================================

    /**
     * 解析最终 actType。
     *
     * <p>优先级（从高到低）：
     * <ol>
     *   <li>元语用规则命中（VETO / STRATEGY_CORRECTION / CLOSED_LOOP）</li>
     *   <li>意图级 act_type_rules（若配置里存在，按序匹配，命中即返回）</li>
     *   <li>{@link #ACT_DEFAULT_ACT_TYPE} 按 intent.act 映射（代码内维护）</li>
     *   <li>意图级 default_act_type（若配置里存在，作为可选覆盖）</li>
     *   <li>全局问句兜底（命中问句 → PROBE）</li>
     *   <li>兜底 COMMAND</li>
     * </ol>
     *
     * @return 枚举名字符串（VETO / COMMAND / PROBE / ...）
     */
    private String resolveActType(String text,
                                  IntentCommand cmd,
                                  ActTypePrediction metaPrediction) {
        // 1. 元语用优先
        if (metaPrediction != null && metaPrediction.actType() != null) {
            return metaPrediction.actType().name();
        }

        // 拿意图定义
        IntentDefinition def = cmd.getMatchedDefinition();
        if (def == null && cmd.getIntentCode() != null && intentRegistry != null) {
            def = intentRegistry.getIntent(cmd.getIntentCode());
            if (def != null) {
                cmd.setMatchedDefinition(def);
            }
        }

        // 2. 意图级 act_type_rules（精细控制）
        if (def != null && def.getActTypeRules() != null) {
            for (ActTypeRule r : def.getActTypeRules()) {
                if (r == null || r.getPattern() == null || r.getActType() == null) continue;
                try {
                    if (Pattern.compile(r.getPattern()).matcher(text).find()) {
                        return r.getActType();
                    }
                } catch (Exception e) {
                    log.warn("act_type_rules pattern 非法: {}", r.getPattern());
                }
            }
        }

        // ========== ★ 2.5 句子级语用分析（新增） ==========
        ActType sentenceActType = sentencePragmaticAnalyzer.analyze(text);
        if (sentenceActType != null) {
            log.debug("[actType] 句子级判定: text='{}', actType={}", text, sentenceActType);
            return sentenceActType.name();
        }

        // 3. 从 act 字段映射（代码内维护）
        if (def != null && def.getAct() != null) {
            String mapped = ACT_DEFAULT_ACT_TYPE.get(def.getAct());
            if (mapped != null) {
                return mapped;
            }
        }

        // 4. 意图级 default_act_type
        if (def != null && def.getDefaultActType() != null
            && !def.getDefaultActType().isBlank()) {
            return def.getDefaultActType();
        }

        // 5. 全局问句兜底（保留，作为最后一道）
        if (looksLikeQuestion(text)) {
            return ActType.PROBE.name();
        }

        // 6. 兜底
        return ActType.COMMAND.name();
    }

    /**
     * 全局问句判定，用于意图无默认语用时的 PROBE 兜底。
     */
    private boolean looksLikeQuestion(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return QUESTION_PATTERN.matcher(text).find();
    }

    // ==========================================================
    //  诊断信息
    // ==========================================================

    /**
     * 从 IntentCommand 提取简短诊断信息，用于失败汇总。
     */
    private String summarize(IntentCommand cmd) {
        if (cmd == null) {
            return "null";
        }
        if (cmd.getErrorMessage() != null) {
            return cmd.getErrorMessage();
        }
        if (cmd.getIntentCode() != null) {
            return "code=" + cmd.getIntentCode() + " (未通过 matched 校验)";
        }
        return "未命中";
    }

    private String safe(String s) {
        return s != null ? s : "-";
    }
}

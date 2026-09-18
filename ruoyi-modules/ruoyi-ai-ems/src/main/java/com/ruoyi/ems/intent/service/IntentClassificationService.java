package com.ruoyi.ems.intent.service;

import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.classifier.*;
import com.ruoyi.ems.intent.config.IntentClassifierProperties;
import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.intent.utils.SentenceSplitter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class IntentClassificationService {

    @Autowired
    private CascadeIntentClassifier cascadeIntentClassifier;

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
    private EmsContextManager contextManager;

    @Autowired
    private SentenceSplitter sentenceSplitter;


    /** ★ 统一失败记录器（本服务是唯一写入方） */
    @Autowired
    private UnmatchedAsrRecorder unmatchedAsrRecorder;



    @PostConstruct
    public void warmUp() {
        log.info("🔥 开始预热 LLM 分类器（建立连接 & 加载模型）...");
        long start = System.currentTimeMillis();
        try {
            IntentClassifier classifier = selectClassifier();
            classifier.warmUp();
            log.info("✅ LLM 分类器预热完成，耗时 {}ms",
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("⚠️ LLM 预热失败（主流程不受影响）: {}", e.getMessage());
        }
    }

    /**
     * 相邻句预合并（进 LLM 前的纯规则处理）。
     *
     * <p>场景：分句把"放置并密封球囊面罩，按紧面罩"切成两句，但后者是前者的
     * 补充说明，语义上是同一个动作。若直接进 LLM，会调 2 次 LLM 拿到同一个意图。
     * 这里提前合并，能省一次 LLM 调用。
     *
     * <p>合并规则（任一命中即合并）：
     * <ol>
     *   <li>后句以补充动作词开头且较短（≤ 8 字）：做/按紧/用力/使劲/保持/贴紧/贴好/固定好/拧紧</li>
     *   <li>后句与"当前累积句"的 2-gram 重叠比例 ≥ 40%</li>
     *   <li>后句长度 ≤ 4 且包含前句中的名词片段</li>
     * </ol>
     *
     * <p>不合并的情况：
     * <ul>
     *   <li>后句过长（> 10 字），视为独立动作</li>
     *   <li>后句以"继续/再/接着/然后/检查/量/测/评估"等独立动作词开头</li>
     *   <li>前后句无字符级重叠且后句不以补充动词开头</li>
     * </ul>
     */
    private List<String> preMergeAdjacent(List<String> sentences) {
        if (sentences == null || sentences.size() <= 1) {
            return sentences;
        }

        List<String> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder(sentences.get(0));

        for (int i = 1; i < sentences.size(); i++) {
            String curr = sentences.get(i);
            String accumulated = buf.toString();

            if (shouldPreMerge(accumulated, curr)) {
                log.info("[预合并] '{}' + '{}'", accumulated, curr);
                buf.append("，").append(curr);
            } else {
                result.add(accumulated);
                buf = new StringBuilder(curr);
            }
        }
        result.add(buf.toString());

        if (result.size() < sentences.size()) {
            log.info("[预合并] {} 句 -> {} 句", sentences.size(), result.size());
        }
        return result;
    }

    /**
     * 判断后句是否应合并到前句。
     */
    private boolean shouldPreMerge(String prev, String curr) {
        if (prev == null || prev.isEmpty() || curr == null || curr.isEmpty()) {
            return false;
        }

        // 后句太长，视为独立动作
        if (curr.length() > 10) {
            return false;
        }

        // 后句以独立动作词开头，不合并
        if (curr.matches("^(继续|再|接着|然后|检查|量|测|评估|准备|给|推|通知|打开|关闭).*")) {
            return false;
        }

        // 规则 1：后句以补充动作词开头且较短
        if (curr.length() <= 8
            && curr.matches("^(做|按紧|用力|使劲|保持|贴紧|贴好|固定好|拧紧|按好).*")) {
            return true;
        }

        // 规则 2：2-gram 重叠比例 ≥ 40%
        Set<String> prevBg = bigrams(prev);
        Set<String> currBg = bigrams(curr);
        if (currBg.isEmpty()) {
            return false;
        }
        long shared = currBg.stream().filter(prevBg::contains).count();
        double ratio = (double) shared / currBg.size();
        if (ratio >= 0.4) {
            return true;
        }

        // 规则 3：后句极短（≤ 4 字）且与前句有 2 字以上的字符片段重叠
        if (curr.length() <= 4) {
            for (int len = 2; len <= curr.length(); len++) {
                for (int i = 0; i + len <= curr.length(); i++) {
                    if (prev.contains(curr.substring(i, i + len))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 提取字符串的所有 2-gram。
     */
    private Set<String> bigrams(String s) {
        Set<String> result = new HashSet<>();
        if (s == null || s.length() < 2) return result;
        for (int i = 0; i < s.length() - 1; i++) {
            result.add(s.substring(i, i + 2));
        }
        return result;
    }
    /**
     * 多句分类入口：先分句，再逐句走完整分类链路。
     *
     * <p>规则分句结果可疑时，可选走 LLM 兜底（暂不实现，预留判断）。
     * 每条返回的 IntentCommand 都带 sentenceIndex。
     */
    public List<IntentCommand> classifyMulti(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.isBlank()) {
            return Collections.emptyList();
        }
        // ============================================================
        // ★ 临时短路：跳过分句，整句直接走单句分类
        //   后续接回分句时删除本段即可
        // ============================================================
        if (true) {
            IntentCommand cmd = classify(asrText, context);
            if (cmd != null) {
                cmd.setSentenceIndex(0);
            }
            return cmd != null ? Collections.singletonList(cmd) : Collections.emptyList();
        }
        // ============================================================
        List<String> sentences = sentenceSplitter.split(asrText);
        log.info("分句结果（{} 句）: {}", sentences.size(), sentences);



        // ★ 新增：进 LLM 前先做相邻句预合并（纯规则，不调 LLM）
        sentences = preMergeAdjacent(sentences);
        if (sentences.size() < 2) {
            log.info("[预合并] 合并后剩余 {} 句: {}", sentences.size(), sentences);
        }

        if (sentences.size() == 1) {
            IntentCommand cmd = classify(sentences.get(0), context);
            if (cmd != null) cmd.setSentenceIndex(0);
            return Collections.singletonList(cmd);
        }

        List<IntentCommand> raw = new ArrayList<>(sentences.size());
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            try {
                IntentCommand cmd = classify(sentence, context);
                if (cmd != null) cmd.setSentenceIndex(i);
                raw.add(cmd);
            } catch (Exception e) {
                log.error("第 {} 句分类失败: '{}'", i, sentence, e);
                IntentCommand failed = IntentCommand.invalid(sentence, "分类异常: " + e.getMessage());
                failed.setSentenceIndex(i);
                raw.add(failed);
            }
        }

        // 后去重作为安全网（预合并没覆盖的情况，兜底）
        return deduplicateSameIntent(raw);
    }

    /**
     * 连续同意图去重。
     *
     * <p>场景：分句把"打开气道，做仰头抬颏"切成两句，但两句都命中 IA_057。
     * 语义上它们是同一个动作的两种表达，应该只执行一次。
     *
     * <p>去重规则：
     * <ul>
     *   <li>只对**连续**两条意图码相同、actType 相同的命令去重</li>
     *   <li>元语用（VETO / CLOSED_LOOP / STRATEGY_CORRECTION）不去重</li>
     *   <li>合并时把被去掉的句子文本拼到保留句的 rawText 上</li>
     *   <li>去重后重排 sentenceIndex</li>
     * </ul>
     */
    private List<IntentCommand> deduplicateSameIntent(List<IntentCommand> commands) {
        if (commands == null || commands.size() <= 1) {
            return commands;
        }

        List<IntentCommand> result = new ArrayList<>();
        for (IntentCommand cmd : commands) {
            if (cmd == null) continue;

            if (!result.isEmpty()) {
                IntentCommand prev = result.get(result.size() - 1);
                if (isSameIntent(prev, cmd)) {
                    String prevText = prev.getRawText();
                    String mergedText = (prevText != null ? prevText : "")
                        + "，" + (cmd.getRawText() != null ? cmd.getRawText() : "");
                    prev.setRawText(mergedText);
                    log.info("[分句去重] 合并重复意图: {} ('{}' + '{}')",
                        prev.getIntentCode(), prevText, cmd.getRawText());
                    continue;
                }
            }
            result.add(cmd);
        }

        // 重排 sentenceIndex
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setSentenceIndex(i);
        }

        log.info("[分句去重] {} 条 -> {} 条", commands.size(), result.size());
        return result;
    }

    /**
     * 判断两条命令是否为同一意图。
     *
     * <p>严格条件：
     * <ul>
     *   <li>两条 intentCode 都非空且相等</li>
     *   <li>两条 actType 相等</li>
     *   <li>都不属于元语用（VETO / CLOSED_LOOP / STRATEGY_CORRECTION）</li>
     * </ul>
     */
    private boolean isSameIntent(IntentCommand a, IntentCommand b) {
        if (a == null || b == null) return false;
        if (a.getIntentCode() == null || b.getIntentCode() == null) return false;
        if (!a.getIntentCode().equals(b.getIntentCode())) return false;

        String act = a.getActType();
        if ("VETO".equals(act)
            || "CLOSED_LOOP".equals(act)
            || "STRATEGY_CORRECTION".equals(act)) {
            return false;
        }

        return java.util.Objects.equals(a.getActType(), b.getActType());
    }
    // ==================== 核心分类方法 ====================

    public boolean prepareForStart(String sessionId) {
        return sessionId != null && !sessionId.trim().isEmpty();
    }

    /**
     * 意图分类（带上下文）。
     *
     * <p>流程：
     * <ol>
     *   <li>级联分类器（路由 + 关键词 + 向量）优先执行</li>
     *   <li>级联未命中 → LLM 分类器（one-stage 或 two-stage）</li>
     *   <li>LLM 未命中 → 兜底分类器</li>
     *   <li><b>全部失败</b> → 统一记录一条 {@code ALL_FAILED} 记录到
     *       {@link UnmatchedAsrRecorder}</li>
     * </ol>
     *
     * <p>各分类器本身不再写失败日志，只把诊断信息放在
     * {@link IntentCommand#getErrorMessage()} 和
     * {@link IntentCommand#getLlmRawResponse()} 中传递上来。
     */
    /**
     * 意图分类（带上下文）。
     *
     * <p>流程：
     * <ol>
     *   <li>级联分类器（元语用短路 + 路由 + 词典 + 关键词 + 向量）优先执行</li>
     *   <li>元语用命中（META_*）直接返回，不走 enrichResult，避免 dummy definition 被覆盖</li>
     *   <li>级联未命中 → LLM 分类器（one-stage 或 two-stage）</li>
     *   <li>LLM 未命中 → 兜底分类器</li>
     *   <li><b>全部失败</b> → 统一记录一条 {@code ALL_FAILED} 记录到
     *       {@link UnmatchedAsrRecorder}</li>
     * </ol>
     */
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return IntentCommand.invalid("", "语音文本为空");
        }

        //log.info("开始意图分类，级联前置模式: {}, 文本: {}", properties.getMode(), asrText);

        // ============================================================
        // 第一步：级联分类器（元语用短路 + 路由 + 词典 + 关键词 + 向量）
        // ============================================================
        IntentCommand cascadeResult = cascadeIntentClassifier.classify(asrText, context);

        if (cascadeResult != null && cascadeResult.isMatched()) {

            // ★ 元语用命中（META_*）：直接返回，不走 enrichResult
            // 原因：CascadeIntentClassifier 塞的 dummy IntentDefinition 会被
            //      enrichResult 的 intentRegistry.getIntent(META_*) 覆盖为 null，
            //      导致 isValid() 返回 false，整条指令被丢弃。
            String code = cascadeResult.getIntentCode();
            if (code != null && code.startsWith("META_")) {
                cascadeResult.setRawText(asrText);
                cascadeResult.setClassifyMode("pragmatic");
                //log.info("元语用命中: code={}, actType={}, confidence={}",
                //    code, cascadeResult.getActType(), cascadeResult.getConfidence());
                return cascadeResult;
            }

            IntentCommand enhanced = enrichResult(cascadeResult, asrText);
            enhanced.setClassifyMode("cascade");
            return enhanced;
        }

        //log.info("级联分类未命中，进入 LLM 分类模式: {}", properties.getMode());

        // ============================================================
        // 第二步：LLM 分类器（one-stage 或 two-stage）
        // ============================================================
        IntentClassifier classifier = selectClassifier();
        IntentCommand llmResult = classifier.classify(asrText, context);

        if (llmResult == null) {
            llmResult = IntentCommand.invalid(asrText, "分类器返回空结果");
        }

        llmResult.setClassifyMode(properties.getMode());
        IntentCommand enhancedResult = enrichResult(llmResult, asrText);

        // ============================================================
        // 第三步：兜底分类器（LLM 失败且允许兜底时）
        // ============================================================
        IntentCommand fallbackResult = null;
        if (!enhancedResult.isValid() && properties.isFallbackOnError()) {
            log.info("LLM 分类失败，启用兜底分类器: {}", enhancedResult.getErrorMessage());
            fallbackResult = fallbackClassifier.classify(asrText, context);
            if (fallbackResult != null) {
                fallbackResult.setClassifyMode("fallback");
            }
            enhancedResult = enrichResult(fallbackResult, asrText);
        }

        // ============================================================
        // 第四步：最终失败 → 统一记录（一次、一条记录）
        // ============================================================
        if (enhancedResult == null || !enhancedResult.isValid()) {
            recordUnifiedFailure(asrText, context, cascadeResult, llmResult, fallbackResult);
        }

        if (enhancedResult != null) {
            log.info("LLM 分类完成: code={}, mode={}, confidence={}",
                enhancedResult.getIntentCode(),
                enhancedResult.getClassifyMode(),
                enhancedResult.getConfidence());
        }

        return enhancedResult != null
            ? enhancedResult
            : IntentCommand.invalid(asrText, "所有分类器均失败");
    }

    /**
     * 原有分类方法（兼容，不带上下文）。
     */
    public IntentCommand classify(String asrText) {
        return classify(asrText, null);
    }

    // ==================== 统一失败记录 ====================

    /**
     * 把"级联 + LLM + 兜底"三个阶段的失败诊断汇总，一次性写入日志。
     *
     * <p>诊断来源：
     * <ul>
     *   <li>{@code cascadeResult.getErrorMessage()}  —— 级联三层诊断</li>
     *   <li>{@code llmResult.getErrorMessage()}      —— LLM 失败原因</li>
     *   <li>{@code llmResult.getLlmRawResponse()}    —— LLM 原始响应</li>
     *   <li>{@code fallbackResult.getErrorMessage()} —— 兜底失败原因</li>
     * </ul>
     */
    private void recordUnifiedFailure(String asrText, ClassificationContext context,
                                      IntentCommand cascadeResult,
                                      IntentCommand llmResult,
                                      IntentCommand fallbackResult) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("所有分类器均失败:\n");

            // 1. 级联分类器
            sb.append("[级联分类器]\n");
            if (cascadeResult != null && cascadeResult.getErrorMessage() != null) {
                sb.append(indent(cascadeResult.getErrorMessage(), "  ")).append("\n");
            } else {
                sb.append("  (无诊断信息)\n");
            }

            // 2. LLM 分类器
            sb.append("[LLM 分类器 ").append(properties.getMode()).append("]\n");
            if (llmResult != null && llmResult.getErrorMessage() != null) {
                sb.append(indent(llmResult.getErrorMessage(), "  ")).append("\n");
            } else {
                sb.append("  (无诊断信息)\n");
            }

            // 3. 兜底分类器
            sb.append("[兜底分类器]\n");
            if (fallbackResult != null && fallbackResult.getErrorMessage() != null) {
                sb.append(indent(fallbackResult.getErrorMessage(), "  ")).append("\n");
            } else {
                sb.append("  (未启用或未命中)\n");
            }

            // 提取 LLM 原始响应（上游 TwoStageClassifier 会填这个字段）
            String llmRaw = (llmResult != null) ? llmResult.getLlmRawResponse() : null;

            // 提取 actType（优先取 LLM 结果，其次级联）
            String actType = null;
            if (llmResult != null && llmResult.getActType() != null) {
                actType = llmResult.getActType();
            } else if (cascadeResult != null && cascadeResult.getActType() != null) {
                actType = cascadeResult.getActType();
            }

            unmatchedAsrRecorder.recordAllFailed(
                asrText, context, sb.toString(), actType, llmRaw);

            log.warn("所有分类器均失败，已记录到日志: {}", asrText);

        } catch (Exception e) {
            // 记录失败不能影响主流程
            log.warn("统一记录分类失败信息时异常: {}", e.getMessage());
        }
    }

    /** 对多行文本逐行加前缀（缩进），跳过空行不额外加前缀的语义保持。 */
    private String indent(String text, String prefix) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(prefix).append(lines[i]);
        }
        return sb.toString();
    }

    // ==================== 其他方法 ====================

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

    // ==================== 私有方法 ====================

    private IntentCommand enrichResult(IntentCommand raw, String rawText) {
        if (raw == null) {
            return IntentCommand.invalid(rawText, "分类器返回空结果");
        }

        raw.setRawText(rawText);

        // 处理候选列表
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

        // 排序
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

        // 填充意图定义和子域
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

    /**
     * 选择分类器（根据配置的 mode）。
     * 注意：此方法仅在级联未命中时调用，作为 LLM 兜底。
     */
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

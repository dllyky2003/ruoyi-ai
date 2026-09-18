package com.ruoyi.ems.engine.extraction;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.classifier.ActTypePrediction;
import com.ruoyi.ems.intent.classifier.RuleBasedPragmaticClassifier;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.service.IntentClassificationService;
import com.ruoyi.ems.intent.utils.IntentDescriptionProvider;
import com.ruoyi.ems.intent.utils.ParameterExtractor;
import com.ruoyi.ems.intent.utils.SentenceSplitter;
import com.ruoyi.ems.intent.utils.VariableDescriptionProvider;
import com.ruoyi.ems.npc.TeamManager;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ProcessTranscriptor {

    private final IntentClassificationService intentService;
    private final ParameterExtractor parameterExtractor;
    private final EmsContextManager contextManager;
    private final YamlStateMachineLoader configLoader;
    private final VariableDescriptionProvider variableDescriptionProvider;
    private final IntentDescriptionProvider intentDescriptionProvider;
    private final CaseIntentDefinitionsLoader caseIntentDefinitionsLoader;
    private final TeamManager teamManager;
    private final SentenceSplitter sentenceSplitter;
    private final RuleBasedPragmaticClassifier pragmaticClassifier;

    public ProcessTranscriptor(IntentClassificationService intentService,
                               ParameterExtractor parameterExtractor,
                               EmsContextManager contextManager,
                               YamlStateMachineLoader configLoader,
                               VariableDescriptionProvider variableDescriptionProvider,
                               IntentDescriptionProvider intentDescriptionProvider,
                               CaseIntentDefinitionsLoader caseIntentDefinitionsLoader,
                               TeamManager teamManager,
                               SentenceSplitter sentenceSplitter,
                               RuleBasedPragmaticClassifier pragmaticClassifier) {
        this.intentService = intentService;
        this.parameterExtractor = parameterExtractor;
        this.contextManager = contextManager;
        this.configLoader = configLoader;
        this.variableDescriptionProvider = variableDescriptionProvider;
        this.intentDescriptionProvider = intentDescriptionProvider;
        this.caseIntentDefinitionsLoader = caseIntentDefinitionsLoader;
        this.teamManager = teamManager;
        this.sentenceSplitter = sentenceSplitter;
        this.pragmaticClassifier = pragmaticClassifier;

    }

    public boolean prepareForStart(String sessionId) {
        return intentService.prepareForStart(sessionId);
    }

    // ==========================================================
    // 多意图入口：分句 → 逐句分类
    // ==========================================================

    /**
     * 处理一段 ASR 文本（走 Session 通道，用于 ASR WebSocket）。
     *
     * <p>与 {@link #processTranscript} 一样，本方法只做“理解”，
     * 绝不修改任何临床变量，也不驱动状态机。</p>
     *
     * @deprecated 建议调用 {@link #processTranscriptMulti(String, String, String)}，
     *             直接传 sessionId，避免 Session 为 null 时参数抽取被跳过。
     */
    @Deprecated
    public List<IntentCommand> processTranscriptMulti(Session session,
                                                      String transcript,
                                                      String caseId) {
        if (transcript == null || transcript.isBlank()) {
            return Collections.emptyList();
        }

        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            log.warn("⚠️ processTranscriptMulti(Session): session 为 null 或缺少 sessionId，参数抽取将被跳过");
        }
        return doProcessTranscriptMulti(sessionId, transcript, caseId);
    }

    /**
     * ★ 新增重载：直接传 sessionId，绕开 Session 依赖。
     *
     * <p>用于非 ASR 通道（如 Unity 引擎上行 asr_transcript），
     * 此时没有 jakarta.websocket.Session，但已经有 emsSessionId。</p>
     */
    public List<IntentCommand> processTranscriptMulti(String sessionId,
                                                      String transcript,
                                                      String caseId) {
        if (transcript == null || transcript.isBlank()) {
            return Collections.emptyList();
        }
        return doProcessTranscriptMulti(sessionId, transcript, caseId);
    }

    // ==========================================================
    // 私有：多意图处理核心（两个重载共用）
    // ==========================================================

    private List<IntentCommand> doProcessTranscriptMulti(String sessionId,
                                                         String transcript,
                                                         String caseId) {
        CaseContext caseContext = sessionId != null ? contextManager.getSnapshot(sessionId) : null;

        ClassificationContext classificationContext = null;
        if (caseContext != null && caseId != null) {
            classificationContext = buildClassificationContext(caseContext, caseId);
        }

        List<IntentCommand> results = intentService.classifyMulti(transcript, classificationContext);

        for (IntentCommand cmd : results) {
            if (cmd == null) continue;

            // 1. 回填 matchedDefinition
            if (cmd.getIntentCode() != null && cmd.getMatchedDefinition() == null) {
                IntentDefinition def = caseIntentDefinitionsLoader.getIntent(cmd.getIntentCode());
                if (def != null) {
                    cmd.setMatchedDefinition(def);
                }
            }

            // 2. 参数抽取 + 临时写回
            if (cmd.isValid() && sessionId != null) {
                doExtractParametersAndWriteback(cmd, sessionId);
            }
        }

        return results;
    }

    /**
     * 参数抽取 + 写回上下文（两个重载共用）。
     *
     * <p>★ 临时方案：Unity 未完全接入前，参数抽取结果直接写回上下文，
     * 让状态机的条件出边（variable-driven）能感知到变量变更。
     * Unity 接入后，此段应删除，改由 Unity NPC 执行完成回传变量。</p>
     */
    private void doExtractParametersAndWriteback(IntentCommand cmd, String sessionId) {
        try {
            Map<String, Object> extracted = parameterExtractor.extract(cmd, sessionId);
            if (extracted.isEmpty()) {
                log.debug("[参数抽取-多意图] intent={} 无参数产出", cmd.getIntentCode());
                return;
            }

            // ★ 填入 IntentCommand.slots，供后续 skill / Unity 使用
            cmd.setSlots(extracted);

            // ★ 写回上下文
            CaseContext latest = contextManager.getSnapshot(sessionId);
            if (latest != null) {
                contextManager.batchSetVarsWithVersion(
                    sessionId, extracted, "PARAM_EXTRACTOR", latest.getVersion());
            }

            log.info("[参数抽取-多意图] intent={}, extracted={}",
                cmd.getIntentCode(), extracted);

        } catch (Exception e) {
            log.error("[参数抽取-多意图] 失败 intent={}", cmd.getIntentCode(), e);
        }
    }

    // ==========================================================
    // 单句分类：元语用规则 → 意图链路 → 回填 actType
    // ==========================================================

    private IntentCommand processSingleSentence(Session session,
                                                String sentence,
                                                String caseId) {
        // 1. 元语用规则（VETO / STRATEGY_CORRECTION / CLOSED_LOOP）
        ActTypePrediction meta = pragmaticClassifier.predict(sentence);
        if (meta != null) {
            IntentCommand metaCmd = new IntentCommand();
            metaCmd.setRawText(sentence);
            metaCmd.setActType(meta.actType().name());
            metaCmd.setConfidence(meta.confidence());
            metaCmd.setClassifySource("meta-rule");
            // 元语用不关联具体意图，但保留上一轮意图供下游判断
            // （下游可根据 actType 决定是否回滚/确认上一个动作）
            log.debug("[ProcessTranscriptor] 元语用命中: '{}' -> {}",
                sentence, meta.actType());
            return metaCmd;
        }

        // 2. 原有意图分类链路（关键词 → 向量 → LLM）
        IntentCommand cmd = processTranscriptInternal(session, sentence, caseId);
        if (cmd == null) {
            return null;
        }

        // 3. 从意图定义带出 defaultActType
        if (cmd.getMatchedDefinition() != null
            && cmd.getMatchedDefinition().getDefaultActType() != null) {
            cmd.setActType(cmd.getMatchedDefinition().getDefaultActType());
        } else if (cmd.getActType() == null) {
            cmd.setActType("COMMAND");   // 兜底
        }
        if (cmd.getClassifySource() == null) {
            cmd.setClassifySource("intent-cascade");
        }

        return cmd;
    }

    // ==========================================================
    // 【保留】单句入口（向后兼容）
    // ==========================================================

    /**
     * 兼容旧调用方：单句入口。
     * 内部逻辑与改造前一致，行为不变。
     */
    public IntentCommand processTranscript(Session session, String transcript, String caseId) {
        return processTranscriptInternal(session, transcript, caseId);
    }

    // ==========================================================
    // 内部：原 processTranscript 的全部逻辑
    // ==========================================================

    private IntentCommand processTranscriptInternal(Session session,
                                                    String transcript,
                                                    String caseId) {
        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            log.warn("无法从 Session 获取 sessionId，降级为无上下文分类");
            return classifyWithoutContext(transcript);
        }

        CaseContext caseContext = contextManager.getSnapshot(sessionId);
        if (caseContext == null) {
            log.warn("会话 {} 的上下文不存在，降级为无上下文分类", sessionId);
            return classifyWithoutContext(transcript);
        }

        ClassificationContext classificationContext = buildClassificationContext(caseContext, caseId);

        IntentCommand intentResult = intentService.classify(transcript, classificationContext);

        // 回填 matchedDefinition
        if (intentResult != null
            && intentResult.getIntentCode() != null
            && intentResult.getMatchedDefinition() == null) {
            IntentDefinition def = caseIntentDefinitionsLoader.getIntent(intentResult.getIntentCode());
            if (def != null) {
                intentResult.setMatchedDefinition(def);
                log.debug("已回填意图定义: {} -> {}", intentResult.getIntentCode(), def.getNameCn());
            } else {
                log.warn("⚠️ 意图码 {} 未在字典中注册（caseId={}），请检查热加载是否覆盖该案例",
                    intentResult.getIntentCode(), caseId);
            }
        }

        // ★ 不再直接改 caseContext 字段，统一走语义入口
        boolean hasValidIntent = intentResult != null
            && intentResult.isValid()
            && intentResult.getIntentCode() != null;

        String historyCode = null;
        ClassificationContext.DialogTurn turn = null;

        if (hasValidIntent) {
            historyCode = intentResult.getIntentCode();
            turn = ClassificationContext.DialogTurn.builder()
                .userMessage(transcript)
                .intentCode(intentResult.getIntentCode())
                .systemResponse(intentResult.formatIntentAndParameters())
                .timestamp(System.currentTimeMillis())
                .build();
        }

        contextManager.updateConversationState(sessionId, intentResult, historyCode, turn);

        return intentResult;
    }

    private IntentCommand classifyWithoutContext(String transcript) {
        IntentCommand result = intentService.classify(transcript);

        if (result != null
            && result.getIntentCode() != null
            && result.getMatchedDefinition() == null) {
            IntentDefinition def = caseIntentDefinitionsLoader.getIntent(result.getIntentCode());
            if (def != null) {
                result.setMatchedDefinition(def);
            } else {
                log.warn("⚠️ [无上下文] 意图码 {} 未在字典中注册", result.getIntentCode());
            }
        }

        Map<String, Object> params = parameterExtractor.extract(result, null);
        result.setSlots(params);
        return result;
    }

    private String extractSessionId(Session session) {
        if (session == null) {
            return null;
        }
        Object id = session.getUserProperties().get("sessionId");
        return id != null ? id.toString() : null;
    }

    private ClassificationContext buildClassificationContext(CaseContext caseContext, String caseId) {
        ResolvedCaseTypeConfig config = configLoader.getConfig(caseId);
        if (config == null) {
            throw new RuntimeException("案例配置不存在: " + caseId);
        }

        String currentState = caseContext.getCurrentState();

        String currentStateDescription = config.getStateDescription(currentState);
        if (currentStateDescription == null) {
            currentStateDescription = currentState.replace("_", " ").toLowerCase();
        }

        Set<String> whitelist = config.buildPromptWhitelist(currentState);
        Set<String> globalEvents = config.getGlobalEventIntents();

        String lastIntentCode = null;
        IntentCommand lastResult = caseContext.getLastIntentResult();
        if (lastResult != null && lastResult.isValid()) {
            lastIntentCode = lastResult.getIntentCode();
        }

        List<String> historyCodes = caseContext.getRecentIntentHistory(10);
        List<String> historyDisplay = new ArrayList<>();
        for (String code : historyCodes) {
            String display = formatIntentDisplay(code);
            historyDisplay.add(display != null ? display : code);
        }

        String lastIntentDisplay = null;
        if (lastIntentCode != null) {
            lastIntentDisplay = formatIntentDisplay(lastIntentCode);
            if (lastIntentDisplay == null) {
                lastIntentDisplay = lastIntentCode;
            }
        }

        String variablesDescription = variableDescriptionProvider.buildDescription(caseId, caseContext);

        String availableIntentsDescription = intentDescriptionProvider.buildPromptDescription(whitelist);

        String sessionId = caseContext.getSessionId();
        String teamStatusSummary = "";
        try {
            teamStatusSummary = teamManager.getTeamStatusSummary(sessionId);
        } catch (Exception e) {
            log.warn("获取团队状态失败: {}", e.getMessage());
        }

        return ClassificationContext.builder()
            .caseId(caseId)
            .caseDisplayName(config.getCaseName())
            .sessionId(sessionId)
            .currentState(currentState)
            .currentStateDescription(currentStateDescription)
            .variables(caseContext.getVariablesSnapshot())
            .variablesDescription(variablesDescription)
            .availableIntentsDescription(availableIntentsDescription)
            .intentHistory(historyCodes)
            .intentHistoryDisplay(historyDisplay)
            .lastIntent(lastIntentCode)
            .lastIntentDisplay(lastIntentDisplay)
            .lastIntentResult(caseContext.getLastIntentResult())
            .scenario(caseContext.getCaseType())
            .availableIntents(whitelist)
            .globalEventIntents(globalEvents)
            .elapsedSeconds(caseContext.getElapsedSeconds())
            .clockStatus(caseContext.getClockStatus())
            .teamStatusSummary(teamStatusSummary)
            .build();
    }

    private String formatIntentDisplay(String intentCode) {
        if (intentCode == null) return null;
        IntentDefinition def = caseIntentDefinitionsLoader.getIntent(intentCode);
        if (def != null && def.getNameCn() != null) {
            return intentCode + "（" + def.getNameCn() + "）";
        }
        return null;
    }
}

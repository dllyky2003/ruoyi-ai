package com.ruoyi.ems.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.agent.ExecutorAgentService;
import com.ruoyi.ems.asr.model.AsrClient;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.config.loader.CaseLoaderService;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.config.loader.VariableMetaLoader;
import com.ruoyi.ems.domain.*;
import com.ruoyi.ems.domain.TeachingMode.TeachingMode;
import com.ruoyi.ems.domain.dto.CommandTask;
import com.ruoyi.ems.domain.dto.TeamPerformance;
import com.ruoyi.ems.domain.entity.EmsSession;
import com.ruoyi.ems.domain.entity.EmsScenario;
import com.ruoyi.ems.engine.PhaseManager;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.engine.extraction.ProcessTranscriptor;

import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.intent.service.IntentClassificationService;
import com.ruoyi.ems.intent.utils.ParameterExtractor;
import com.ruoyi.ems.intent.utils.TextCleaner;
import com.ruoyi.ems.mapper.EmsSessionMapper;
import com.ruoyi.ems.npc.TeamManager;

import com.ruoyi.ems.npc.TeamSize;
import com.ruoyi.ems.spi.IntelligentCommandProcessor;
import com.ruoyi.ems.tts.KokoroTtsClient;
import com.ruoyi.ems.websocket.WebSocketMessageSender;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 会话服务核心类，负责整个 EMS 演练的会话生命周期管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------- 依赖注入 --------------------
    private final AsrClient asrClient;
    private final EmsSessionMapper sessionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProcessTranscriptor processTranscriptService;
    private final ScenarioService scenarioService;
    private final IntentClassificationService intentClassificationService;
    private final ParameterExtractor parameterExtractor;
    private final StateMachineFacade stateMachineFacade;
    private final CaseIntentDefinitionsLoader intentRegistry;
    private final VariableMetaLoader variableMetaManager;
    private final YamlStateMachineLoader configLoader;
    private final EmsContextManager contextManager;
    private final TextCleaner textCleaner;
    private final KokoroTtsClient ttsClient;
    private final WebSocketMessageSender messageSender;
    private final CaseLoaderService caseLoader;

    private final TeamManager teamManager;
    private final com.ruoyi.ems.service.agent.PlannerAgentService plannerAgentService;
    private final ExecutorAgentService executorAgentService;
    private final ObjectProvider<IntelligentCommandProcessor> intelligentProcessorProvider;

    // ★ Phase SM
    private final PhaseManager phaseManager;

    // -------------------- 缓存与常量 --------------------

    private final Map<String, StateMachine<String, String>> stateMachineCache = new ConcurrentHashMap<>();

    private static final String SESSION_CONTEXT_PREFIX = "ems:session:context:";
    private static final long SESSION_TIMEOUT_HOURS = 24;

    /** sessionId → 模式。会话级，不变。 */
    private final Map<String, TeachingMode> sessionModes = new ConcurrentHashMap<>();

    /**
     * 会话启动时由 CaseReadyHandler / startSession 写入。
     */
    public void setMode(String sessionId, TeachingMode mode) {
        if (sessionId == null || mode == null) return;
        sessionModes.put(sessionId, mode);
    }

    /**
     * 取会话模式。
     * 未设置时返回 null，由调用方决定默认行为——ExceptionEngine 视为 EXAM（静默 + 后置记录）。
     * 不做隐式默认，避免"忘了传就变成学习模式乱提示"。
     */
    public TeachingMode getMode(String sessionId) {
        return sessionId == null ? null : sessionModes.get(sessionId);
    }

    public void clearMode(String sessionId) {
        if (sessionId != null) sessionModes.remove(sessionId);
    }
    // ==================== 1. 创建会话 ====================

    @Transactional
    public EmsSession createSession(String scenarioId, String traineeName, String mode, TeamSize teamSize) {
        log.info("创建演练会话: scenarioId={}, traineeName={}, mode={},teamSize={}", scenarioId, traineeName, mode,teamSize);

        EmsScenario scenario = scenarioService.getById(scenarioId);
        if (scenario == null) {
            throw new RuntimeException("场景不存在: " + scenarioId);
        }

        String caseId = scenario.getId();
        ResolvedCaseTypeConfig config = configLoader.getConfig(caseId);
        if (config == null) {
            throw new RuntimeException("案例配置不存在: " + caseId);
        }

        EmsSession session = new EmsSession();
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        session.setId(sessionId);
        session.setScenarioId(scenarioId);
        session.setTraineeName(traineeName);
        session.setMode(mode);
        session.setStatus("INIT");
        session.setStartTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        sessionMapper.insert(session);

        // ============================================================
        // ★ 1. 先创建上下文并写入 caseType
        //    必须在创建状态机之前完成，否则 getOrCreateMachine 期间
        //    触发的 Guard 会因 getSnapshot(sessionId) 返回 null 而找不到上下文。
        // ============================================================
        CaseContext caseContext = contextManager.getOrCreate(sessionId);
        Map<String, Object> initUpdates = new LinkedHashMap<>();
        initUpdates.put("caseType", caseId);
        contextManager.batchSetVarsWithVersion(
            sessionId, initUpdates, "SYSTEM", caseContext.getVersion());

        // ============================================================
        // ★ 2. 再创建状态机（此时 Guard 能拿到上下文）
        // ============================================================
        StateMachine<String, String> stateMachine = stateMachineFacade.getOrCreateMachine(sessionId, config);
        String initialState = stateMachine.getState().getId();
        stateMachineCache.put(sessionId, stateMachine);

        stateMachine.getExtendedState().getVariables().put("caseId", caseId);
        stateMachine.getExtendedState().getVariables().put("sessionId", sessionId);
        stateMachine.getExtendedState().getVariables().put("scenarioId", scenarioId);

        // ============================================================
        // ★ 3. 写入 initialState 到上下文
        // ============================================================
        CaseContext ctxAfterState = contextManager.getSnapshot(sessionId);
        contextManager.batchSetVarsWithVersion(
            sessionId,
            Map.of("currentState", initialState),
            "SYSTEM",
            ctxAfterState != null ? ctxAfterState.getVersion() : 0L);

        // ============================================================
        // ★ 4. 应用 resetDefaults（会触发 derived 自动刷新）
        // ============================================================
        boolean resetApplied = contextManager.applyResetDefaults(sessionId, caseId, "SYSTEM");
        if (!resetApplied) {
            log.warn("应用 resetDefaults 失败，可能无默认值或版本冲突，sessionId={}", sessionId);
        } else {
            log.info("✅ 案例 {} 默认变量已初始化", caseId);
        }

        // ============================================================
        // ★ 4.5 Phase SM：初始化主阶段
        //    位置：resetDefaults 之后（变量已就绪），团队初始化之前
        //    - initialize 幂等，重复调用安全
        //    - 若无 phases 配置，内部自动跳过
        //    - 失败不阻断会话创建（warn 日志，主阶段保持 null）
        // ============================================================
        try {
            phaseManager.initialize(sessionId, config);
            CaseContext phaseCtx = contextManager.getSnapshot(sessionId);
            String initialPhase = phaseCtx != null ? phaseCtx.getPrimaryPhase() : null;
            log.info("✅ Phase 已初始化: session={}, primaryPhase={}", sessionId, initialPhase);
        } catch (Exception e) {
            log.warn("Phase 初始化失败（不阻断会话创建）: session={}, err={}",
                sessionId, e.getMessage());
        }

        CaseContext updatedCtx = contextManager.getSnapshot(sessionId);
        Map<String, Object> allVars = updatedCtx != null ? updatedCtx.getVariables() : Collections.emptyMap();

        // ============================================================
        // ★ 5. 初始化团队
        // ============================================================
        try {
            TeamSize teamTemplate = teamSize;
            try {
                JsonNode configNode = objectMapper.readTree(scenario.getConfigJson());
                //teamTemplate = configNode.path("teamTemplate");
            } catch (Exception e) {
                log.debug("无法从场景配置读取团队模板，使用默认值: {}", teamSize);
            }
            teamManager.initializeTeam(sessionId, teamSize);
            log.info("🏥 虚拟团队已初始化: {} (模板: {})", sessionId, teamSize);
        } catch (Exception e) {
            log.warn("虚拟团队初始化失败，使用默认三人组: {}", e.getMessage());
            teamManager.initializeTeam(sessionId, teamSize);
        }

        // ============================================================
        // ★ 6. 写入 Redis 会话上下文
        // ============================================================
        Map<String, Object> redisContext = new HashMap<>();
        redisContext.put("sessionId", sessionId);
        redisContext.put("scenarioId", scenarioId);
        redisContext.put("caseId", caseId);
        redisContext.put("traineeName", traineeName);
        redisContext.put("mode", mode);
        redisContext.put("teamSize", teamSize);   // ★ 新增：供 startSession 恢复用
        redisContext.put("status", "RUNNING");
        redisContext.put("currentState", initialState);
        redisContext.put("actions", new ArrayList<>());
        redisContext.put("traces", new ArrayList<>());
        redisContext.put("transcript", "");
        if (allVars != null && !allVars.isEmpty()) {
            redisContext.putAll(allVars);
        }

        redisTemplate.opsForHash().putAll(SESSION_CONTEXT_PREFIX + sessionId, redisContext);
        redisTemplate.expire(SESSION_CONTEXT_PREFIX + sessionId, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);

        session.setStatus("RUNNING");
        sessionMapper.updateById(session);

        log.info("✅ 会话创建成功，状态机已初始化: {}", sessionId);
        return session;
    }

    /**
     * 重建会话上下文、状态机、Phase 与团队。
     *
     * <p>用于 startSession 恢复被 endSession 清空的会话（"结束→启动"场景）。</p>
     */
    private void reinitializeContext(String sessionId, String scenarioId, TeamSize teamSize) {
        EmsScenario scenario = scenarioService.getById(scenarioId);
        if (scenario == null) {
            throw new RuntimeException("场景不存在: " + scenarioId);
        }
        String caseId = scenario.getId();
        ResolvedCaseTypeConfig config = configLoader.getConfig(caseId);
        if (config == null) {
            throw new RuntimeException("案例配置不存在: " + caseId);
        }

        // 1. 建 context + caseType
        CaseContext caseContext = contextManager.getOrCreate(sessionId);
        contextManager.batchSetVarsWithVersion(
            sessionId, Map.of("caseType", caseId), "SYSTEM", caseContext.getVersion());

        // 2. 建状态机
        redisTemplate.delete("acls:sm:state:" + sessionId);
        stateMachineFacade.clearCache(sessionId);
        StateMachine<String, String> stateMachine =
            stateMachineFacade.getOrCreateMachine(sessionId, config);

        String initialState = stateMachine.getState().getId();
        stateMachineCache.put(sessionId, stateMachine);
        stateMachine.getExtendedState().getVariables().put("caseId", caseId);
        stateMachine.getExtendedState().getVariables().put("sessionId", sessionId);
        stateMachine.getExtendedState().getVariables().put("scenarioId", scenarioId);

        // 3. 写 currentState
        CaseContext ctxAfterState = contextManager.getSnapshot(sessionId);
        contextManager.batchSetVarsWithVersion(
            sessionId, Map.of("currentState", initialState), "SYSTEM",
            ctxAfterState != null ? ctxAfterState.getVersion() : 0L);

        // 4. resetDefaults（触发 derived 刷新）
        boolean resetApplied = contextManager.applyResetDefaults(sessionId, caseId, "SYSTEM");
        if (!resetApplied) {
            log.warn("应用 resetDefaults 失败: {}", sessionId);
        } else {
            log.info("✅ 案例 {} 默认变量已初始化", caseId);
        }

        // 5. Phase 初始化
        try {
            phaseManager.initialize(sessionId, config);
            CaseContext phaseCtx = contextManager.getSnapshot(sessionId);
            String initialPhase = phaseCtx != null ? phaseCtx.getPrimaryPhase() : null;
            log.info("✅ Phase 已初始化: session={}, primaryPhase={}", sessionId, initialPhase);
        } catch (Exception e) {
            log.warn("Phase 初始化失败: session={}, err={}", sessionId, e.getMessage());
        }

        // 6. 团队初始化
        try {
            teamManager.initializeTeam(sessionId, teamSize);
            log.info("🏥 虚拟团队已初始化: {} (模板: {})", sessionId, teamSize);
        } catch (Exception e) {
            log.warn("虚拟团队初始化失败: {}", e.getMessage());
        }

        // 7. 同步 Redis 中的 status（context 已重建，标记为 RUNNING）
        redisTemplate.opsForHash().put(SESSION_CONTEXT_PREFIX + sessionId, "status", "RUNNING");
        redisTemplate.opsForHash().put(SESSION_CONTEXT_PREFIX + sessionId, "teamSize", teamSize);

        log.info("✅ 会话上下文已重建: {}", sessionId);
    }

    /**
     * 从 Redis 读取 teamSize（会话创建时写入）。
     */
    private TeamSize readTeamSizeFromRedis(String sessionId) {
        try {
            Object v = redisTemplate.opsForHash()
                .get(SESSION_CONTEXT_PREFIX + sessionId, "teamSize");
            if (v == null) return null;
            if (v instanceof TeamSize) return (TeamSize) v;
            return TeamSize.valueOf(String.valueOf(v));
        } catch (Exception e) {
            log.warn("读取 teamSize 失败: {}", e.getMessage());
            return null;
        }
    }

    public String getLiveMermaid(String sessionId) {
        StateMachine<String, String> sm = stateMachineCache.get(sessionId);
        if (sm == null) {
            throw new RuntimeException("状态机未找到: " + sessionId);
        }
        String currentState = sm.getState().getId();
        String caseId = (String) sm.getExtendedState().getVariables().get("caseId");
        if (StringUtils.isBlank(caseId)) {
            caseId = (String) redisTemplate.opsForHash().get(SESSION_CONTEXT_PREFIX + sessionId, "caseId");
        }
        if (StringUtils.isBlank(caseId)) {
            throw new RuntimeException("无法获取案例ID");
        }
        return configLoader.generateMermaid(caseId, true);
    }

    // ==================== 2. 开始会话 ====================
    public void startSession(EmsSession session) {
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }
        String sessionId = session.getId();
        log.info("开始会话: {}", sessionId);

        // ★ 新增：检测 context 已被 endSession 清空 → 重建
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null || ctx.getCaseType() == null) {
            log.info("♻️ 检测到 context 已失效，重新初始化: ems={}, scenario={}",
                sessionId, session.getScenarioId());

            TeamSize teamSize = readTeamSizeFromRedis(sessionId);
            if (teamSize == null) {
                teamSize = TeamSize.defaultSize();
                log.warn("Redis 无 teamSize 记录，使用默认: {}", teamSize);
            }

            reinitializeContext(sessionId, session.getScenarioId(), teamSize);
        }

        session.setStatus("RUNNING");
        sessionMapper.updateById(session);
        redisTemplate.opsForHash().put(SESSION_CONTEXT_PREFIX + sessionId, "status", "RUNNING");
        processTranscriptService.prepareForStart(sessionId);

        // ★ 建立 ASR 连接（幂等：已连接就不重复建）
        try {
            if (!asrClient.isSessionActive(sessionId)) {
                asrClient.startRealtimeRecognition(sessionId);
                log.info("✅ ASR 连接已在 startSession 建立: {}", sessionId);
            } else {
                log.info("ASR 连接已存在，跳过创建: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("❌ startSession 建立 ASR 连接失败: {}", sessionId, e);
            // 不抛出，允许会话继续，前端连接后可再试
        }
    }

    /**
     * ★ 获取转移历史。显式类型判断，避免 unchecked 警告。
     */
    public List<Map<String, Object>> getTransitionHistory(String sessionId) {
        String key = SESSION_CONTEXT_PREFIX + sessionId + ":transitions";
        List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object obj : list) {
            if (obj instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    typed.put(String.valueOf(e.getKey()), e.getValue());
                }
                result.add(typed);
            }
        }
        return result;
    }

    public String getCaseMermaid(String caseType,boolean isfull) {
        return configLoader.generateMermaid(caseType,isfull);
    }

    public Map<String, Object> getDefaultVariables(String caseType) {
        Map<String, VariableMeta> varMetaMap = variableMetaManager.getVariablesForCase(caseType);
        if (varMetaMap == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> defaults = new HashMap<>();
        for (Map.Entry<String, VariableMeta> entry : varMetaMap.entrySet()) {
            Object defaultVal = entry.getValue().getDefaultValue();
            if (defaultVal != null) {
                defaults.put(entry.getKey(), defaultVal);
            }
        }
        return defaults;
    }

    public Map<String, Object> getAllVariables(String sessionId) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            throw new RuntimeException("会话上下文不存在: " + sessionId);
        }
        return new HashMap<>(ctx.getVariables());
    }

    public Map<String, Object> getModifiedVariables(String sessionId) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            throw new RuntimeException("会话上下文不存在: " + sessionId);
        }
        return ctx.getModifiedVariablesAsMap();
    }

    /**
     * 同步执行用户指令。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String executeCommandSync(CommandTask task) {
        String sessionId = task.getSessionId();
        String transcript = task.getTranscript();
        Session webSocketSession = task.getWebSocketSession();

        log.info("🧠 [执行同步] 会话 {} 开始处理指令: {}", sessionId, transcript);
        // ★ 修改：改用 emsSessionId 走 RoomBroadcaster 新通道（旧 Session 通道 Unity 收不到）
        messageSender.sendTranscript(sessionId,
            String.format("会话 %s 开始处理指令: %s", sessionId, transcript), true);

        if (Thread.interrupted()) {
            return "TASK_INTERRUPTED";
        }

        String caseId = (String) redisTemplate.opsForHash()
            .get(SESSION_CONTEXT_PREFIX + sessionId, "caseId");

        List<IntentCommand> commands;
        try {
            commands = processTranscriptService.processTranscriptMulti(sessionId, transcript, caseId);
        } catch (Exception e) {
            log.error("❌ 分句分类失败", e);
            return "CLASSIFY_ERROR: " + e.getMessage();
        }

        if (commands == null || commands.isEmpty()) {
            return "UNKNOWN_INTENT: 无有效分句";
        }

        List<String> results = new ArrayList<>(commands.size());
        for (int i = 0; i < commands.size(); i++) {
            if (Thread.interrupted()) {
                results.add("INTERRUPTED_AT_" + i);
                break;
            }
            IntentCommand cmd = commands.get(i);
            if (cmd == null) {
                results.add("NULL_AT_" + i);
                continue;
            }
            try {
                String r = executeSingleCommand(sessionId, webSocketSession, cmd, i, commands.size());
                results.add(r);
            } catch (Exception e) {
                log.error("❌ 第 {} 条指令处理失败", i, e);
                results.add("ERROR_AT_" + i + ": " + e.getMessage());
            }
        }

        return String.join(" | ", results);
    }

    // ==================== ★ 单条指令执行（含元语用分派） ====================

    /**
     * 单条指令执行：
     * <ul>
     *   <li>META_* → 元语用分派（VETO / CLOSED_LOOP / STRATEGY_CORRECTION）</li>
     *   <li>其余 → 常规意图处理（走 IntelligentCommandProcessor）</li>
     * </ul>
     */
    private String executeSingleCommand(String sessionId,
                                        Session webSocketSession,
                                        IntentCommand command,
                                        int index,
                                        int total) {

        if (!command.isValid()) {
            String msg = String.format("未识别意图: %s", command.getErrorMessage());
            // ★ 修改
            messageSender.sendTranscript(sessionId, msg, true);
            return "UNKNOWN_INTENT: " + command.getErrorMessage();
        }

        String intentCode = command.getIntentCode();
        String actType = command.getActType();

        // ==================== ★ 元语用分派 ====================
        if (intentCode != null && intentCode.startsWith("META_")) {
            log.info("💬 元语用分派: session={}, intent={}, actType={}",
                sessionId, intentCode, actType);

            return switch (actType == null ? "" : actType) {
                case "VETO" ->
                    handleVeto(sessionId, webSocketSession, command);
                case "CLOSED_LOOP" ->
                    handleClosedLoop(sessionId, webSocketSession, command);
                case "STRATEGY_CORRECTION" ->
                    handleStrategyCorrection(sessionId, webSocketSession, command, index, total);
                default -> {
                    // ★ 修改
                    messageSender.sendTranscript(sessionId, "收到。", true);
                    yield "SUCCESS: 收到";
                }
            };
        }

        // ==================== 常规意图处理 ====================
        String matchedName = command.getMatchedDefinition() != null
            ? command.getMatchedDefinition().getNameCn()
            : "(未绑定定义: " + intentCode + ")";

        String prefix = total > 1 ? String.format("[%d/%d] ", index + 1, total) : "";
        String info = String.format("%s🔍 意图: intent=%s, actType=%s, classifyMode=%s",
            prefix, matchedName, command.getActType(), command.getClassifyMode());
        // ★ 修改
        messageSender.sendTranscript(sessionId, info, true);

        boolean success = false;
        String resultText = "";

        // ★ 新增：处理器明确拒绝（带反馈）时记录，区别于"无处理器支持"
        String rejectedFeedback = null;
        String rejectedReturnCode = null;

        try {
            List<IntelligentCommandProcessor> processors =
                intelligentProcessorProvider.stream().toList();

            for (IntelligentCommandProcessor processor : processors) {
                if (!processor.supports(intentCode)) continue;

                Map<String, Object> result = processor.processCommand(sessionId, command);
                if (result == null) continue;

                boolean ok = Boolean.TRUE.equals(result.get("success"));

                if (!ok) {
                    // ★ 明确拒绝：取 errorMessage（兼容 errorMsg / msg / reason 几种常见 key）
                    String errMsg = firstNonBlank(
                        result.get("errorMessage"),
                        result.get("errorMsg"),
                        result.get("msg"),
                        result.get("reason")
                    );
                    if (errMsg != null && !errMsg.isBlank()) {
                        log.info("🚫 处理器明确拒绝: session={}, intent={}, processor={}, msg={}",
                            sessionId, intentCode,
                            processor.getClass().getSimpleName(), errMsg);
                        rejectedFeedback = errMsg;
                        rejectedReturnCode = (String) result.getOrDefault("returnCode", "BLOCKED");
                        break;
                    }
                    // 无反馈文本 → 视作"不支持"，继续试下一个
                    continue;
                }

                // 成功路径
                success = true;
                resultText = (String) result.getOrDefault("responseText", "指令已处理");

                @SuppressWarnings("unchecked")
                Map<String, Object> contextUpdates =
                    (Map<String, Object>) result.get("contextUpdates");
                if (contextUpdates != null && !contextUpdates.isEmpty()) {
                    applyContextUpdates(sessionId, contextUpdates);
                }
                break;
            }

            // 只有"没有任何处理器支持该意图"才打 WARN
            if (!success && rejectedFeedback == null) {
                log.warn("⚠️ 没有处理器支持意图: {}, 已尝试处理器数: {}",
                    intentCode, processors.size());
            }
        } catch (Exception e) {
            log.error("❌ 执行异常: intent={}", intentCode, e);
            return "LLM_ERROR: " + e.getMessage();
        }

        // ==================== ★ 处理器明确拒绝：走统一反馈路径 ====================
        if (!success && rejectedFeedback != null) {
            String reply = "🚫 " + rejectedFeedback;
            try {
                ttsClient.synthesizeAndSend(sessionId, rejectedFeedback, "zf_005", 1.2f);
            } catch (Exception ttsEx) {
                log.warn("拒绝反馈 TTS 失败（不影响前端下发）: session={}, err={}",
                    sessionId, ttsEx.getMessage());
            }
            // ★ 修改
            messageSender.sendTranscript(sessionId, reply, true);

            log.info("📤 拒绝反馈已下发: session={}, intent={}, feedback={}",
                sessionId, intentCode, rejectedFeedback);

            return (rejectedReturnCode != null ? rejectedReturnCode : "BLOCKED")
                + ": " + rejectedFeedback;
        }

        // ==================== 成功路径 ====================
        if (success) {
            ttsClient.synthesizeAndSend(sessionId, resultText, "zf_005", 1.2f);
            // ★ 修改
            messageSender.sendTranscript(sessionId, resultText, true);
            // ★ 记录最后一次成功意图，供 VETO / CLOSED_LOOP 回查
            recordLastIntent(sessionId, command);
            return "SUCCESS: " + resultText;
        }

        return "PROCESSING_FAILED: " + intentCode;
    }

    /**
     * ★ 新增工具方法：从若干候选中返回第一个非空非 blank 的字符串。
     * 兼容不同适配器可能使用的 key（errorMessage / errorMsg / msg / reason）。
     */
    private static String firstNonBlank(Object... candidates) {
        if (candidates == null) return null;
        for (Object c : candidates) {
            if (c == null) continue;
            String s = String.valueOf(c);
            if (!s.isBlank()) return s;
        }
        return null;
    }

    public void cleanupRuntime(String sessionId) {
        try {
            stateMachineFacade.clearCache(sessionId);
        } catch (Exception e) {
            log.warn("清理状态机缓存失败: sessionId={}", sessionId, e);
        }
        try {
            teamManager.clearTeam(sessionId);
        } catch (Exception e) {
            log.warn("清理团队失败: sessionId={}", sessionId, e);
        }
        try {
            teamManager.removeChain(sessionId);
        } catch (Exception e) {
            log.warn("清理任务链失败: sessionId={}", sessionId, e);
        }
        try {
            contextManager.clearContext(sessionId);
        } catch (Exception e) {
            log.warn("清理上下文失败: sessionId={}", sessionId, e);
        }
        clearMode(sessionId);
        log.info("🧹 会话运行时资源已清理: {}", sessionId);
    }

    // ==================== 元语用处理 ====================

    /**
     * VETO：否决上一个动作。
     */
    private String handleVeto(String sessionId, Session ws, IntentCommand cmd) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        IntentCommand lastIntent = ctx != null ? ctx.getLastIntentResult() : null;
        String targetCode = lastIntent != null ? lastIntent.getIntentCode() : null;

        String targetName = null;
        if (lastIntent != null && lastIntent.getMatchedDefinition() != null) {
            targetName = lastIntent.getMatchedDefinition().getNameCn();
        }

        String msg;
        if (targetCode != null) {
            msg = String.format("已否决上一个动作: %s",
                targetName != null ? targetName : targetCode);
        } else {
            msg = "已否决当前操作";
        }

        log.info("🛑 [VETO] session={}, target={}, raw='{}'",
            sessionId, targetCode, cmd.getRawText());

        if (targetCode != null) {
            List<IntelligentCommandProcessor> processors =
                intelligentProcessorProvider.stream().toList();
            for (IntelligentCommandProcessor processor : processors) {
                if (!processor.supports(targetCode)) continue;
                try {
                    log.debug("[VETO] 处理器 {} 支持意图 {}，但尚未实现回滚语义，仅记录",
                        processor.getClass().getSimpleName(), targetCode);
                } catch (Exception e) {
                    log.warn("[VETO] 回滚调用失败: {}", e.getMessage());
                }
                break;
            }
        }

        // ★ 修改
        messageSender.sendTranscript(sessionId, "🛑 " + msg, true);
        return "SUCCESS: " + msg;
    }

    /**
     * CLOSED_LOOP：确认上一个动作已完成。
     */
    private String handleClosedLoop(String sessionId, Session ws, IntentCommand cmd) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        IntentCommand lastIntent = ctx != null ? ctx.getLastIntentResult() : null;
        String targetCode = lastIntent != null ? lastIntent.getIntentCode() : null;

        String msg;
        if (targetCode != null && lastIntent.isValid()) {
            String name = lastIntent.getMatchedDefinition() != null
                ? lastIntent.getMatchedDefinition().getNameCn()
                : targetCode;
            msg = String.format("已完成: %s", name);
        } else {
            msg = "当前无待确认动作";
        }

        log.info("✅ [CLOSED_LOOP] session={}, target={}", sessionId, targetCode);
        // ★ 修改
        messageSender.sendTranscript(sessionId, "✅ " + msg, true);
        return "SUCCESS: " + msg;
    }

    /**
     * STRATEGY_CORRECTION：策略纠偏。
     */
    private String handleStrategyCorrection(String sessionId,
                                            Session ws,
                                            IntentCommand cmd,
                                            int index,
                                            int total) {
        log.info("🔄 [STRATEGY_CORRECTION] session={}, intent={}, raw='{}'",
            sessionId, cmd.getIntentCode(), cmd.getRawText());

        // ★ 修改
        messageSender.sendTranscript(sessionId, "🔄 收到，已按新策略调整。", true);
        return "SUCCESS: 策略纠偏已记录";
    }

    // ==================== 常规意图处理（独立方法，语义同 executeSingleCommand 常规路径） ====================

    private String executeIntent(String sessionId,
                                 Session webSocketSession,
                                 IntentCommand command,
                                 int index,
                                 int total) {

        String intentCode = command.getIntentCode();
        String matchedName = command.getMatchedDefinition() != null
            ? command.getMatchedDefinition().getNameCn()
            : "(未绑定定义: " + intentCode + ")";

        String prefix = total > 1 ? String.format("[%d/%d] ", index + 1, total) : "";
        String info = String.format("%s🔍 意图: intent=%s, actType=%s, classifyMode=%s",
            prefix, matchedName, command.getActType(), command.getClassifyMode());
        // ★ 修改
        messageSender.sendTranscript(sessionId, info, true);

        boolean success = false;
        String resultText = "";

        try {
            List<IntelligentCommandProcessor> processors =
                intelligentProcessorProvider.stream().toList();

            for (IntelligentCommandProcessor processor : processors) {
                if (!processor.supports(intentCode)) continue;

                Map<String, Object> result = processor.processCommand(sessionId, command);
                if (result == null || !Boolean.TRUE.equals(result.get("success"))) continue;

                success = true;
                resultText = (String) result.getOrDefault("responseText", "指令已处理");

                @SuppressWarnings("unchecked")
                Map<String, Object> contextUpdates =
                    (Map<String, Object>) result.get("contextUpdates");
                if (contextUpdates != null && !contextUpdates.isEmpty()) {
                    applyContextUpdates(sessionId, contextUpdates);
                }
                break;
            }

            if (!success) {
                log.warn("⚠️ 没有处理器支持意图: {}, 已尝试处理器数: {}",
                    intentCode, processors.size());
            }
        } catch (Exception e) {
            log.error("❌ 执行异常: intent={}", intentCode, e);
            if (e instanceof InterruptedException) {
                return "LLM_INTERRUPTED";
            }
            return "LLM_ERROR: " + e.getMessage();
        }

        if (success) {
            ttsClient.synthesizeAndSend(sessionId, resultText, "zf_005", 1.2f);
            // ★ 修改
            messageSender.sendTranscript(sessionId, resultText, true);
            // ★ 记录最后一次成功意图
            recordLastIntent(sessionId, command);
            return "SUCCESS: " + resultText;
        }

        return "PROCESSING_FAILED: " + intentCode;
    }

    // ==================== ★ 记录最后一次成功意图 ====================

    /**
     * 记录最后一次成功执行的意图，供 VETO / CLOSED_LOOP 等元语用回查。
     * <p>走 updateConversationState，不再直接改 CaseContext 字段。</p>
     */
    private void recordLastIntent(String sessionId, IntentCommand command) {
        try {
            contextManager.updateConversationState(sessionId, command, null, null);
            log.debug("记录 lastIntentResult: session={}, intent={}",
                sessionId, command != null ? command.getIntentCode() : null);
        } catch (Exception e) {
            log.warn("记录 lastIntentResult 失败: {}", e.getMessage());
        }
    }

    // ==================== 应用上下文更新 ====================

    private void applyContextUpdates(String sessionId, Map<String, Object> updates) {
        CaseContext latestCtx = contextManager.getSnapshot(sessionId);
        long version = latestCtx != null ? latestCtx.getVersion() : 0L;
        boolean ok = contextManager.batchSetVarsWithVersion(
            sessionId, updates, "SYSTEM", version);

        if (!ok) {
            CaseContext retryCtx = contextManager.getSnapshot(sessionId);
            long retryVersion = retryCtx != null ? retryCtx.getVersion() : 0L;
            ok = contextManager.batchSetVarsWithVersion(
                sessionId, updates, "SYSTEM", retryVersion);
            if (!ok) {
                log.error("上下文更新重试仍失败，可能数据不一致，sessionId={}", sessionId);
            } else {
                log.info("重试应用上下文更新成功");
            }
        } else {
            log.debug("上下文更新已应用: {}", updates.keySet());
        }
    }

    // ==================== 4. 获取会话数据 ====================

    public Map<Object, Object> getSessionContext(String sessionId) {
        return redisTemplate.opsForHash().entries(SESSION_CONTEXT_PREFIX + sessionId);
    }

    public String getTranscript(String sessionId) {
        return (String) redisTemplate.opsForHash().get(SESSION_CONTEXT_PREFIX + sessionId, "transcript");
    }

    public TeamPerformance getPerformance(String sessionId) {
        return (TeamPerformance) redisTemplate.opsForHash().get(SESSION_CONTEXT_PREFIX + sessionId, "performance");
    }

    /**
     * ★ 获取当前状态：走 batchSetVarsWithVersion，不再直接改字段 + update(...)
     */
    public String getCurrentState(String sessionId) {
        StateMachine<String, String> sm = stateMachineCache.get(sessionId);
        if (sm != null && sm.getState() != null) {
            String state = sm.getState().getId();
            redisTemplate.opsForHash().put(SESSION_CONTEXT_PREFIX + sessionId, "currentState", state);

            CaseContext ctx = contextManager.getSnapshot(sessionId);
            if (ctx != null) {
                contextManager.batchSetVarsWithVersion(
                    sessionId,
                    Map.of("currentState", state),
                    "SYSTEM",
                    ctx.getVersion()
                );
            }
            return state;
        }
        return (String) redisTemplate.opsForHash().get(SESSION_CONTEXT_PREFIX + sessionId, "currentState");
    }

    public EmsSession getSessionById(String sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    public String getSessionStatus(String sessionId) {
        EmsSession session = sessionMapper.selectById(sessionId);
        return session != null ? session.getStatus() : null;
    }

    // ==================== 5. 结束会话 ====================

    @Transactional
    public void endSession(String sessionId) {
        log.info("结束会话: {}", sessionId);
        EmsSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        // ★ 断开 ASR 连接
        try {
            asrClient.stopRealtimeRecognition("asr-task-" + sessionId);
            log.info("✅ ASR 连接已在 endSession 断开: {}", sessionId);
        } catch (Exception e) {
            log.error("❌ endSession 断开 ASR 失败: {}", sessionId, e);
        }

        try {
            teamManager.clearTeam(sessionId);
            log.info("🏥 虚拟团队已清理: {}", sessionId);
        } catch (Exception e) {
            log.warn("清理虚拟团队失败: {}", e.getMessage());
        }
        removeSessionCache(sessionId);
        stateMachineFacade.clearCache(sessionId);
        redisTemplate.delete("acls:sm:state:" + sessionId);
        contextManager.clearContext(sessionId);
    }
    public void removeSessionCache(String sessionId) {
        StateMachine<String, String> machine = stateMachineCache.remove(sessionId);
        if (machine != null) {
            try {
                machine.stopReactively();
                log.info("状态机已停止并移除: {}", sessionId);
            } catch (Exception e) {
                log.warn("停止状态机时发生异常: {}", e.getMessage());
            }
        }
    }

    // ==================== 6. 团队管理接口 ====================

    public boolean switchTeamTemplate(String sessionId, TeamSize templateId) {
        try {
            teamManager.initializeTeam(sessionId, templateId);
            redisTemplate.opsForHash().put(SESSION_CONTEXT_PREFIX + sessionId, "teamTemplate", templateId);
            log.info("🏥 切换团队模板: {} -> {}", sessionId, templateId);
            return true;
        } catch (Exception e) {
            log.error("切换团队模板失败: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getTeamState(String sessionId) {
        return teamManager.getTeamState(sessionId);
    }

    public void resetTeam(String sessionId) {
        teamManager.resetTeam(sessionId);
        log.info("🔄 团队已重置: {}", sessionId);
    }

    // ==================== 7. 热加载与测试接口 ====================

    public void reloadIntentDictionary(String caseId) {
        log.info("🔄 [SessionService] 开始热加载意图字典...");
        long startTime = System.currentTimeMillis();
        try {
            intentClassificationService.reloadIntentDictionary(caseId);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ [SessionService] 意图字典热加载完成！耗时 {}ms", elapsed);
        } catch (Exception e) {
            log.error("❌ [SessionService] 意图字典热加载失败", e);
            throw new RuntimeException("意图字典热加载失败", e);
        }
    }

    public int getActiveSessionCount() {
        return stateMachineCache.size();
    }

    public Set<String> getActiveSessionIds() {
        return new HashSet<>(stateMachineCache.keySet());
    }

    public CaseIntentDefinitionsLoader getIntentRegistry() {
        return intentRegistry;
    }

    // ==================== 8. 统一状态信息接口 ====================
    /**
     * 从 primaryPhase 递归向上找到顶层 phase id。
     * 若 primaryPhase 本身就是顶层，直接返回；若找不到，兜底返回 primaryPhase。
     */
    private String resolveTopPhaseId(ResolvedCaseTypeConfig config, String primaryPhase) {
        if (config == null || primaryPhase == null) {
            return primaryPhase;
        }
        for (PhaseDefinition top : config.getPhases()) {
            if (top.getId().equals(primaryPhase)) {
                return top.getId();
            }
            if (top.hasChildren()) {
                String found = findInChildren(top, primaryPhase);
                if (found != null) {
                    return top.getId();
                }
            }
        }
        return primaryPhase;
    }

    private String findInChildren(PhaseDefinition parent, String targetId) {
        if (parent.getChildren() == null) {
            return null;
        }
        for (PhaseDefinition child : parent.getChildren()) {
            if (child.getId().equals(targetId)) {
                return child.getId();
            }
            if (child.hasChildren()) {
                String found = findInChildren(child, targetId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    public Map<String, Object> getStateInfo(String sessionId, String caseType) {
        if (sessionId != null && !sessionId.isEmpty()) {
            EmsSession session = getSessionById(sessionId);
            if (session != null) {
                return buildRuntimeState(sessionId, session);
            }
            if (caseType != null && !caseType.isEmpty()) {
                log.warn("会话不存在，降级到设计态: sessionId={}, caseType={}", sessionId, caseType);
                return buildDesignState(caseType);
            }
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        if (caseType != null && !caseType.isEmpty()) {
            return buildDesignState(caseType);
        }
        throw new IllegalArgumentException("请提供 sessionId 或 caseType");
    }

    private Map<String, Object> buildRuntimeState(String sessionId, EmsSession session) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            throw new RuntimeException("会话上下文丢失: " + sessionId);
        }
        String caseId = ctx.getCaseType();
        if (caseId == null) {
            caseId = (String) redisTemplate.opsForHash().get(SESSION_CONTEXT_PREFIX + sessionId, "caseId");
        }
        if (caseId == null) {
            throw new RuntimeException("无法获取案例类型");
        }

        String currentState = getCurrentState(sessionId);
        Map<String, Object> variables = new HashMap<>(ctx.getVariables());
        List<Map<String, Object>> history = getTransitionHistory(sessionId);
        String liveMermaid = getLiveMermaid(sessionId);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sessionId", sessionId);
        snapshot.put("status", session.getStatus());
        snapshot.put("currentState", currentState != null ? currentState : "UNKNOWN");
        snapshot.put("variables", variables);
        snapshot.put("history", history != null ? history : Collections.emptyList());
        snapshot.put("mermaid", liveMermaid);

        // ★ Phase SM：把主阶段加入快照（若已启用）
        if (ctx.getPhaseRuntime() != null) {
            // ★ 改动：解析顶层 phase，用于主图高亮（子阶段时回到其顶层祖先）
            ResolvedCaseTypeConfig config = configLoader.getConfig(caseId);
            String primaryPhase = ctx.getPrimaryPhase();
            String primaryTopPhase = resolveTopPhaseId(config, primaryPhase);

            Map<String, Object> phaseInfo = new LinkedHashMap<>();
            phaseInfo.put("primaryPhase", primaryPhase);
            phaseInfo.put("primaryTopPhase", primaryTopPhase); // ★ 改动：新增字段，前端主图高亮用
            phaseInfo.put("activePhases", new ArrayList<>(ctx.getPhaseRuntime().getActivePhases()));
            phaseInfo.put("lockedPhase", ctx.getPhaseRuntime().getLockedPhase());
            phaseInfo.put("lockUntil", ctx.getPhaseRuntime().getLockUntil());
            phaseInfo.put("revision", ctx.getPhaseRuntime().getRevision());
            snapshot.put("phase", phaseInfo);
        }

        Map<String, Object> definitions = buildDefinitions(caseId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "runtime");
        result.put("snapshot", snapshot);
        result.put("definitions", definitions);
        return result;
    }

    private Map<String, Object> buildDesignState(String caseType) {
        ResolvedCaseTypeConfig config = configLoader.getConfig(caseType);
        if (config == null) {
            throw new RuntimeException("案例配置不存在: " + caseType);
        }

        String initialState = config.getInitialState();
        if (initialState == null) {
            initialState = "SCENE_START";
        }
        String staticMermaid = getCaseMermaid(caseType,false);
        Map<String, Object> defaultVariables = getDefaultVariables(caseType);

        Map<String, Object> definitions = buildDefinitions(caseType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "design");
        result.put("caseType", caseType);
        result.put("initialState", initialState);
        // ★ Phase SM：设计态附上 initialPhase 与 phases 概要
        result.put("initialPhase", config.getInitialPhase());
        result.put("phaseCount", config.getPhaseCount());
        result.put("mermaid", staticMermaid != null ? staticMermaid : "");
        result.put("defaultVariables", defaultVariables != null ? defaultVariables : Collections.emptyMap());
        result.put("definitions", definitions);
        return result;
    }

    private Map<String, Object> buildDefinitions(String caseType) {
        Map<String, Object> definitions = new LinkedHashMap<>();

        Map<String, VariableMeta> variableMeta = variableMetaManager.getVariablesForCase(caseType);
        definitions.put("variableMeta", variableMeta != null ? variableMeta : Collections.emptyMap());

        List<SubDomain> intents = intentRegistry.getCaseDefinitions(caseType);
        definitions.put("intentDefinitions", intents != null ? intents : Collections.emptyList());

        ResolvedCaseTypeConfig config = configLoader.getConfig(caseType);
        if (config != null) {
            Map<String, Object> stateMachineInfo = new LinkedHashMap<>();
            stateMachineInfo.put("initialState", config.getInitialState() != null ? config.getInitialState() : "SCENE_START");
            definitions.put("stateMachine", stateMachineInfo);

            // ★ Phase SM：暴露 phases 定义供前端审校
            if (config.hasPhases()) {
                definitions.put("phases", config.getPhases());
            }
        }

        return definitions;
    }
    /**
     * ★ Phase SM：生成某个阶段的内部状态机 mermaid。
     *
     * <p>供前端点击阶段框（subgraph）时弹出子状态机使用。</p>
     *
     * <p>解析优先级：</p>
     * <ol>
     *   <li>若提供 sessionId，从上下文取 caseType</li>
     *   <li>否则使用传入的 caseType</li>
     * </ol>
     *
     * @param sessionId 会话 ID（可选）
     * @param caseType  案例类型（可选，sessionId 为空时使用）
     * @param phaseId   阶段 ID（必填）
     * @return mermaid 代码；无配置时返回空串
     */
    public String getPhaseSubgraphMermaid(String sessionId, String caseType, String phaseId) {
        if (phaseId == null || phaseId.isEmpty()) {
            throw new IllegalArgumentException("phaseId 不能为空");
        }

        String effectiveCaseType = caseType;

        // 优先从 sessionId 解析 caseType
        if (sessionId != null && !sessionId.isEmpty()) {
            CaseContext ctx = contextManager.getSnapshot(sessionId);
            if (ctx != null && ctx.getCaseType() != null && !ctx.getCaseType().isEmpty()) {
                effectiveCaseType = ctx.getCaseType();
            }
        }

        if (effectiveCaseType == null || effectiveCaseType.isEmpty()) {
            throw new IllegalArgumentException("必须提供 sessionId 或 caseType");
        }

        return configLoader.generatePhaseSubgraph(effectiveCaseType, phaseId);
    }

    public Map<String, Object> getNodeEntryConditions(String sessionId, String nodeId) {
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            throw new RuntimeException("会话上下文不存在: " + sessionId);
        }
        String caseId = ctx.getCaseType();
        Map<String, Object> variables = new HashMap<>(ctx.getVariables());

        StateMachine<String, String> sm = stateMachineCache.get(sessionId);
        if (sm != null) {
            Map<Object, Object> extVars = sm.getExtendedState().getVariables();
            for (Map.Entry<Object, Object> entry : extVars.entrySet()) {
                String key = entry.getKey().toString();
                if (key.startsWith("derived.")) {
                    variables.put(key, entry.getValue());
                }
            }
        }

        List<TransitionRule> rules = configLoader.getEntryTransitions(caseId, nodeId);

        List<Map<String, Object>> entries = new ArrayList<>();
        for (TransitionRule rule : rules) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("from", rule.getSource());
            entry.put("description", rule.getDescription());
            entry.put("rawCondition", rule.getCondition());
            if (rule.getCondition() != null && !rule.getCondition().isEmpty()) {
                String resolved = resolveExpression(rule.getCondition(), variables);
                entry.put("evaluated", resolved);
            } else {
                entry.put("evaluated", null);
            }
            entries.add(entry);
        }

        entries.sort((a, b) -> {
            int p1 = (int) a.getOrDefault("priority", 0);
            int p2 = (int) b.getOrDefault("priority", 0);
            return Integer.compare(p2, p1);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId);
        result.put("entries", entries);
        result.put("variables", variables);
        return result;
    }

    private String resolveExpression(String expression, Map<String, Object> variables) {
        if (expression == null) return null;
        String result = expression;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("#([a-zA-Z0-9_.]+)");
        java.util.regex.Matcher m = p.matcher(expression);
        while (m.find()) {
            String varName = m.group(1);
            Object value = variables.get(varName);
            String valueStr = (value != null) ? value.toString() : "null";
            if (value instanceof String) {
                valueStr = "'" + valueStr + "'";
            }
            result = result.replace(m.group(0), valueStr);
        }
        return result;
    }
}

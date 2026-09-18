package com.ruoyi.ems.intent.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.ExtractionConfig;
import com.ruoyi.ems.intent.model.ExtractionContext;
import com.ruoyi.ems.intent.model.routing.SlotSpec;
import com.ruoyi.ems.intent.parser.*;
import com.ruoyi.ems.intent.prompt.PromptTemplateService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.baomidou.mybatisplus.spring.spi.SpringCompatibleSet.applicationContext;

@Slf4j
@Component
public class ParameterExtractor {

    private final ChatModel chatModel;
    private final PromptTemplateService promptTemplateService;
    private final EmsContextManager contextManager;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExpressionParser spelParser = new SpelExpressionParser();

    // 所有解析器（使用 required = false 允许缺失）
    private final DrugDoseParser drugParser;
    private final ThresholdParser thresholdParser;
    private final NegationAwareSymptomParser symptomParser;
    private final RhythmParser rhythmParser;
    private final VagalParser vagalParser;
    private final CardioversionParser cardioversionParser;
    private final DeteriorationParser deteriorationParser;
    private final ErrorParser errorParser;
    private final ErrorInterceptorParser errorInterceptorParser;
    private final WpwParser wpwParser;
    private final ResultParser resultParser;
    private final AnticoagulantParser anticoagulantParser;

    public ParameterExtractor(@Qualifier("throttledExtractionChatModel") ChatModel chatModel,
                              PromptTemplateService promptTemplateService,
                              EmsContextManager contextManager,
                              @Autowired(required = false) DrugDoseParser drugParser,
                              @Autowired(required = false) ThresholdParser thresholdParser,
                              @Autowired(required = false) NegationAwareSymptomParser symptomParser,
                              @Autowired(required = false) RhythmParser rhythmParser,
                              @Autowired(required = false) VagalParser vagalParser,
                              @Autowired(required = false) CardioversionParser cardioversionParser,
                              @Autowired(required = false) DeteriorationParser deteriorationParser,
                              @Autowired(required = false) ErrorParser errorParser,
                              @Autowired(required = false) ErrorInterceptorParser errorInterceptorParser,
                              @Autowired(required = false) WpwParser wpwParser,
                              @Autowired(required = false) ResultParser resultParser,
                              @Autowired(required = false) AnticoagulantParser anticoagulantParser) {
        this.chatModel = chatModel;
        this.promptTemplateService = promptTemplateService;
        this.contextManager = contextManager;
        this.drugParser = drugParser;
        this.thresholdParser = thresholdParser;
        this.symptomParser = symptomParser;
        this.rhythmParser = rhythmParser;
        this.vagalParser = vagalParser;
        this.cardioversionParser = cardioversionParser;
        this.deteriorationParser = deteriorationParser;
        this.errorParser = errorParser;
        this.errorInterceptorParser = errorInterceptorParser;
        this.wpwParser = wpwParser;
        this.resultParser = resultParser;
        this.anticoagulantParser = anticoagulantParser;
    }

    public Map<String, Object> extract(IntentCommand intentResult, String sessionId) {
        if (intentResult == null || !intentResult.isValid()) {
            log.warn("意图无效，跳过参数抽取");
            return Collections.emptyMap();
        }

        if (intentResult.getMatchedDefinition() == null) {
            log.debug("意图定义缺失，跳过参数抽取");
            return Collections.emptyMap();
        }

        // ========== 0. 无 extraction 但有 trg.slots，走槽位回退 ==========
        if (intentResult.getMatchedDefinition().getExtraction() == null) {
            Map<String, SlotSpec> slots = intentResult.getMatchedDefinition().getSlots();
            if (slots != null && !slots.isEmpty()) {
                Map<String, Object> slotResult = extractFromTrgSlots(slots, intentResult.getRawText());
                if (!slotResult.isEmpty()) {
                    log.info("意图 {} 槽位抽取成功，参数：{}", intentResult.getIntentCode(), slotResult);
                    intentResult.setSlots(slotResult);
                    return slotResult;
                }
                log.debug("意图 {} 有 slots 但未匹配到任何值", intentResult.getIntentCode());
                return Collections.emptyMap();
            }
            log.debug("意图 {} 无需参数抽取", intentResult.getIntentCode());
            return Collections.emptyMap();
        }

        ExtractionConfig extraction = intentResult.getMatchedDefinition().getExtraction();
        String rawText = intentResult.getRawText();

        // ========== 1. 获取 CaseContext ==========
        CaseContext caseContext = null;
        if (sessionId != null) {
            caseContext = contextManager.getSnapshot(sessionId);
            if (caseContext == null) {
                caseContext = contextManager.getOrCreate(sessionId);
            }
        } else {
            log.warn("caseInstanceId 为空，无法使用全局上下文，抽取将无记忆能力");
        }

        // ========== 2. 构建 ExtractionContext ==========
        ExtractionContext ctx = new ExtractionContext(
            rawText,
            intentResult.getIntentCode(),
            caseContext,
            sessionId
        );

        // ========== 3. 构建 SpEL 上下文（注册所有解析器） ==========
        StandardEvaluationContext spelCtx = new StandardEvaluationContext();

        if (drugParser != null) {
            spelCtx.setVariable("drugParser", drugParser);
        } else {
            log.warn("drugParser 为 null，无法注册到 SpEL 上下文");
        }
        if (thresholdParser != null) {
            spelCtx.setVariable("thresholdParser", thresholdParser);
        } else {
            log.warn("thresholdParser 为 null，无法注册到 SpEL 上下文");
        }
        if (symptomParser != null) {
            spelCtx.setVariable("symptomParser", symptomParser);
        } else {
            log.warn("symptomParser 为 null，无法注册到 SpEL 上下文");
        }
        if (rhythmParser != null) {
            spelCtx.setVariable("rhythmParser", rhythmParser);
        } else {
            log.warn("rhythmParser 为 null，无法注册到 SpEL 上下文");
        }
        if (vagalParser != null) {
            spelCtx.setVariable("vagalParser", vagalParser);
        } else {
            log.warn("vagalParser 为 null，无法注册到 SpEL 上下文");
        }
        if (cardioversionParser != null) {
            spelCtx.setVariable("cardioversionParser", cardioversionParser);
        } else {
            log.warn("cardioversionParser 为 null，无法注册到 SpEL 上下文");
        }
        if (deteriorationParser != null) {
            spelCtx.setVariable("deteriorationParser", deteriorationParser);
        } else {
            log.warn("deteriorationParser 为 null，无法注册到 SpEL 上下文");
        }
        if (errorParser != null) {
            spelCtx.setVariable("errorParser", errorParser);
        } else {
            log.warn("errorParser 为 null，无法注册到 SpEL 上下文");
        }
        if (errorInterceptorParser != null) {
            spelCtx.setVariable("errorInterceptorParser", errorInterceptorParser);
        } else {
            log.warn("errorInterceptorParser 为 null，无法注册到 SpEL 上下文");
        }
        if (wpwParser != null) {
            spelCtx.setVariable("wpwParser", wpwParser);
        } else {
            log.warn("wpwParser 为 null，无法注册到 SpEL 上下文");
        }
        if (resultParser != null) {
            spelCtx.setVariable("resultParser", resultParser);
        } else {
            log.warn("resultParser 为 null，无法注册到 SpEL 上下文");
        }
        if (anticoagulantParser != null) {
            spelCtx.setVariable("anticoagulantParser", anticoagulantParser);
        } else {
            log.warn("anticoagulantParser 为 null，无法注册到 SpEL 上下文");
        }
        spelCtx.setVariable("ctx", ctx);

        // ========== 4. 解析 rules 中的 SpEL 表达式（跳过 processor 键） ==========
        if (extraction.getRules() != null && !extraction.getRules().isEmpty()) {
            Map<String, Object> resolvedRules = (Map<String, Object>) resolveSpelIfNeeded(extraction.getRules(), spelCtx);
            extraction.setRules(resolvedRules);
            log.info(">>> 【SpEL解析后】rules={}", resolvedRules);
        }

        // 如果已有 slots，直接返回
        if (intentResult.getSlots() != null && !intentResult.getSlots().isEmpty()) {
            Map<String, Object> cleaned = removeNullValues(intentResult.getSlots());
            log.info("意图 {} 已有参数，过滤后返回 {} 个有效参数",
                intentResult.getIntentCode(), cleaned.size());
            return cleaned;
        }

        // ========== 5. 处理器模式（processor），优先级最高 ==========
        if (extraction.getRules() != null && extraction.getRules().containsKey("processor")) {
            Object processorExpr = extraction.getRules().get("processor");
            if (processorExpr instanceof String) {
                try {
                    String expr = (String) processorExpr;
                    if (expr.startsWith("#{") && expr.endsWith("}")) {
                        expr = "#" + expr.substring(2, expr.length() - 1);
                    }
                    log.info(">>> 准备执行 SpEL 表达式: {}", expr);
                    Object result = spelParser.parseExpression(expr).getValue(spelCtx);



                    if (result instanceof Map) {
                        Map<String, Object> processorResult = (Map<String, Object>) result;
                        if (!processorResult.isEmpty()) {
                            // ★ 按 extraction.outputs 把 parser 返回的短名映射为 context 全名
                            Map<String, String> outputs = extraction.getOutputs();
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            for (Map.Entry<String, Object> e : processorResult.entrySet()) {
                                String shortName = e.getKey();
                                if (shortName.contains(".")) {
                                    mapped.put(shortName, e.getValue());   // 已是全名，不动
                                    continue;
                                }
                                if (outputs != null && outputs.containsKey(shortName)) {
                                    mapped.put(outputs.get(shortName), e.getValue());
                                } else {
                                    log.warn("意图 {} 的 outputs 里找不到 {} 的映射，丢弃",
                                        intentResult.getIntentCode(), shortName);
                                }
                            }
                            log.info("意图 {} 处理器抽取成功，参数（映射前）：{}",
                                intentResult.getIntentCode(), processorResult);
                            log.info("意图 {} 处理器抽取成功，参数（映射后）：{}",
                                intentResult.getIntentCode(), mapped);
                            saveExtractionMemory(sessionId, ctx);
                            intentResult.setSlots(mapped);
                            return mapped;
                        }
                    } else {



                        log.warn("处理器返回结果不是 Map，实际类型: {}，意图码: {}",
                            result == null ? "null" : result.getClass().getSimpleName(),
                            intentResult.getIntentCode());
                    }
                } catch (Exception e) {
                    log.error("处理器 SpEL 执行失败: {}，异常原因: {}", processorExpr, e.getMessage());
                    log.error("异常详细信息: ", e);
                    log.error("执行失败时 drugParser 是否存在: {}",
                        spelCtx.lookupVariable("drugParser") != null ? "是" : "否");
                }
            }
        }

        // ========== 6. 正则抽取 ==========
        if (extraction.getRules() != null && extraction.getRules().containsKey("patterns")) {
            Map<String, Object> regexResult = extractWithRegex(rawText, extraction);
            if (!regexResult.isEmpty()) {
                log.info("意图 {} 正则抽取成功，参数：{}", intentResult.getIntentCode(), regexResult);
                intentResult.setSlots(regexResult);
                return regexResult;
            } else {
                log.info(">>> 【正则失败，即将进入 LLM 兜底】intentCode={}", intentResult.getIntentCode());
            }
        }

        // ========== 7. method = "none" 时跳过 LLM ==========
        if ("none".equals(extraction.getMethod())) {
            log.info("意图 {} 配置 method=none，且正则未提取到参数，返回空", intentResult.getIntentCode());
            intentResult.setSlots(Collections.emptyMap());
            return Collections.emptyMap();
        }

        // ========== 8. LLM 兜底 ==========
        log.info(">>> 【LLM兜底】intentCode={}, 因正则未提取到参数，开始调用大模型", intentResult.getIntentCode());
        try {
            Map<String, String> promptPair = promptTemplateService.buildExtractionPromptPair(intentResult);
            if (promptPair == null || promptPair.isEmpty()) {
                log.warn("意图 {} 无有效的抽取提示词", intentResult.getIntentCode());
                return Collections.emptyMap();
            }

            String systemPrompt = promptPair.get("system");
            String userPrompt = promptPair.get("user");
            List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
            );
            ChatResponse response = chatModel.chat(messages);
            String llmResponse = response.aiMessage().text();
            log.info(">>> LLM 原始响应: {}", llmResponse);

            Map<String, Object> extractedParams = parseJsonToMap(llmResponse);
            if (extractedParams == null) {
                extractedParams = Collections.emptyMap();
            }
            // ★ 新增：LLM 可能返回嵌套结构（如 {intervention={lastDrug=x}}），展平为点分全名
            Map<String, Object> flatParams = flattenNestedMap(extractedParams);
            Map<String, Object> cleanedParams = removeNullValues(flatParams);

            log.info("意图 {} LLM 抽取成功，参数：{}", intentResult.getIntentCode(), cleanedParams);
            saveExtractionMemory(sessionId, ctx);




            intentResult.setSlots(cleanedParams);
            return cleanedParams;

        } catch (Exception e) {
            log.error("LLM 抽取失败，意图码: {}", intentResult.getIntentCode(), e);
            intentResult.setSlots(Collections.emptyMap());
            return Collections.emptyMap();
        }
    }

    // ==================== ★ 新增：trg.slots 回退抽取 ====================

    /**
     * 从 trg.slots 抽取：只处理 extract_by=regex 的槽位。
     *
     * <p>用于全局意图（无 extraction 定义但 trg.slots 有槽位定义）的参数抽取。</p>
     */
    private Map<String, Object> extractFromTrgSlots(Map<String, SlotSpec> slots, String rawText) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (rawText == null || rawText.isBlank()) {
            return result;
        }

        for (Map.Entry<String, SlotSpec> entry : slots.entrySet()) {
            String slotName = entry.getKey();
            SlotSpec spec = entry.getValue();
            if (spec == null) continue;
            if (!"regex".equals(spec.getExtractBy())) continue;

            // ★ 改动 2：抽出 rawValue 后经 coerceSlotValue 归一化再写入
            Object rawValue = matchSlot(spec, rawText);
            if (rawValue != null) {
                String targetVar = resolveTargetVar(slotName);
                Object value = coerceSlotValue(targetVar, rawValue);
                result.put(targetVar, value);
                log.debug("[SlotExtract] {} -> {} = {} (raw={})",
                    slotName, targetVar, value, rawValue);
            }
        }
        return result;
    }

    /**
     * 用 SlotSpec 的 patterns 匹配 rawText，返回 enum key。
     *
     * <p>多个 pattern 命中时，按 pattern 长度降序优先，避免短 pattern 误伤长 pattern。</p>
     */
    private Object matchSlot(SlotSpec spec, String rawText) {
        Map<String, List<String>> patterns = spec.getPatterns();
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }

        // 收集所有 (enumKey, pattern) 对，按 pattern 长度降序
        List<Map.Entry<String, String>> candidates = new ArrayList<>();
        for (Map.Entry<String, List<String>> p : patterns.entrySet()) {
            String enumValue = p.getKey();
            List<String> regexes = p.getValue();
            if (regexes == null) continue;
            for (String regex : regexes) {
                if (regex == null || regex.isBlank()) continue;
                candidates.add(new AbstractMap.SimpleEntry<>(enumValue, regex));
            }
        }
        candidates.sort((a, b) -> Integer.compare(b.getValue().length(), a.getValue().length()));

        for (Map.Entry<String, String> c : candidates) {
            String enumValue = c.getKey();
            String regex = c.getValue();
            try {
                if (Pattern.compile(regex).matcher(rawText).find()) {
                    return enumValue;
                }
            } catch (Exception e) {
                log.warn("槽位正则无效: '{}'", regex);
            }
        }
        return null;
    }

    /**
     * 槽位名 → CaseContext 变量名。
     * 先用显式映射表，未命中则透传槽位名。
     */
    private String resolveTargetVar(String slotName) {
        return SLOT_TO_VAR.getOrDefault(slotName, slotName);
    }

    // ★ 改动 3：新增 coerceSlotValue 方法
    /**
     * 把槽位枚举值按目标变量的类型要求归一化。
     *
     * <p>例：{@code phys.hasPulse} 是 boolean，"present"/"absent" 需转 true/false；
     * "unknown" 保留字符串（_variables.json 的 value_labels 允许）。</p>
     *
     * <p>未在 {@link #SLOT_VALUE_COERCION} 中登记的变量，原值透传。</p>
     */
    private Object coerceSlotValue(String targetVar, Object rawValue) {
        if (targetVar == null || rawValue == null) {
            return rawValue;
        }
        Map<String, Object> mapping = SLOT_VALUE_COERCION.get(targetVar);
        if (mapping == null) {
            return rawValue;
        }
        String key = String.valueOf(rawValue).toLowerCase(Locale.ROOT);
        Object mapped = mapping.get(key);
        if (mapped == null) {
            log.debug("槽位值 '{}' 在 {} 的转换表中未命中，原值透传", rawValue, targetVar);
            return rawValue;
        }
        return mapped;
    }

    /**
     * 槽位名到变量的默认映射。
     *
     * <p>未列入的槽位会透传原名，契约诊断会提示"被读但无人写"。</p>
     */
    private static final Map<String, String> SLOT_TO_VAR = Map.ofEntries(
        // ========== ABCs ==========
        Map.entry("scene_safety",              "phys.sceneSafety"),
        Map.entry("approach_action",           "process.approachAction"),
        Map.entry("stimulus",                  "process.stimulusType"),
        Map.entry("response",                  "phys.responsiveness"),
        Map.entry("pulse_site",                "phys.pulseSite"),
        Map.entry("pulse_present",             "phys.hasPulse"),
        Map.entry("breathing_present",         "phys.breathingPresent"),
        Map.entry("technique",                 "process.breathingTechnique"),

        // ========== 病史 ==========
        Map.entry("medication",                "process.medicationList"),
        Map.entry("medication_context",        "process.medicationContext"),
        Map.entry("anticoagulant",             "process.anticoagulantDrug"),
        Map.entry("anticoagulant_status",      "process.anticoagulationAssessed"),
        Map.entry("hpi",                       "process.hpi"),
        Map.entry("pmh",                       "process.pmh"),
        Map.entry("substance",                 "process.substanceUse"),
        Map.entry("frequency",                 "process.substanceFrequency"),

        // ========== 生命体征 ==========
        Map.entry("systolic",                  "phys.systolicBp"),
        Map.entry("diastolic",                 "phys.diastolicBp"),
        Map.entry("bp_location",               "phys.bpLocation"),
        Map.entry("heart_rate",                "phys.heartRate"),
        Map.entry("rhythm",                    "phys.rhythmRegularity"),
        Map.entry("spo2",                      "phys.spo2"),
        Map.entry("on_oxygen",                 "phys.onOxygen"),
        Map.entry("respiratory_rate",          "phys.respiratoryRate"),
        Map.entry("pattern",                   "phys.respiratoryPattern"),
        Map.entry("glucometer_ready",          "device.glucometerReady"),
        Map.entry("strip_ready",               "device.stripReady"),
        Map.entry("glucose_value",             "phys.bloodGlucose"),
        Map.entry("unit",                      "phys.glucoseUnit"),
        Map.entry("pupil_left",                "phys.pupilLeft"),
        Map.entry("pupil_right",               "phys.pupilRight"),
        Map.entry("color",                     "phys.skinColor"),
        Map.entry("temperature",               "phys.skinTemperature"),
        Map.entry("moisture",                  "phys.skinMoisture"),
        Map.entry("orientation_person",        "phys.orientationPerson"),
        Map.entry("orientation_place",         "phys.orientationPlace"),
        Map.entry("orientation_time",          "phys.orientationTime"),
        Map.entry("consciousness",             "phys.consciousness"),

        // ========== 卒中评估 ==========
        Map.entry("grip_left",                 "phys.gripLeft"),
        Map.entry("grip_right",                "phys.gripRight"),
        Map.entry("symmetry",                  "process.gripSymmetryChecked"),
        Map.entry("face_symmetry",             "process.faceDroopChecked"),
        Map.entry("side",                      "process.strokeSide"),
        Map.entry("arm_drift",                 "process.armDriftChecked"),
        Map.entry("duration_seconds",          "process.armDriftDurationSec"),
        Map.entry("speech_clarity",            "process.speechChecked"),
        Map.entry("repeated_phrase",           "process.repeatedPhrase"),
        Map.entry("balance",                   "process.balanceChecked"),
        Map.entry("coordination",              "process.coordinationChecked"),
        Map.entry("lkw_time",                  "process.lkwTime"),
        Map.entry("witness",                   "process.lkwWitness"),
        Map.entry("vision_deficit",            "process.visionDeficit"),
        Map.entry("field_cut",                 "process.visionFieldCut"),
        Map.entry("aphasia_type",              "process.aphasia"),
        Map.entry("severity",                  "process.aphasiaSeverity"),
        Map.entry("neglect_side",              "process.neglectSigns"),
        Map.entry("present",                   "process.neglectPresent"),

        // ========== 12 导联 ECG ==========
        Map.entry("explanation_done",          "process.ecgExplained"),
        Map.entry("patient_consent",           "process.ecgConsent"),
        Map.entry("shave_site",                "device.shaveSite"),
        Map.entry("shave_done",                "device.shaveDone"),
        Map.entry("lead_label",                "device.ecgLeadLabel"),
        Map.entry("placement_done",            "device.ecgLeadPlaced"),
        Map.entry("v1_position",               "device.v1Position"),
        Map.entry("v2_position",               "device.v2Position"),
        Map.entry("v3_position",               "device.v3Position"),
        Map.entry("v4_position",               "device.v4Position"),
        Map.entry("v5_position",               "device.v5Position"),
        Map.entry("v6_position",               "device.v6Position"),
        Map.entry("instruction_given",         "process.patientStill"),
        Map.entry("patient_movement",          "process.patientMovement"),
        Map.entry("ecg_power",                 "device.ecgPower"),
        Map.entry("leads_connected",           "device.leadsConnected"),
        Map.entry("ecg_device",                "device.ecgDeviceName"),
        Map.entry("age",                       "clinical.age"),
        Map.entry("sex",                       "clinical.gender"),
        Map.entry("ecg_recording",             "device.ecgRecorded"),
        Map.entry("artifact_id",               "process.ecgArtifactId"),
        Map.entry("interpretation",            "process.ecgInterpretation"),
        Map.entry("reported",                  "process.ecgReported"),

        // ========== CPR ==========
        Map.entry("cpr_responsiveness",        "process.responsiveness"),
        Map.entry("compression_pause",         "process.compressionPaused"),
        Map.entry("cpr_pulse",                 "phys.hasPulse"),
        Map.entry("cpr_breathing",             "phys.breathingPresent"),
        Map.entry("compression_rate",          "process.compressionRate"),
        Map.entry("compression_depth",         "process.compressionDepth"),
        Map.entry("continuous",                "process.compressionContinuous"),
        Map.entry("reassessment",              "process.reassessed"),
        Map.entry("rosc",                      "clinical.rosc"),

        // ========== 除颤/AED ==========
        Map.entry("aed_requested",             "process.aedRequested"),
        Map.entry("requested_to",              "process.aedRequestedTo"),
        Map.entry("assistance_type",           "process.assistanceType"),
        Map.entry("requested",                 "process.assistanceRequested"),
        Map.entry("aed_power",                 "device.aedOn"),
        Map.entry("pads_position",             "device.defibPadsPosition"),
        Map.entry("chest_exposed",             "process.chestExposed"),
        Map.entry("clear_analysis",            "process.clearForAnalysis"),
        Map.entry("no_contact",                "process.noContactDuringAnalysis"),
        Map.entry("clear_shock",               "process.clearForShock"),
        Map.entry("all_clear",                 "process.allClearForShock"),
        Map.entry("shock_delivered",           "process.shockDone"),
        Map.entry("shock_count",               "process.shockCount"),

        // ========== 通气/气道 ==========
        Map.entry("airway_maneuver",           "device.airwayManeuver"),
        Map.entry("airway_patent",             "device.airwayPatent"),
        Map.entry("bvm_seal",                  "device.bvmSealed"),
        Map.entry("bvm_placed",                "device.bvmPlaced"),
        Map.entry("ventilation_rate",          "process.ventilationRate"),
        Map.entry("chest_rise",                "process.chestRecoil"),
        Map.entry("ventilation_done",          "process.ventilationDone"),
        Map.entry("suction_done",              "process.suctionDone"),
        Map.entry("secretion",                 "process.airwaySecretion"),
        Map.entry("npa_size",                  "device.npaSize"),
        Map.entry("npa_inserted",              "device.npaPlaced"),

        // ========== 转运 ==========
        Map.entry("transport_target",          "process.transportTarget"),
        Map.entry("transport_started",         "process.transportStarted"),
        Map.entry("stretcher_loaded",          "process.stretcherLoaded"),
        Map.entry("transfer_method",           "process.transferMethod"),
        Map.entry("secured",                   "process.patientSecured"),
        Map.entry("straps_count",              "process.strapsCount"),
        Map.entry("hospital_notified",         "process.hospitalNotified"),
        Map.entry("eta",                       "process.eta"),
        Map.entry("patient_report",            "process.patientReport"),
        Map.entry("stroke_alert_activated",    "process.strokeAlertActivated"),
        Map.entry("stroke_team_notified",      "process.strokeTeamNotified"),
        Map.entry("handoff_done",              "process.handoffDone"),
        Map.entry("handoff_format",            "process.handoffFormat")
    );

    // ★ 改动 1：新增槽位值归一化表
    /**
     * 槽位枚举值 → 目标变量值的归一化表。
     *
     * <p>背景：意图槽位声明为 enum（present/absent），但目标变量可能是 boolean。
     * EmsContextManager 按 _variables.json 的类型校验，类型不符会拒收
     * （日志："跳过非法变量值: phys.hasPulse=absent"）。这里按目标变量名归一化。</p>
     *
     * <p>未登记的变量原值透传。</p>
     */
    private static final Map<String, Map<String, Object>> SLOT_VALUE_COERCION = Map.of(
        "phys.hasPulse", Map.of(
            "present", Boolean.TRUE,
            "absent",  Boolean.FALSE,
            "unknown", "unknown"      // meta value_labels 允许 "unknown"
        )
        // 未来若 phys.breathingPresent 等也是 boolean，在此追加一行
    );

    // ==================== 记忆持久化 ====================

    /**
     * 将记忆持久化到 CaseContext。
     *
     * <p>走语义化入口 batchSetVarsWithVersion，不再直接调用 private 的 updateWithVersion。</p>
     */
    private void saveExtractionMemory(String caseInstanceId, ExtractionContext ctx) {
        if (caseInstanceId == null) {
            return;
        }
        Map<String, Object> memoryToSave = ctx.getMemoryForSave();
        if (memoryToSave.isEmpty()) {
            return;
        }

        CaseContext latest = contextManager.getSnapshot(caseInstanceId);
        long currentVersion = latest != null ? latest.getVersion() : 0L;

        Map<String, Object> varMap = Map.of("__extraction_memory", memoryToSave);

        boolean success = contextManager.batchSetVarsWithVersion(
            caseInstanceId,
            varMap,
            "ParameterExtractor",
            currentVersion
        );

        if (!success) {
            CaseContext retryCtx = contextManager.getSnapshot(caseInstanceId);
            long retryVersion = retryCtx != null ? retryCtx.getVersion() : 0L;
            success = contextManager.batchSetVarsWithVersion(
                caseInstanceId,
                varMap,
                "ParameterExtractor",
                retryVersion
            );
            if (!success) {
                log.warn("抽取记忆更新失败（重试后仍冲突），caseInstanceId={}", caseInstanceId);
                return;
            }
            log.info("抽取记忆更新成功（重试）: {}", memoryToSave);
            return;
        }

        log.info("抽取记忆已持久化: {}", memoryToSave);
    }

    /**
     * 解析对象中的 SpEL 表达式，但跳过键名为 "processor" 的字段
     */
    private Object resolveSpelIfNeeded(Object value, EvaluationContext context) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                if ("processor".equals(key)) {
                    resolved.put(key, entry.getValue());
                } else {
                    resolved.put(key, resolveSpelIfNeeded(entry.getValue(), context));
                }
            }
            return resolved;
        }

        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> resolved = new ArrayList<>();
            for (Object item : list) {
                resolved.add(resolveSpelIfNeeded(item, context));
            }
            return resolved;
        }

        if (value instanceof String) {
            String str = (String) value;
            if (str.startsWith("#{") && str.endsWith("}")) {
                String expression = str.substring(2, str.length() - 1);
                try {
                    Object result = spelParser.parseExpression(expression).getValue(context);
                    log.info(">>> 【SpEL解析】表达式: {} -> 结果: {}", expression, result);
                    return result;
                } catch (Exception e) {
                    log.error("SpEL 解析失败: {}", expression, e);
                    return str;
                }
            }
            return str;
        }

        return value;
    }

    // ==================== 正则抽取 / 工具方法 ====================

    private Map<String, Object> extractWithRegex(String rawText, ExtractionConfig config) {
        Map<String, Object> result = new HashMap<>();
        List<String> targetParams = config.getTargetParams();
        if (targetParams == null || targetParams.isEmpty()) {
            log.info(">>> 【正则】targetParams 为空，无法抽取");
            return result;
        }

        Map<String, Object> rules = config.getRules();
        if (rules == null) {
            log.info(">>> 【正则】rules 为空");
            return result;
        }

        Object patternsObj = rules.get("patterns");
        if (!(patternsObj instanceof List)) {
            log.info(">>> 【正则】patterns 不是 List 类型，实际类型：{}",
                patternsObj == null ? "null" : patternsObj.getClass().getSimpleName());
            return result;
        }

        @SuppressWarnings("unchecked")
        List<String> patterns = (List<String>) patternsObj;

        log.info(">>> 【正则】开始匹配，rawText='{}', 共有 {} 条正则，targetParams={}",
            rawText, patterns.size(), targetParams);

        int patternIndex = 0;
        for (String patternStr : patterns) {
            patternIndex++;
            log.info(">>> 【正则-{}/{}】尝试匹配: {}", patternIndex, patterns.size(), patternStr);

            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(rawText);

            if (matcher.find()) {
                int groupCount = matcher.groupCount();
                log.info(">>> 【正则-{}/{}】✅ 匹配成功！捕获组数量: {}", patternIndex, patterns.size(), groupCount);

                for (int i = 1; i <= groupCount; i++) {
                    String groupValue = matcher.group(i);
                    log.info(">>>   group[{}] = '{}'", i, groupValue);
                }

                for (int i = 0; i < targetParams.size(); i++) {
                    if (i < groupCount) {
                        String value = matcher.group(i + 1);
                        if (value != null && !value.trim().isEmpty()) {
                            result.put(targetParams.get(i), value.trim());
                            log.info(">>>   映射: {} -> '{}'", targetParams.get(i), value.trim());
                        } else {
                            log.info(">>>   group[{}] 为空或空白，跳过映射到 {}", i + 1, targetParams.get(i));
                        }
                    } else {
                        log.info(">>>   捕获组数量不足，targetParams[{}]={} 无对应 group",
                            i, targetParams.get(i));
                    }
                }

                if (!result.isEmpty()) {
                    log.info(">>> 【正则】提取完成，返回结果: {}", result);
                    return removeNullValues(result);
                } else {
                    log.info(">>> 【正则】匹配成功但所有捕获组均为空或不在 targetParams 中，继续尝试下一条正则");
                    result.clear();
                }
            } else {
                log.info(">>> 【正则-{}/{}】❌ 匹配失败", patternIndex, patterns.size());
            }
        }

        log.info(">>> 【正则】所有正则均未提取到有效参数，返回空 Map");
        return Collections.emptyMap();
    }

    private Map<String, Object> removeNullValues(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                log.debug("跳过 null 参数: {}", key);
                continue;
            }

            if (value instanceof Map) {
                Map<String, Object> cleanedNested = removeNullValues((Map<String, Object>) value);
                if (!cleanedNested.isEmpty()) {
                    cleaned.put(key, cleanedNested);
                }
            } else if (value instanceof List) {
                List<Object> cleanedList = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item == null) continue;
                    if (item instanceof Map) {
                        Map<String, Object> cleanedItem = removeNullValues((Map<String, Object>) item);
                        if (!cleanedItem.isEmpty()) {
                            cleanedList.add(cleanedItem);
                        }
                    } else {
                        cleanedList.add(item);
                    }
                }
                if (!cleanedList.isEmpty()) {
                    cleaned.put(key, cleanedList);
                }
            } else {
                cleaned.put(key, value);
            }
        }
        return cleaned;
    }

    private Map<String, Object> parseJsonToMap(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        String trimmed = rawResponse.trim();

        try {
            return MAPPER.readValue(trimmed, Map.class);
        } catch (Exception e) {
            log.debug("直接解析失败，尝试其他方式");
        }

        Pattern markdownPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        Matcher m = markdownPattern.matcher(trimmed);
        if (m.find()) {
            String jsonCandidate = m.group(1).trim();
            try {
                return MAPPER.readValue(jsonCandidate, Map.class);
            } catch (Exception e) {
                log.debug("Markdown 提取的 JSON 解析失败，继续尝试");
            }
        }

        int startObj = trimmed.indexOf('{');
        int endObj = trimmed.lastIndexOf('}');
        if (startObj >= 0 && endObj > startObj) {
            String jsonCandidate = trimmed.substring(startObj, endObj + 1);
            try {
                return MAPPER.readValue(jsonCandidate, Map.class);
            } catch (Exception e) {
                log.debug("提取的 JSON 对象解析失败，尝试数组提取");
            }
        }

        int startArr = trimmed.indexOf('[');
        int endArr = trimmed.lastIndexOf(']');
        if (startArr >= 0 && endArr > startArr) {
            String jsonCandidate = trimmed.substring(startArr, endArr + 1);
            try {
                List<Object> list = MAPPER.readValue(jsonCandidate, List.class);
                if (!list.isEmpty()) {
                    Map<String, Object> wrapped = new HashMap<>();
                    wrapped.put("result", list);
                    log.warn("LLM 返回数组，已包装为 Map，请检查提示词");
                    return wrapped;
                }
            } catch (Exception e) {
                log.debug("数组提取解析失败");
            }
        }

        log.error("无法从 LLM 响应中提取 JSON，原始内容：\n{}", rawResponse);
        return Collections.emptyMap();
    }

    /**
     * 将 LLM 返回的嵌套 Map 展平为点分全名。
     *
     * <p>例：{intervention={lastDrug=x, lastDrugDose=y}} -> {"intervention.lastDrug": x, "intervention.lastDrugDose": y}
     *
     * <p>规则：
     * <ul>
     *   <li>value 是 Map → 递归展平，key 用 "parent.child" 形式拼接</li>
     *   <li>value 非 Map（标量/null/List）→ key 原样保留，不做处理</li>
     * </ul>
     *
     * <p>目的：兜底 LLM 不遵守 prompt 约束、输出嵌套 JSON 的情况。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenNestedMap(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input == null || input.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> e : input.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (value instanceof Map) {
                Map<String, Object> subMap = flattenNestedMap((Map<String, Object>) value);
                for (Map.Entry<String, Object> sub : subMap.entrySet()) {
                    result.put(key + "." + sub.getKey(), sub.getValue());
                }
            } else {
                result.put(key, value);
            }
        }
        return result;
    }
}

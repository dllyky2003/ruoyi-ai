package com.ruoyi.ems.intent.classifier;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;

import com.ruoyi.ems.intent.model.routing.RouteScoringEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteIntentClassifier implements IntentClassifier {

    private final CaseIntentDefinitionsLoader loader;
    private final RouteScoringEngine engine;

    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
    private volatile boolean warmedUp = false;

    // ==================== 接口实现 ====================

    @Override
    public String getMode() {
        return "route_v1_3";
    }

    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (!warmedUp) {
            synchronized (this) {
                if (!warmedUp) warmUp();
            }
        }
        if (asrText == null || asrText.trim().isEmpty()) {
            return buildReject(asrText, "EMPTY_ASR");
        }

        RouteScoringEngine.RouteInput input = buildInput(asrText, context);
        RouteScoringEngine.RouteResult result = engine.route(input);
        return toCommand(result, asrText);
    }

    @Override
    public void warmUp() {
        long t0 = System.currentTimeMillis();
        int count = 0;
        for (IntentDefinition intent : loader.getAllIntentList()) {
            if (intent.getTrg() == null) continue;
            count += compileAll(intent.getTrg().getRegex());
            count += compileAll(intent.getTrg().getNegativeRegex());
        }
        warmedUp = true;
        log.info("RouteIntentClassifier warmUp 完成：预编译 {} 条正则，耗时 {} ms",
            count, System.currentTimeMillis() - t0);
    }

    // ==================== Context → RouteInput ====================

    private RouteScoringEngine.RouteInput buildInput(String asrText, ClassificationContext ctx) {
        RouteScoringEngine.RouteInput input = new RouteScoringEngine.RouteInput();
        input.setAsrText(asrText);
        input.setCurrentSentence(asrText);
        if (ctx == null) {
            input.setStateTags(Collections.emptySet());
            input.setContextVars(Collections.emptyMap());
            input.setSlotConfidence(Collections.emptyMap());
            return input;
        }

        input.setCaseId(ctx.getCaseId());

        // 1. asrSentences ← recentDialogs.userMessage
        List<String> sentences = new ArrayList<>();
        if (ctx.getRecentDialogs() != null) {
            for (ClassificationContext.DialogTurn turn : ctx.getRecentDialogs()) {
                if (turn != null && turn.getUserMessage() != null) {
                    sentences.add(turn.getUserMessage());
                }
            }
        }
        sentences.add(asrText);
        input.setAsrSentences(sentences);

        // 2. stateTags ← currentState + sessionMode + extra["stateTags"]
        Set<String> stateTags = new HashSet<>();
        if (notEmpty(ctx.getCurrentState())) stateTags.add(ctx.getCurrentState());
        if (notEmpty(ctx.getSessionMode())) stateTags.add(ctx.getSessionMode());
        mergeStringCollection(ctx.getExtra() == null ? null : ctx.getExtra().get("stateTags"), stateTags);
        input.setStateTags(stateTags);

        // 3. contextVars ← variables + extra + 关键标量
        Map<String, Object> vars = new HashMap<>();
        if (ctx.getVariables() != null) vars.putAll(ctx.getVariables());
        if (ctx.getExtra() != null) vars.putAll(ctx.getExtra());
        if (notEmpty(ctx.getCurrentState())) vars.put("currentState", ctx.getCurrentState());
        if (notEmpty(ctx.getSessionMode())) vars.put("sessionMode", ctx.getSessionMode());
        if (notEmpty(ctx.getLastIntent())) vars.put("lastIntent", ctx.getLastIntent());
        if (notEmpty(ctx.getClockStatus())) vars.put("clockStatus", ctx.getClockStatus());
        vars.put("elapsedSeconds", ctx.getElapsedSeconds());
        if (ctx.getAvailableIntents() != null) vars.put("__available_intents__", ctx.getAvailableIntents());
        if (ctx.getGlobalEventIntents() != null) vars.put("__global_event_intents__", ctx.getGlobalEventIntents());
        input.setContextVars(vars);

        // 4. slotConfidence ← extra["slotConfidence"]
        Map<String, Double> slotConf = new HashMap<>();
        if (ctx.getExtra() != null && ctx.getExtra().get("slotConfidence") instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof Number n) {
                    slotConf.put(e.getKey().toString(), n.doubleValue());
                }
            }
        }
        input.setSlotConfidence(slotConf);

        return input;
    }

    // ==================== RouteResult → IntentCommand ====================

    /**
     * ★ 这里按 IntentCommand 是 @Builder 风格写的。
     *  如果你的 IntentCommand 字段名不同，只需要改这一个方法。
     */
    private IntentCommand toCommand(RouteScoringEngine.RouteResult result, String asrText) {
        String decisionMode = result.getDecisionMode();
        String selectedCode = result.getSelectedIntentCode();

        // 1. 得分
        Object finalScore = result.getScoreBreakdown().get("final");
        double score = finalScore instanceof Number ? ((Number) finalScore).doubleValue() : 0.0;

        // 2. trace 汇总（放进 matchDetail）
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("decisionMode", decisionMode);
        detail.put("scoreBreakdown", result.getScoreBreakdown());
        detail.put("gates", result.getGates());
        detail.put("negativeEvidence", result.getNegativeEvidence());
        detail.put("regexEvidence", result.getRegexEvidence());
        detail.put("missingRequiredSlots", result.getMissingRequiredSlots());

        // 3. 候选列表
        List<IntentCandidate> candidates = new ArrayList<>();
        if (result.getTopCandidates() != null) {
            for (IntentDefinition def : result.getTopCandidates()) {
                if (def == null) continue;
                candidates.add(IntentCandidate.builder()
                    .intentCode(def.getIntentCode())
                    .nameCn(def.getNameCn())
                    .score(score)               // ← 若 IntentCandidate 有 score 字段
                    .build());
            }
        }

        // 4. matchedDefinition
        IntentDefinition matched = (selectedCode != null) ? loader.getIntent(selectedCode) : null;

        // 5. 组装
        boolean top1 = "TOP1".equals(decisionMode);
        return IntentCommand.builder()
            .intentCode(selectedCode)
            .confidence(score)
            .rawText(asrText)
            .source("route")
            .classifyMode("route_v1_3")
            .valid(top1)
            .reason(buildReason(decisionMode, selectedCode, score))
            .matchDetail(detail)
            .candidates(candidates)
            .matchedDefinition(matched)
            .slots(Collections.emptyMap())
            .build();
    }

    private String buildReason(String mode, String code, double score) {
        if ("TOP1".equals(mode)) {
            return String.format("路由命中 %s (score=%.3f)", code, score);
        }
        if ("TOP2".equals(mode)) {
            return String.format("路由候选不唯一，需澄清 (top1_score=%.3f)", score);
        }
        return "路由拒绝：无候选通过门控";
    }

    private IntentCommand buildReject(String asrText, String errorMessage) {
        return IntentCommand.noMatch("route", errorMessage);
    }


    // ==================== 工具 ====================

    private boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private void mergeStringCollection(Object src, Set<String> dest) {
        if (src instanceof Collection<?> c) {
            for (Object o : c) if (o != null) dest.add(o.toString());
        } else if (src instanceof String s && !s.isEmpty()) {
            dest.add(s);
        }
    }

    private int compileAll(List<String> regexes) {
        if (regexes == null || regexes.isEmpty()) return 0;
        int c = 0;
        for (String r : regexes) {
            if (r == null || r.isEmpty()) continue;
            patternCache.computeIfAbsent(r, k -> {
                try { return Pattern.compile(k); }
                catch (Exception e) {
                    log.warn("非法正则已跳过：{}", k);
                    return null;
                }
            });
            c++;
        }
        return c;
    }
}

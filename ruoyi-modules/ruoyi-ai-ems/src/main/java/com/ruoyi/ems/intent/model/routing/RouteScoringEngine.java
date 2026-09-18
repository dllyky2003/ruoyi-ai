package com.ruoyi.ems.intent.model.routing;



import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.routing.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RouteScoringEngine {

    @Autowired
    private CaseIntentDefinitionsLoader loader;

    /** ASR 输入上下文 */
    @Data
    public static class RouteInput {
        private String caseId;
        private String asrText;
        private List<String> asrSentences;      // 可选：用于 same_sentence / same_window
        private String currentSentence;         // 当前句
        private Set<String> stateTags = new HashSet<>();
        private Map<String, Object> contextVars = new HashMap<>();
        private Map<String, Double> slotConfidence = new HashMap<>();  // slotName -> confidence
    }

    /** 路由结果 */
    @Data
    public static class RouteResult {
        private String selectedIntentCode;
        private String decisionMode;      // TOP1|TOP2|REJECT
        private Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
        private Map<String, Object> gates = new LinkedHashMap<>();
        private Map<String, Object> negativeEvidence = new LinkedHashMap<>();
        private Map<String, Object> regexEvidence = new LinkedHashMap<>();
        private List<String> missingRequiredSlots = new ArrayList<>();
        private List<IntentDefinition> topCandidates = new ArrayList<>();
    }

    public RouteResult route(RouteInput input) {
        List<IntentDefinition> candidates = new ArrayList<>();
        List<Scored> scored = new ArrayList<>();

        for (IntentDefinition intent : loader.getAllIntentList()) {

            if (input.getCaseId() != null
                && intent.isSpecific()
                && !input.getCaseId().equals(intent.getCaseCode())) {
                continue;
            }


            Scored s = score(intent, input);
            if (s != null && !s.hardBlocked) scored.add(s);
        }
        scored.sort(Comparator.comparingDouble(Scored::getFinalScore).reversed());

        RouteResult result = new RouteResult();
        if (scored.isEmpty()) {
            result.setDecisionMode("REJECT");
            return result;
        }

        Scored top1 = scored.get(0);
        Scored top2 = scored.size() > 1 ? scored.get(1) : null;

        // 应用 confusion_pair penalty
        applyConfusionPenalty(scored);

        double margin = top2 == null ? Double.MAX_VALUE : top1.finalScore - top2.finalScore;
        RouterDefaults.AcceptPolicy accept = loader.getRouterDefaults() != null
            ? loader.getRouterDefaults().getAcceptPolicy() : new RouterDefaults.AcceptPolicy();

        if (top1.finalScore >= accept.getTop1MinScore() && margin >= accept.getTop1Top2Margin()) {
            result.setDecisionMode("TOP1");
            result.setSelectedIntentCode(top1.intent.getIntentCode());
        } else {
            result.setDecisionMode("TOP2");
            result.setSelectedIntentCode(null);
        }

        result.setTopCandidates(scored.stream().limit(3)
            .map(s -> s.intent).collect(Collectors.toList()));
        result.getScoreBreakdown().put("S_trg", top1.sTrg);
        result.getScoreBreakdown().put("S_prior", top1.sPrior);
        result.getScoreBreakdown().put("S_pref", top1.sPref);
        result.getScoreBreakdown().put("final", top1.finalScore);
        result.getGates().put("route_guard_pass", true);
        result.getGates().put("negative_hard_blocked", false);
        result.getGates().put("min_regex_hit_ok", true);
        result.getGates().put("required_slots_ok", top1.missingRequiredSlots.isEmpty());
        result.setMissingRequiredSlots(top1.missingRequiredSlots);
        return result;
    }

    private Scored score(IntentDefinition intent, RouteInput input) {
        if (intent.getTrg() == null || intent.getRouteGuard() == null) return null;

        Scored s = new Scored();
        s.intent = intent;

        // 1. route_guard
        if (!guardPass(intent.getRouteGuard(), input)) {
            s.hardBlocked = true;
            return s;
        }

        // 2. negative_policy hard
        NegativePolicy np = intent.getNegativePolicy() != null
            ? intent.getNegativePolicy()
            : (loader.getRouterDefaults() != null ? loader.getRouterDefaults().getNegativePolicy() : null);
        if (np == null) np = new NegativePolicy();

        String scopeText = resolveScopeText(np.getNegativeScope(), input);
        boolean negHit = matchAny(intent.getTrg().getNegativeRegex(), scopeText);
        if ("hard".equals(np.getMode()) && np.isBlockOnAnyHit() && negHit) {
            s.hardBlocked = true;
            return s;
        }

        // 3. min_regex_hit
        EvidencePolicy ep = intent.getTrg().getEvidencePolicy();
        if (ep == null) ep = new EvidencePolicy();
        int hitCount = countHits(intent.getTrg().getRegex(), scopeText);
        if (hitCount < ep.getMinRegexHit()) {
            s.hardBlocked = true;
            return s;
        }

        // 4. required slots
        Set<String> missingRequired = new HashSet<>();
        if (ep.getSlotRequired() != null && intent.getTrg().getSlots() != null) {
            for (String slotName : ep.getSlotRequired()) {
                SlotSpec spec = intent.getTrg().getSlots().get(slotName);
                if (spec == null) continue;
                Double conf = input.getSlotConfidence().get(slotName);
                if (conf == null || conf < ep.getSlotConfidenceThreshold()) {
                    missingRequired.add(slotName);
                }
            }
        }
        if (!missingRequired.isEmpty() && ep.getRequiredSlotPolicy() != null
            && "reject".equals(ep.getRequiredSlotPolicy().getMode())) {
            s.hardBlocked = true;
            return s;
        }

        // 5. S_trg
        double sRegex = Math.min(1.0, (double) hitCount / Math.max(1, ep.getMinRegexHit()));
        double sRequired = 1.0;
        if (!missingRequired.isEmpty() && ep.getRequiredSlotPolicy() != null
            && "downweight".equals(ep.getRequiredSlotPolicy().getMode())) {
            sRequired = Math.pow(ep.getRequiredSlotPolicy().getDownweightFactor(), missingRequired.size());
        }
        double sOptional = 1.0;
        if (intent.getTrg().getSlots() != null && ep.getOnSlotMissing() != null
            && "downweight".equals(ep.getOnSlotMissing().getMode())) {
            for (Map.Entry<String, SlotSpec> e : intent.getTrg().getSlots().entrySet()) {
                if (Boolean.TRUE.equals(e.getValue().isRequired())) continue;
                Double conf = input.getSlotConfidence().get(e.getKey());
                if (conf == null || conf < ep.getSlotConfidenceThreshold()) {
                    sOptional *= ep.getOnSlotMissing().getFactor();
                }
            }
        }
        s.sTrg = sRegex * sRequired * sOptional;

        if ("soft".equals(np.getMode()) && negHit) {
            s.sTrg *= np.getDownweightFactor();
        }

        // 6. S_prior
        s.sPrior = computePrior(intent, input);

        // 7. S_pref（confusion pair 内部先置 0，由 applyConfusionPenalty 统一处理）
        s.sPref = 0.0;

        // 8. final
        ScoringDefinition sd = loader.getScoringDefinition();
        double wTrg = sd != null ? sd.getWTrg() : 0.65;
        double wPrior = sd != null ? sd.getWPrior() : 0.25;
        double wPref = sd != null ? sd.getWPref() : 0.10;
        s.finalScore = wTrg * s.sTrg + wPrior * s.sPrior + wPref * s.sPref;
        s.missingRequiredSlots = new ArrayList<>(missingRequired);
        return s;
    }

    private boolean guardPass(RouteGuard guard, RouteInput input) {
        boolean hard = "hard".equals(guard.getGuardMode());

        if (guard.getRequiresContext() != null && !contextMatch(guard.getRequiresContext(), input, true)) {
            if (hard) return false;
        }
        if (guard.getExcludesContext() != null && contextMatch(guard.getExcludesContext(), input, false)) {
            if (hard) return false;
        }
        return true;
    }

    private boolean contextMatch(RouteGuardContext ctx, RouteInput input, boolean requireMode) {
        if (ctx.getStateTagsAll() != null && !ctx.getStateTagsAll().isEmpty()) {
            if (!input.getStateTags().containsAll(ctx.getStateTagsAll())) return false;
        }
        if (ctx.getStateTagsAny() != null && !ctx.getStateTagsAny().isEmpty()) {
            boolean any = ctx.getStateTagsAny().stream().anyMatch(input.getStateTags()::contains);
            if (requireMode && !any) return false;
            if (!requireMode && any) return true;
        }
        if (ctx.getContextVars() != null) {
            for (Map.Entry<String, Object> e : ctx.getContextVars().entrySet()) {
                Object v = input.getContextVars().get(e.getKey());
                if (requireMode && !Objects.equals(v, e.getValue())) return false;
                if (!requireMode && Objects.equals(v, e.getValue())) return true;
            }
        }
        return !requireMode;
    }

    private String resolveScopeText(String scope, RouteInput input) {
        if ("same_sentence".equals(scope) && input.getCurrentSentence() != null)
            return input.getCurrentSentence();
        if ("same_window".equals(scope) && input.getAsrSentences() != null)
            return String.join(" ", input.getAsrSentences());
        return input.getAsrText() == null ? "" : input.getAsrText();
    }

    private boolean matchAny(List<String> regexes, String text) {
        if (regexes == null || text == null) return false;
        for (String r : regexes) {
            try { if (Pattern.compile(r).matcher(text).find()) return true; }
            catch (Exception ignore) {}
        }
        return false;
    }

    private int countHits(List<String> regexes, String text) {
        if (regexes == null || text == null) return 0;
        int c = 0;
        for (String r : regexes) {
            try { if (Pattern.compile(r).matcher(text).find()) c++; }
            catch (Exception ignore) {}
        }
        return c;
    }

    private double computePrior(IntentDefinition intent, RouteInput input) {
        ScoringDefinition sd = loader.getScoringDefinition();
        double hw = sd != null ? sd.getSPriorDefinition().getHWeight() : 0.5;
        double aw = sd != null ? sd.getSPriorDefinition().getActWeight() : 0.3;
        double ow = sd != null ? sd.getSPriorDefinition().getObjWeight() : 0.2;

        double sh = intent.getH() != null && !intent.getH().isEmpty()
            && input.getStateTags().contains(intent.getH().get(0)) ? 1.0 : 0.3;
        double sa = intent.getAct() != null && input.getContextVars().containsKey("__act__")
            && intent.getAct().equals(input.getContextVars().get("__act__")) ? 1.0 : 0.3;
        double so = intent.getObj() != null && input.getContextVars().containsKey("__obj_type__")
            && intent.getObj().getType().equals(input.getContextVars().get("__obj_type__")) ? 1.0 : 0.3;

        return hw * sh + aw * sa + ow * so;
    }

    private void applyConfusionPenalty(List<Scored> scored) {
        Map<String, Scored> byCode = scored.stream()
            .collect(Collectors.toMap(s -> s.intent.getIntentCode(), s -> s));
        for (Scored s : scored) {
            if (s.intent.getRouteGuard() == null || s.intent.getRouteGuard().getConfusionPairs() == null) continue;
            for (ConfusionPair cp : s.intent.getRouteGuard().getConfusionPairs()) {
                Scored other = byCode.get(cp.getWith());
                if (other == null) continue;
                double delta = cp.getDelta() != null ? cp.getDelta() : 0.08;
                if (s.finalScore < other.finalScore) {
                    s.finalScore -= delta;
                    s.sPref -= delta;
                }
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::getFinalScore).reversed());
    }

    @Data
    private static class Scored {
        IntentDefinition intent;
        boolean hardBlocked = false;
        double sTrg = 0;
        double sPrior = 0;
        double sPref = 0;
        double finalScore = 0;
        List<String> missingRequiredSlots = new ArrayList<>();
    }
}

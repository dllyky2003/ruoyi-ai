package com.ruoyi.acls.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.acls.cognitive.ReflexRule;
import com.ruoyi.acls.cognitive.ReflexRuleLoader;
import com.ruoyi.acls.contract.ContractSnapshot;
import com.ruoyi.acls.guard.GuardRailLoader;
import com.ruoyi.acls.guard.GuardRule;
import com.ruoyi.acls.skill.registery.YamlSkillLoader;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.*;
import com.ruoyi.ems.domain.DerivationRule;
import com.ruoyi.ems.domain.RuleDefinition;
import com.ruoyi.ems.domain.VariableMeta;
import com.ruoyi.ems.intent.dict.InvertedHit;
import com.ruoyi.ems.intent.model.IntentDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractSnapshotExporter {

    private final YamlStateMachineLoader stateMachineLoader;
    private final VariableMetaLoader variableMetaLoader;
    private final CaseIntentDefinitionsLoader intentLoader;
    private final IntentDictLoader intentDictLoader;
    private final DerivationRuleLoader derivationRuleLoader;
    private final YamlSkillLoader yamlSkillLoader;
    private final GuardRailLoader guardRailLoader;
    private final ReflexRuleLoader reflexRuleLoader;
    private final PhysiologyConfigService physiologyConfigService;
    private final RuleConfigLoader ruleConfigLoader;

    @Value("${acls.snapshot.output-dir:config/contract-snapshot}")
    private String outputDir;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public ContractSnapshot build(String caseId) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config == null) throw new IllegalArgumentException("案例不存在: " + caseId);

        ContractSnapshot snap = new ContractSnapshot();

        // ===== 1. 元数据 =====
        snap.setCaseId(config.getCode());
        snap.setCaseName(config.getCaseName());
        snap.setParent(config.getParent());
        snap.setInitialState(config.getInitialState());
        snap.setTerminalStates(config.getEndStates() != null
            ? new ArrayList<>(config.getEndStates()) : new ArrayList<>());
        snap.setGlobalEventIntents(config.getGlobalEventIntents() != null
            ? new ArrayList<>(config.getGlobalEventIntents()) : new ArrayList<>());
        snap.setResetDefaults(variableMetaLoader.getResetDefaults(caseId));
        snap.setSnapshotVersion("1.0");
        snap.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // ===== 2. 状态模板 =====
        snap.setStateTemplates(buildStateTemplates(config));

        // ===== 3. 意图 =====
        snap.setIntents(extractIntents(caseId, config));

        // ===== 4. 变量 =====
        snap.setVariables(extractVariables(caseId, config));

        // ===== 5. 派生规则 =====
        snap.setDerivations(extractDerivations(caseId));

        // ===== 6. 技能 =====
        snap.setSkills(extractSkills(caseId));

        // ===== 7. 护栏 =====
        snap.setGuardRails(extractGuardRails(caseId));

        // ===== 8. 反射规则 =====
        snap.setReflexRules(extractReflexRules(caseId));

        // ===== 9. 生理基线 =====
        snap.setPhysiologyBaseline(extractPhysiology(caseId));

        // ===== 10. 规则 =====
        snap.setRules(extractRules(caseId));

        // ===== 11. 人工维护 =====
        snap.setGuidelineRules(defaultGuidelineRules());
        snap.setTerminationRules(defaultTerminationRules());

        return snap;
    }

    // ==================================================================
    // 状态模板
    // ==================================================================
    private List<ContractSnapshot.StateTemplate> buildStateTemplates(
        ResolvedCaseTypeConfig config) {
        List<ContractSnapshot.StateTemplate> list = new ArrayList<>();
        Set<String> terminals = new HashSet<>(
            config.getEndStates() != null ? config.getEndStates() : Collections.emptyList());
        Map<String, String> desc = config.getStateDescriptions() != null
            ? config.getStateDescriptions() : Collections.emptyMap();

        List<String> states = config.getStates() != null
            ? config.getStates() : Collections.emptyList();
        for (String stateId : states) {
            ContractSnapshot.StateTemplate st = new ContractSnapshot.StateTemplate();
            st.setId(stateId);
            st.setName(desc.getOrDefault(stateId, stateId));
            st.setTerminal(terminals.contains(stateId));
            st.setDescription(desc.get(stateId));
            st.setTags(Collections.emptyList()); // 见下方注释
            list.add(st);
        }
        return list;
    }

    /*
     * 关于 stateTags：
     * ResolvedCaseTypeConfig 没有直接的 stateTags 字段。
     * 若需标签，建议在 _statemachine.yaml 中显式维护 stateTags，
     * 或从 TransitionRule.intentCode 的 routeGuard.stateTagsAny 反推。
     * 当前留空，不猜。
     */

    // ==================================================================
    // 意图：从 CaseIntentDefinitionsLoader 拿完整定义
    // ==================================================================
    private List<ContractSnapshot.IntentSnapshot> extractIntents(
        String caseId, ResolvedCaseTypeConfig config) {
        Set<String> wanted = collectWantedIntentCodes(config);
        if (wanted.isEmpty()) return new ArrayList<>();

        List<ContractSnapshot.IntentSnapshot> list = new ArrayList<>();
        for (String code : wanted) {
            // ★ 修正：用 getIntent(code) 拿完整定义
            IntentDefinition def = intentLoader.getIntent(code);

            ContractSnapshot.IntentSnapshot s = new ContractSnapshot.IntentSnapshot();
            s.setIntentCode(code);

            if (def == null) {
                // 兜底：只从词典补 name 和 surfaces
                IntentDictLoader.IntentMetaInfo meta = intentDictLoader.getIntentMeta(code);
                if (meta != null) s.setNameCn(meta.name());
                s.setExamples(new ArrayList<>(
                    intentDictLoader.getSurfacesForIntent(code)));
                list.add(s);
                continue;
            }

            // ===== 直接字段 =====
            s.setNameCn(def.getNameCn());
            s.setActType(def.getAct());              // 注意：字段名是 act，不是 actType
            s.setDomainCode(def.getDomainCode());
            s.setSubDomain(def.getSubDomain());
            s.setGeneral(def.isGeneral());
            s.setSpecific(def.isSpecific());
            s.setCaseCode(def.getCaseCode());
            s.setExamples(def.getExamples() != null
                ? new ArrayList<>(def.getExamples()) : new ArrayList<>());

            // ===== Trg =====
            if (def.getTrg() != null) {
                s.setRegex(def.getTrg().getRegex() != null
                    ? new ArrayList<>(def.getTrg().getRegex()) : new ArrayList<>());
                s.setNegativeRegex(def.getTrg().getNegativeRegex() != null
                    ? new ArrayList<>(def.getTrg().getNegativeRegex()) : new ArrayList<>());
                // slots 若需保留，看 Trg.getSlots() 的类型再映射
            }

            // ===== RouteGuard =====
            if (def.getRouteGuard() != null
                && def.getRouteGuard().getRequiresContext() != null) {
                var ctx = def.getRouteGuard().getRequiresContext();

                s.setStateTagsAny(ctx.getStateTagsAny() != null
                    ? new ArrayList<>(ctx.getStateTagsAny()) : new ArrayList<>());
                s.setStateTagsAll(ctx.getStateTagsAll() != null
                    ? new ArrayList<>(ctx.getStateTagsAll()) : new ArrayList<>());
                s.setContextVars(ctx.getContextVars() != null
                    ? new LinkedHashMap<>(ctx.getContextVars())
                    : new LinkedHashMap<>());
            }

            // ===== Out =====
            if (def.getOut() != null) {
                s.setOutArtifact(def.getOut().getArtifact());
                s.setDownstream(def.getOut().getDownstream());
            }

            // ===== 兜底：examples 为空时从词典补 surface =====
            if (s.getExamples().isEmpty()) {
                s.setExamples(new ArrayList<>(
                    intentDictLoader.getSurfacesForIntent(code)));
            }

            list.add(s);
        }
        return list;
    }
    /**
     * 收集本案例相关的意图编码：
     * - 转移规则里引用的
     * - 常驻安全意图
     * - 全局事件意图
     * - 意图→状态映射的 key
     */
    private Set<String> collectWantedIntentCodes(ResolvedCaseTypeConfig config) {
        Set<String> wanted = new LinkedHashSet<>();
        if (config.getTransitions() != null) {
            for (TransitionRule t : config.getTransitions()) {
                if (t.getIntentCode() != null && !t.getIntentCode().startsWith("SYS_")) {
                    wanted.add(t.getIntentCode());
                }
            }
        }
        if (config.getAlwaysAllowedIntents() != null) {
            wanted.addAll(config.getAlwaysAllowedIntents());
        }
        if (config.getGlobalEventIntents() != null) {
            wanted.addAll(config.getGlobalEventIntents());
        }
        if (config.getIntentToStateMapping() != null) {
            wanted.addAll(config.getIntentToStateMapping().keySet());
        }
        return wanted;
    }

    // ==================================================================
    // 变量：从 VariableMetaLoader 拿
    // ==================================================================
    private ContractSnapshot.VariableSnapshot extractVariables(
        String caseId, ResolvedCaseTypeConfig config) {
        ContractSnapshot.VariableSnapshot vs = new ContractSnapshot.VariableSnapshot();

        // ★ 实际 API：getVariablesForCase(caseType)
        Map<String, VariableMeta> metaMap = variableMetaLoader.getVariablesForCase(caseId);
        if (metaMap != null) {
            metaMap.forEach((name, meta) -> {
                if (name.startsWith("derived.")) {
                    vs.getDerived().add(name);
                } else {
                    vs.getReadable().add(name);
                    vs.getWritable().add(name);
                }
            });
        }

        // 前缀统计
        Map<String, Integer> prefixCount = new LinkedHashMap<>();
        for (String name : vs.getReadable()) {
            int idx = name.indexOf('.');
            String prefix = idx > 0 ? name.substring(0, idx) : "other";
            prefixCount.merge(prefix, 1, Integer::sum);
        }
        prefixCount.forEach((k, v) -> vs.getPrefixes().put(k, String.valueOf(v)));

        return vs;
    }

    // ==================================================================
    // 派生规则：从 DerivationRuleLoader 拿
    // ==================================================================
    private List<ContractSnapshot.DerivationSnapshot> extractDerivations(String caseId) {
        List<ContractSnapshot.DerivationSnapshot> list = new ArrayList<>();
        // ★ 实际 API：getRulesForCase(caseType)
        List<DerivationRule> rules = derivationRuleLoader.getRulesForCase(caseId);
        if (rules == null) return list;

        for (DerivationRule r : rules) {
            ContractSnapshot.DerivationSnapshot d = new ContractSnapshot.DerivationSnapshot();
            // 按 DerivationRule 实际字段映射
            // d.setTargetVar(r.getTarget());
            // d.setExpression(r.getExpression());
            // d.setDependsOn(r.getDependsOn());
            list.add(d);
        }
        return list;
    }

    // ==================================================================
    // 技能：YamlSkillLoader
    // ==================================================================
    private List<ContractSnapshot.SkillSnapshot> extractSkills(String caseId) {
        List<ContractSnapshot.SkillSnapshot> list = new ArrayList<>();
        // 假设 YamlSkillLoader 有 getAllSkillIds()
        Set<String> ids = yamlSkillLoader.getAllSkillIds();
        if (ids == null) return list;
        for (String id : ids) {
            ContractSnapshot.SkillSnapshot s = new ContractSnapshot.SkillSnapshot();
            s.setSkillId(id);
            // 若 YamlSkillLoader 有 getSkillMeta(skillId)，在此补充 intentCode/writes/reads
            list.add(s);
        }
        return list;
    }

    // ==================================================================
    // 护栏：从 GuardRailLoader 拿
    // ==================================================================
    private List<ContractSnapshot.GuardRailSnapshot> extractGuardRails(String caseId) {
        List<ContractSnapshot.GuardRailSnapshot> list = new ArrayList<>();
        // ★ 实际 API：getIntentToGuardsMap(caseType)
        Map<String, List<GuardRule>> map = guardRailLoader.getIntentToGuardsMap(caseId);
        if (map == null) return list;

        Set<String> seen = new HashSet<>();
        for (List<GuardRule> rules : map.values()) {
            for (GuardRule r : rules) {
                if (r.getId() == null || !seen.add(r.getId())) continue;
                ContractSnapshot.GuardRailSnapshot g = new ContractSnapshot.GuardRailSnapshot();
                g.setRuleId(r.getId());
                // 按 GuardRule 实际字段映射
                // g.setTarget(...);
                // g.setCondition(...);
                // g.setAction(...);
                // g.setSeverity(r.getSeverity() != null ? r.getSeverity().name() : null);
                list.add(g);
            }
        }
        return list;
    }

    // ==================================================================
    // 反射规则：从 ReflexRuleLoader 拿
    // ==================================================================
    private List<ContractSnapshot.ReflexRuleSnapshot> extractReflexRules(String caseId) {
        List<ContractSnapshot.ReflexRuleSnapshot> list = new ArrayList<>();
        // ★ 实际 API：getRulesForCase(caseType)
        List<ReflexRule> rules = reflexRuleLoader.getRulesForCase(caseId);
        if (rules == null) return list;

        for (ReflexRule r : rules) {
            ContractSnapshot.ReflexRuleSnapshot f = new ContractSnapshot.ReflexRuleSnapshot();
            f.setRuleId(r.getId());
            // 按 ReflexRule 实际字段映射
            // f.setTrigger(r.getCondition());
            // f.setAction(r.getSpeech());
            list.add(f);
        }
        return list;
    }

    // ==================================================================
    // 生理基线
    // ==================================================================
    private Map<String, Object> extractPhysiology(String caseId) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            var cfg = physiologyConfigService.loadCase(caseId);
            if (cfg != null) {
                // 按 PhysiologyConfig 实际字段映射
                // map.put("statePhysiology", cfg.getStatePhysiology());
            }
        } catch (Exception e) {
            log.warn("生理配置加载失败: {}", e.getMessage());
        }
        return map;
    }

    // ==================================================================
    // 规则：从 RuleConfigLoader 拿
    // ==================================================================
    private List<ContractSnapshot.RuleSnapshot> extractRules(String caseId) {
        List<ContractSnapshot.RuleSnapshot> list = new ArrayList<>();
        // ★ 实际 API：getMergedRulesForCase(caseId)
        List<RuleDefinition> rules = ruleConfigLoader.getMergedRulesForCase(caseId);
        if (rules == null) return list;

        for (RuleDefinition r : rules) {
            ContractSnapshot.RuleSnapshot s = new ContractSnapshot.RuleSnapshot();
            s.setRuleId(r.getId());
            // 按 RuleDefinition 实际字段映射
            // s.setType(...);
            // s.setExpression(...);
            list.add(s);
        }
        return list;
    }

    // ==================================================================
    // 2025 AHA + 终止性规则（不变）
    // ==================================================================
    private List<ContractSnapshot.GuidelineRule> defaultGuidelineRules() {
        List<ContractSnapshot.GuidelineRule> list = new ArrayList<>();
        list.add(rule("AHA2025-CV-ENERGY", "synchronized_cardioversion",
            "#phys.rhythm == 'AF' || #phys.rhythm == 'AFlutter'",
            "device.defibEnergy >= 200", "P0", "2025 AHA Part 9, Top 10 #2"));
        list.add(rule("AHA2025-WPW-ADENOSINE-12", "adenosine_12mg",
            "true", "#clinical.isWpw == false", "P0", "2025 AHA Tachycardia Algorithm"));
        list.add(rule("AHA2025-WPW-CCB-BB", "second_line_drug",
            "#clinical.isWpw == true",
            "intervention.diltiazem.status != 'given' && intervention.metoprolol.status != 'given'",
            "P0", "2025 AHA Tachycardia Algorithm"));
        list.add(rule("AHA2025-SOTALOL-REMOVED", "wide_qrs_stable",
            "#derived.qrsWidth == 'wide' && #derived.stable == true",
            "intentCode != 'SOTALOL'", "P1", "2025 AHA What's New"));
        list.add(rule("AHA2025-INSTABILITY-FIRST", "any_tachycardia",
            "#derived.stable == false",
            "target == 'F_101_UNSTABLE_CARDIOVERSION' || exitReason == 'UNSTABLE'",
            "P0", "2025 AHA Tachycardia Algorithm Step 2-3"));
        return list;
    }

    private ContractSnapshot.GuidelineRule rule(String id, String target,
                                                String when, String require, String severity, String ref) {
        ContractSnapshot.GuidelineRule r = new ContractSnapshot.GuidelineRule();
        r.setRuleId(id); r.setTarget(target); r.setAppliesWhen(when);
        r.setRequire(require); r.setSeverity(severity); r.setGuidelineRef(ref);
        return r;
    }

    private List<ContractSnapshot.TerminationRule> defaultTerminationRules() {
        List<ContractSnapshot.TerminationRule> list = new ArrayList<>();
        String[][] rules = {
            {"T1", "每个非终止状态必须至少有一条出边", "P0"},
            {"T2", "每个非终止状态必须有一条兜底转移", "P0"},
            {"T3", "condition 引用的变量必须有写入来源", "P0"},
            {"T4", "同一 source 下 priority 唯一", "P0"},
            {"T5", "终止状态不得有出边", "P0"},
            {"T6", "初始状态到每个终止状态必须存在有向路径", "P0"},
            {"T7", "自循环必须有次数上限或超时出口", "P0"},
        };
        for (String[] r : rules) {
            ContractSnapshot.TerminationRule t = new ContractSnapshot.TerminationRule();
            t.setRuleId(r[0]); t.setDescription(r[1]); t.setSeverity(r[2]);
            list.add(t);
        }
        return list;
    }

    // ==================================================================
    // 导出
    // ==================================================================
    public Path export(String caseId) throws IOException {
        ContractSnapshot snap = build(caseId);
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(caseId + ".json");
        MAPPER.writeValue(file.toFile(), snap);
        log.info("✅ 契约快照已导出: {}", file.toAbsolutePath());
        return file;
    }

    public Path exportAll() throws IOException {
        Path last = null;
        for (String caseId : stateMachineLoader.getConfigs().keySet()) {
            if ("GLOBAL".equals(caseId)) continue;
            last = export(caseId);
        }
        return last;
    }
}

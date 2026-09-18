package com.ruoyi.acls.contract;

import com.ruoyi.acls.cognitive.ReflexRuleLoader;
import com.ruoyi.acls.contract.models.*;
import com.ruoyi.acls.guard.GuardRailLoader;
import com.ruoyi.acls.guard.GuardRule;
import com.ruoyi.acls.router.SemanticRoutingLoader;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.acls.skill.registery.YamlSkillLoader;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;

import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.*;
import com.ruoyi.acls.contract.*;
import com.ruoyi.acls.contract.util.DerivationGraphBuilder;
import com.ruoyi.acls.contract.util.GuardExpressionParser;
import com.ruoyi.ems.domain.DerivationRule;
import com.ruoyi.ems.domain.VariableMeta;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.exception.ConfigurationException;
import com.ruoyi.ems.orchestrator.model.ActionChainConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaseContractBuilder {

    private final GuardExpressionParser guardParser;
    private final DerivationGraphBuilder graphBuilder;

    private final YamlStateMachineLoader stateMachineLoader;
    private final DerivationRuleLoader derivationRuleLoader;
    private final CaseIntentDefinitionsLoader intentLoader;
    private final SemanticRoutingLoader routingLoader;
    private final YamlSkillLoader skillLoader;
    private final VariableMetaLoader variableMetaLoader;
    private final GuardRailLoader guardRailLoader;
    private final ReflexRuleLoader reflexRuleLoader;
    private final SkillRegistry skillRegistry;

    // ====================================================================
    // 主入口
    // ====================================================================

    public Map<String, CaseContract> buildAll() {
        Map<String, CaseContract> result = new LinkedHashMap<>();
        Map<String, ResolvedCaseTypeConfig> configs = stateMachineLoader.getConfigs();
        for (String caseId : configs.keySet()) {
            if ("GLOBAL".equals(caseId)) continue;
            try {
                result.put(caseId, build(caseId));
            } catch (Exception e) {
                log.error("构建案例 {} 契约失败", caseId, e);
                throw new ConfigurationException("契约构建失败: " + caseId, e);
            }
        }
        return result;
    }

    public CaseContract build(String caseId) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config == null) {
            throw new ConfigurationException("案例不存在: " + caseId);
        }

        CaseContract contract = new CaseContract(caseId);

        // 1. 派生依赖图
        List<DerivationRule> derivations = derivationRuleLoader.getRulesForCase(caseId);
        Map<String, Set<String>> derivationGraph =
            graphBuilder.buildDependencyGraph(derivations);
        Set<String> cyclicNodes = new LinkedHashSet<>();
        graphBuilder.topologicalSort(derivationGraph, cyclicNodes);

        // 2. 意图 → 写入变量（合并技能 + execution + extraction）
        Map<String, Set<String>> intentToWrittenVars = buildIntentToWrittenVars(caseId);
        Map<String, Set<String>> varToIntents = invert(intentToWrittenVars);

        // 3. 转移契约
        for (String stateId : stateMachineLoader.getStateIdsForCase(caseId)) {
            List<TransitionRule> outgoing =
                stateMachineLoader.getTransitionsFromState(caseId, stateId);
            for (TransitionRule rule : outgoing) {
                TransitionContract tc = buildTransition(
                    caseId, rule, derivationGraph, varToIntents, intentToWrittenVars);
                contract.getTransitionContracts().put(tc.getTransitionId(), tc);
            }
        }

        // 4. 变量契约
        for (VariableMeta v : variableMetaLoader.getVariablesForCase(caseId).values()) {
            VariableContract vc = buildVariableContract(
                caseId, v, contract, varToIntents, derivationGraph, intentToWrittenVars);
            contract.getVariableContracts().put(v.getName(), vc);
        }

        // 5. 派生变量契约
        for (DerivationRule r : derivations) {
            DerivedVarContract dv = buildDerivedContract(
                caseId, r, derivationGraph, contract, cyclicNodes);
            contract.getDerivedVarContracts().put(r.getTarget(), dv);
        }

        // 6. 补充未定义的派生变量
        Set<String> referencedDerived = collectReferencedDerivedVars(contract);
        for (String undefined : referencedDerived) {
            contract.getDerivedVarContracts().computeIfAbsent(undefined, k -> {
                DerivedVarContract dv = new DerivedVarContract();
                dv.setName(k);
                dv.setUndefined(true);
                return dv;
            });
        }

        // 7. 意图契约
        buildIntentContracts(caseId, contract, intentToWrittenVars);

        // 8. 状态契约
        buildStateContracts(caseId, contract);

        // 9. 补齐变量读取源（护栏 / 反射规则 / 技能前置条件）
        enrichVariableReadSources(caseId, contract);

        // 10. 全局诊断
        diagnoseContract(caseId, contract);

        return contract;
    }

    // ====================================================================
    // 意图 → 写入变量
    // ====================================================================

    private Map<String, Set<String>> buildIntentToWrittenVars(String caseId) {
        Map<String, Set<String>> result = new HashMap<>();

        List<IntentDefinition> intents = intentLoader.getIntentsForCase(caseId);
        for (IntentDefinition def : intents) {
            Set<String> writes = new LinkedHashSet<>();
            String intentCode = def.getIntentCode();

            // 来源 A：意图在路由表里关联的技能
            Map<String, String> skillsByState =
                routingLoader.getIntentToSkillMap(caseId, null);
            // 逐状态查询
            for (String stateId : stateMachineLoader.getStateIdsForCase(caseId)) {
                String skillId = routingLoader
                    .getIntentToSkillMap(caseId, stateId).get(intentCode);
                if (skillId != null) {
                    writes.addAll(skillLoader.getSkillWrites(skillId));
                }
            }

            // 来源 B：execution.steps.outputs（执行链声明式输出）
            ActionChainConfig execution = def.getExecution();
            if (execution != null && execution.steps() != null) {
                for (ActionChainConfig.StepConfig step : execution.steps()) {
                    if (step.outputs() != null) {
                        writes.addAll(step.outputs().keySet());
                    }
                }
            }

            // 来源 C：extraction.targetParams（抽取目标参数）
            if (def.hasExtraction() && def.getExtraction().getTargetParams() != null) {
                writes.addAll(def.getExtraction().getTargetParams());
            }

            if (!writes.isEmpty()) {
                result.put(intentCode, writes);
            }
        }
        return result;
    }

    // ====================================================================
    // 转移契约
    // ====================================================================

    private TransitionContract buildTransition(String caseId, TransitionRule rule,
                                               Map<String, Set<String>> derivationGraph,
                                               Map<String, Set<String>> varToIntents,
                                               Map<String, Set<String>> intentToWrittenVars) {
        TransitionContract tc = new TransitionContract();
        tc.setSourceState(rule.getSource());
        tc.setTargetState(rule.getTarget());
        tc.setPriority(rule.getPriority() == null ? 0 : rule.getPriority());
        tc.setDescription(rule.getDescription());
        tc.setIntentCode(rule.getIntentCode());
        tc.setEvent(rule.getEvent());
        tc.setSystemTrigger(rule.isSystemTrigger());
        tc.setEnabled(rule.isEnabled());
        tc.setDeviation(rule.isDeviation());
        tc.setGuardExpression(rule.getCondition());
        tc.setActionExpression(rule.getAction());
        tc.setSystemActionType(rule.getSystemActionType());
        tc.setSystemActionParams(rule.getSystemActionParams());
        tc.setExitReason(rule.getExitReason());

        if (rule.getExitReason() != null && !rule.getExitReason().isBlank()) {
            // exitReason 会写入 process.returnToParent
            tc.getWrittenVariables().add("process.returnToParent");
        }

        if (rule.getEffects() != null) {
            for (TransitionRule.EffectRule e : rule.getEffects()) {
                if (e == null || e.getVar() == null || e.getVar().isBlank()) continue;
                TransitionContract.Effect effect = new TransitionContract.Effect();
                effect.setVar(e.getVar());
                effect.setValue(e.getValue());
                effect.setValueExpression(e.getValueExpression());
                tc.getEffects().add(effect);

                // ★ effects 写入的变量也算 writtenVariables
                tc.getWrittenVariables().add(e.getVar());
            }
        }
        // 唯一 ID
        String base = "T-" + rule.getSource() + "-" + rule.getTarget();
        String transitionId = base + "-" + String.format("%02d",
            Math.abs(Objects.hash(rule.getPriority(), rule.getIntentCode(),
                rule.getEvent(), rule.getCondition())) % 100);
        tc.setTransitionId(transitionId);

        // 读变量：从 Guard 提取
        Set<String> readVars = guardParser.extractReadVariables(rule.getCondition());
        tc.setReadVariables(readVars);

        // 叶子变量：展开 derived.*
        Set<String> leafVars = new LinkedHashSet<>();
        for (String v : readVars) {
            if (v.startsWith("derived.") && derivationGraph.containsKey(v)) {
                leafVars.addAll(graphBuilder.expandToLeafVariables(v, derivationGraph));
            } else {
                leafVars.add(v);
            }
        }
        tc.setReadLeafVariables(leafVars);

        // 写变量：process.currentState + action 的赋值目标
        tc.getWrittenVariables().add("process.currentState");
        tc.getWrittenVariables().addAll(guardParser.extractWriteTargets(rule.getAction()));

        // 触发意图
        Set<String> triggers = new LinkedHashSet<>();
        if (rule.getIntentCode() != null && !rule.getIntentCode().isEmpty()) {
            triggers.add(rule.getIntentCode());
        }
        for (String v : readVars) {
            Set<String> writers = varToIntents.get(v);
            if (writers != null) triggers.addAll(writers);
        }
        tc.setTriggeringIntents(triggers);

        // 关联技能：从转移的意图反查
        if (rule.getIntentCode() != null) {
            String skillId = routingLoader
                .getIntentToSkillMap(caseId, rule.getSource())
                .get(rule.getIntentCode());
            if (skillId != null) tc.getSkills().add(skillId);
        }

        // 诊断
        boolean hasTrigger = !triggers.isEmpty()
            || tc.isSystemTrigger()
            || (rule.getEvent() != null && !rule.getEvent().isEmpty());
        if (!hasTrigger) {
            tc.setDead(true);
            tc.getWarnings().add("无触发路径：intentCode / event / systemTrigger 均为空");
        }
        if (rule.getSource() != null && rule.getSource().equals(rule.getTarget())) {
            tc.setSelfLoop(true);
        }
        if (rule.getIntentCode() != null
            && !intentLoader.isValidIntentCode(rule.getIntentCode())) {
            tc.getWarnings().add("意图编码不存在: " + rule.getIntentCode());
        }

        return tc;
    }

    // ====================================================================
    // 变量契约
    // ====================================================================

    private VariableContract buildVariableContract(String caseId, VariableMeta v,
                                                   CaseContract contract,
                                                   Map<String, Set<String>> varToIntents,
                                                   Map<String, Set<String>> derivationGraph,
                                                   Map<String, Set<String>> intentToWrittenVars) {
        VariableContract vc = new VariableContract();
        vc.setName(v.getName());
        vc.setNameCn(v.getNameCn());
        vc.setType(v.getType());
        vc.setCategory(v.getCategory());
        vc.setSource(v.getSource());
        vc.setWritableBy(v.getWritableBy());

        // 写入方：意图
        vc.setWrittenByIntents(new LinkedHashSet<>(
            varToIntents.getOrDefault(v.getName(), Set.of())));

        // 写入方：派生规则
        for (Map.Entry<String, Set<String>> e : derivationGraph.entrySet()) {
            if (e.getKey().equals(v.getName())) {
                vc.getWrittenByDerivations().add(e.getKey());
            }
        }

        // 写入方：状态机转移的 action
        for (TransitionContract tc : contract.getTransitionContracts().values()) {
            if (tc.getWrittenVariables().contains(v.getName())) {
                vc.getWrittenByTransitions().add(tc.getTransitionId());
            }
        }

        // 写入方：状态机 onEnter
        Map<String, List<String>> onEnterRules =
            stateMachineLoader.getAllOnEnterRules(caseId);
        for (Map.Entry<String, List<String>> e : onEnterRules.entrySet()) {
            for (String expr : e.getValue()) {
                if (guardParser.extractWriteTargets(expr).contains(v.getName())) {
                    vc.getWrittenByOnEnter().add(e.getKey());
                }
            }
        }

        // 系统变量
        String src = v.getSource();
        vc.setWrittenBySystem("SYSTEM".equalsIgnoreCase(src)
            || "STATE_MACHINE".equalsIgnoreCase(src));

        // 读取方：Guard
        for (TransitionContract tc : contract.getTransitionContracts().values()) {
            if (tc.getReadVariables().contains(v.getName())) {
                vc.getReadByGuards().add(tc.getTransitionId());
            }
        }

        // 读取方：onEnter
        for (Map.Entry<String, List<String>> e : onEnterRules.entrySet()) {
            for (String expr : e.getValue()) {
                if (guardParser.extractReadVariables(expr).contains(v.getName())) {
                    vc.getReadByOnEnter().add(e.getKey());
                }
            }
        }

        // 读取方：派生规则
        for (Map.Entry<String, Set<String>> e : derivationGraph.entrySet()) {
            if (e.getValue().contains(v.getName())) {
                vc.getReadByDerivations().add(e.getKey());
            }
        }

        // 诊断
        boolean read = !vc.getReadByGuards().isEmpty()
            || !vc.getReadByOnEnter().isEmpty()
            || !vc.getReadByDerivations().isEmpty()
            || !vc.getReadBySkills().isEmpty()
            || !vc.getReadByGuardRails().isEmpty()
            || !vc.getReadByReflexRules().isEmpty();
        boolean written = !vc.getWrittenByIntents().isEmpty()
            || !vc.getWrittenByDerivations().isEmpty()
            || !vc.getWrittenByTransitions().isEmpty()
            || !vc.getWrittenByOnEnter().isEmpty()
            || vc.isWrittenBySystem();
        vc.setOrphan(!read);
        vc.setUnwritable(read && !written);

        return vc;
    }

    // ====================================================================
    // 派生变量契约
    // ====================================================================

    private DerivedVarContract buildDerivedContract(String caseId, DerivationRule r,
                                                    Map<String, Set<String>> derivationGraph,
                                                    CaseContract contract,
                                                    Set<String> cyclicNodes) {
        DerivedVarContract dv = new DerivedVarContract();
        dv.setName(r.getTarget());
        dv.setType(r.getType());
        dv.setExpression(r.getExpression());
        dv.setDependencies(new LinkedHashSet<>(
            derivationGraph.getOrDefault(r.getTarget(), Set.of())));
        dv.setLeafDependencies(
            graphBuilder.expandToLeafVariables(r.getTarget(), derivationGraph));

        // 读取方：Guard
        for (TransitionContract tc : contract.getTransitionContracts().values()) {
            if (tc.getReadVariables().contains(r.getTarget())) {
                dv.getReadByGuards().add(tc.getTransitionId());
            }
        }

        // 读取方：onEnter
        Map<String, List<String>> onEnterRules =
            stateMachineLoader.getAllOnEnterRules(caseId);
        for (Map.Entry<String, List<String>> e : onEnterRules.entrySet()) {
            for (String expr : e.getValue()) {
                if (guardParser.extractReadVariables(expr).contains(r.getTarget())) {
                    dv.getReadByOnEnter().add(e.getKey());
                }
            }
        }

        // 读取方：其他派生规则
        for (Map.Entry<String, Set<String>> e : derivationGraph.entrySet()) {
            if (e.getValue().contains(r.getTarget())) {
                dv.getReadByDerivations().add(e.getKey());
            }
        }

        dv.setCircular(cyclicNodes.contains(r.getTarget()));
        return dv;
    }

    // ====================================================================
    // 意图契约
    // ====================================================================

    private void buildIntentContracts(String caseId, CaseContract contract,
                                      Map<String, Set<String>> intentToWrittenVars) {
        List<IntentDefinition> intents = intentLoader.getIntentsForCase(caseId);
        Set<String> alwaysAllowed = stateMachineLoader.getAlwaysAllowedIntents(caseId);
        Map<String, Set<String>> surfacesMap = intentDictSurfacesMap();

        for (IntentDefinition def : intents) {
            String intentCode = def.getIntentCode();
            if (intentCode == null) continue;

            IntentContract ic = new IntentContract();
            ic.setIntentCode(intentCode);
            ic.setNameCn(def.getNameCn());
            ic.setActType(def.getAct());
            ic.setGeneral(def.isGeneral());
            ic.setCaseCode(def.getCaseCode());
            ic.setWriterType(intentLoader.getIntentWriterType(intentCode));

            // 触发模式
            ic.getTriggers().addAll(def.getAllTriggers());
            ic.getNegativeRegex().addAll(def.getNegativeRegexes());
            if (def.getAliases() != null) ic.getAliases().addAll(def.getAliases());
            if (def.getExamples() != null) ic.getExamples().addAll(def.getExamples());
            Set<String> surfaces = surfacesMap.get(intentCode);
            if (surfaces != null) ic.getSurfaces().addAll(surfaces);

            // 路由：按状态
            for (String stateId : stateMachineLoader.getStateIdsForCase(caseId)) {
                String skillId = routingLoader
                    .getIntentToSkillMap(caseId, stateId).get(intentCode);
                if (skillId != null) {
                    ic.getSkillsByState().put(stateId, skillId);
                    ic.getAllSkills().add(skillId);
                }
            }
            // 通配状态
            String wildcardSkill = routingLoader
                .getIntentToSkillMap(caseId, null).get(intentCode);
            if (wildcardSkill != null) {
                ic.getSkillsByState().put("*", wildcardSkill);
                ic.getAllSkills().add(wildcardSkill);
            }

            // 写入变量
            ic.getWrittenVariables().addAll(
                intentToWrittenVars.getOrDefault(intentCode, Set.of()));

            // 护栏
            Map<String, List<GuardRule>> guardMap =
                guardRailLoader.getIntentToGuardsMap(caseId);
            List<GuardRule> guards = guardMap.get(intentCode);
            if (guards != null) {
                for (GuardRule g : guards) {
                    if (g.getId() != null) ic.getGuardRules().add(g.getId());
                }
            }

            // 常驻意图标记
            if (alwaysAllowed.contains(intentCode)) {
                ic.setAlwaysAllowed(true);
            }

            // 诊断
            if (ic.getAllSkills().isEmpty()) {
                ic.setNoSkill(true);
            }
            if (ic.getWrittenVariables().isEmpty() && !ic.isAlwaysAllowed()) {
                ic.setNoWriter(true);
            }
            if (!intentLoader.isValidIntentCode(intentCode)) {
                ic.getWarnings().add("意图编码未在 validIntentCodes 中");
            }
            if (!def.getSlots().isEmpty()) {
                ic.getWarnings().add("含槽位定义: " + def.getSlots().keySet());
            }

            contract.getIntentContracts().put(intentCode, ic);
        }

        // 处理"常驻但不在 intentLoader 里"的意图
        for (String alwaysCode : alwaysAllowed) {
            if (!contract.getIntentContracts().containsKey(alwaysCode)) {
                IntentContract ic = new IntentContract();
                ic.setIntentCode(alwaysCode);
                ic.setAlwaysAllowed(true);
                ic.setNoSkill(true);
                ic.getWarnings().add("常驻意图未在 intentLoader 中找到定义");
                contract.getIntentContracts().put(alwaysCode, ic);
            }
        }
    }

    /** 惰性从 IntentDictLoader 拿 surfaces（可选依赖） */
    private Map<String, Set<String>> intentDictSurfacesMap() {
        // 若你已注入 IntentDictLoader，直接调用 getIntentToSurfacesMap()
        // 这里为了不强依赖，返回空 Map；后续可改为注入
        return Collections.emptyMap();
    }

    // ====================================================================
    // 状态契约
    // ====================================================================

    private void buildStateContracts(String caseId, CaseContract contract) {
        String initialState = stateMachineLoader.getInitialState(caseId);
        Set<String> endStates = stateMachineLoader.getEndStates(caseId);

        for (String stateId : stateMachineLoader.getStateIdsForCase(caseId)) {
            StateContract sc = new StateContract();
            sc.setStateId(stateId);
            sc.setStateDescription(
                stateMachineLoader.getConfig(caseId).getStateDescription(stateId));
            sc.setInitialState(stateId.equals(initialState));
            sc.setEndState(endStates.contains(stateId));
            sc.getOnEnterRules().addAll(
                stateMachineLoader.getOnEnterRules(caseId, stateId));

            // 出边
            for (TransitionContract tc : contract.getTransitionContracts().values()) {
                if (stateId.equals(tc.getSourceState())) {
                    sc.getTransitions().add(tc);
                    sc.getRequiredVariables().addAll(tc.getReadVariables());
                    sc.getRequiredIntents().addAll(tc.getTriggeringIntents());
                }
            }

            contract.getStateContracts().put(stateId, sc);
        }
    }

    // ====================================================================
    // 补齐变量的读取源（护栏 / 反射规则 / 技能前置条件）
    // ====================================================================

    private void enrichVariableReadSources(String caseId, CaseContract contract) {
        // 护栏
        Map<String, Set<String>> varToGuards = guardRailLoader.getVariableToGuardsMap();
        // 反射规则
        Map<String, Set<String>> varToRules = reflexRuleLoader.getVariableToRulesMap();

        for (VariableContract vc : contract.getVariableContracts().values()) {
            String name = vc.getName();
            Set<String> guards = varToGuards.get(name);
            if (guards != null) vc.getReadByGuardRails().addAll(guards);
            Set<String> rules = varToRules.get(name);
            if (rules != null) vc.getReadByReflexRules().addAll(rules);
        }

        // 技能前置条件
        for (IntentContract ic : contract.getIntentContracts().values()) {
            for (String skillId : ic.getAllSkills()) {
                Set<String> reads = skillLoader.getSkillReads(skillId);
                for (String varName : reads) {
                    VariableContract vc = contract.getVariableContracts().get(varName);
                    if (vc != null) vc.getReadBySkills().add(skillId);
                }
            }
        }
    }

    // ====================================================================
    // 诊断
    // ====================================================================

    private void diagnoseContract(String caseId, CaseContract contract) {
        // 技能覆盖率
        Set<String> declaredInYaml = skillLoader.getAllSkillIds();
        Set<String> registered = skillRegistry.getAllSkillIds();
        Set<String> missing = new LinkedHashSet<>(declaredInYaml);
        missing.removeAll(registered);
        if (!missing.isEmpty()) {
            contract.getBuildWarnings().add("以下技能在 YAML 声明但未注册: " + missing);
        }

        // 路由冲突
        List<String> conflicts = routingLoader.findRoutingConflicts(caseId, null);
        contract.getBuildWarnings().addAll(conflicts);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private Set<String> collectReferencedDerivedVars(CaseContract contract) {
        Set<String> result = new LinkedHashSet<>();
        for (TransitionContract tc : contract.getTransitionContracts().values()) {
            for (String v : tc.getReadVariables()) {
                if (v.startsWith("derived.")) result.add(v);
            }
        }
        return result;
    }

    private Map<String, Set<String>> invert(Map<String, Set<String>> map) {
        Map<String, Set<String>> result = new HashMap<>();
        map.forEach((k, vars) -> {
            for (String v : vars) {
                result.computeIfAbsent(v, x -> new LinkedHashSet<>()).add(k);
            }
        });
        return result;
    }
}

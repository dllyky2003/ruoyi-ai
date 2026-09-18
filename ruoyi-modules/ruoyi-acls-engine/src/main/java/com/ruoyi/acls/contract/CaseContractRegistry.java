package com.ruoyi.acls.contract;

import com.ruoyi.acls.contract.*;
import com.ruoyi.acls.contract.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CaseContractRegistry {

    private final Map<String, CaseContract> contracts = new ConcurrentHashMap<>();

    public void register(String caseId, CaseContract contract) {
        if (caseId == null || contract == null) return;
        contracts.put(caseId, contract);
        log.info("契约已注册: caseId={}, 转移数={}, 意图数={}, 变量数={}",
            caseId,
            contract.getTransitionContracts().size(),
            contract.getIntentContracts().size(),
            contract.getVariableContracts().size());
    }

    public void registerAll(Map<String, CaseContract> map) {
        if (map == null || map.isEmpty()) return;
        contracts.putAll(map);
        log.info("批量注册契约完成，共 {} 个案例: {}", map.size(), map.keySet());
    }

    public void replace(String caseId, CaseContract contract) {
        if (caseId == null || contract == null) return;
        contracts.put(caseId, contract);
        log.info("契约已热替换: caseId={}", caseId);
    }

    public void clear() {
        contracts.clear();
        log.info("契约注册表已清空");
    }

    // ==================== 查询 ====================

    public CaseContract get(String caseId) { return contracts.get(caseId); }

    public boolean contains(String caseId) { return contracts.containsKey(caseId); }

    public Set<String> getAllCaseIds() {
        return Collections.unmodifiableSet(contracts.keySet());
    }

    public Map<String, CaseContract> getAll() {
        return Collections.unmodifiableMap(contracts);
    }

    public TransitionContract getTransition(String caseId, String transitionId) {
        CaseContract c = contracts.get(caseId);
        return c != null ? c.getTransitionContracts().get(transitionId) : null;
    }

    public VariableContract getVariable(String caseId, String varName) {
        CaseContract c = contracts.get(caseId);
        return c != null ? c.getVariableContracts().get(varName) : null;
    }

    public IntentContract getIntent(String caseId, String intentCode) {
        CaseContract c = contracts.get(caseId);
        return c != null ? c.getIntentContracts().get(intentCode) : null;
    }

    public StateContract getState(String caseId, String stateId) {
        CaseContract c = contracts.get(caseId);
        return c != null ? c.getStateContracts().get(stateId) : null;
    }

    // ==================== 反查 ====================

    public List<String> findTriggeringIntents(String caseId, String transitionId) {
        TransitionContract tc = getTransition(caseId, transitionId);
        return tc == null ? List.of() : new ArrayList<>(tc.getTriggeringIntents());
    }

    public List<String> findTransitionsTriggeredBy(String caseId, String intentCode) {
        IntentContract ic = getIntent(caseId, intentCode);
        return ic == null ? List.of() : new ArrayList<>(ic.getMayTriggerTransitions());
    }

    public List<String> findIntentsWriting(String caseId, String varName) {
        VariableContract vc = getVariable(caseId, varName);
        return vc == null ? List.of() : new ArrayList<>(vc.getWrittenByIntents());
    }

    public List<TransitionContract> findTransitionsReading(String caseId, String varName) {
        CaseContract c = contracts.get(caseId);
        if (c == null) return List.of();
        return c.getTransitionContracts().values().stream()
            .filter(tc -> tc.getReadVariables().contains(varName))
            .collect(Collectors.toList());
    }

    // ==================== 诊断 ====================

    public ContractDiagnostics getDiagnostics(String caseId) {
        CaseContract c = contracts.get(caseId);
        return c == null ? null : ContractDiagnostics.from(c);
    }

    public Map<String, ContractDiagnostics> getAllDiagnostics() {
        Map<String, ContractDiagnostics> result = new LinkedHashMap<>();
        contracts.forEach((caseId, c) ->
            result.put(caseId, ContractDiagnostics.from(c)));
        return result;
    }

    public void logAllDiagnostics() {
        for (Map.Entry<String, ContractDiagnostics> e : getAllDiagnostics().entrySet()) {
            String caseId = e.getKey();
            ContractDiagnostics d = e.getValue();
            log.info("========== 案例 {} 契约诊断 ==========", caseId);
            if (!d.getUndefinedDerivedVars().isEmpty())
                log.error("  ❌ 未定义的派生变量: {}", d.getUndefinedDerivedVars());
            if (!d.getCircularDependencies().isEmpty())
                log.error("  ❌ 派生变量循环依赖: {}", d.getCircularDependencies());
            if (!d.getDeadTransitions().isEmpty())
                log.error("  ❌ 死转移: {}", d.getDeadTransitions());
            if (!d.getSelfLoops().isEmpty())
                log.warn("  ⚠️ 自循环转移: {}", d.getSelfLoops());
            if (!d.getUnwritableVariables().isEmpty())
                log.warn("  ⚠️ 被读但无人写的变量: {}", d.getUnwritableVariables());
            if (!d.getEmptyIntents().isEmpty())
                log.warn("  ⚠️ 无技能关联的意图: {}", d.getEmptyIntents());
            if (!d.getOrphanVariables().isEmpty())
                log.info("  ℹ️ 无人读取的变量: {}", d.getOrphanVariables());
            CaseContract c = contracts.get(caseId);
            if (c != null && !c.getBuildWarnings().isEmpty()) {
                for (String w : c.getBuildWarnings()) log.warn("  ⚠️ {}", w);
            }
        }
    }
}

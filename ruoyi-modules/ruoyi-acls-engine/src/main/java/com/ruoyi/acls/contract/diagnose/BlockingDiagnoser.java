package com.ruoyi.acls.contract.diagnose;

import com.ruoyi.acls.contract.CaseContractRegistry;
import com.ruoyi.acls.contract.models.CaseContract;
import com.ruoyi.acls.contract.models.TransitionContract;
import com.ruoyi.acls.contract.util.CrossModuleReferenceScanner;
import com.ruoyi.acls.contract.util.CrossModuleReferenceScanner.Reference;
import com.ruoyi.acls.contract.util.CrossModuleReferenceScanner.VariableBlood;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 从"被拒现象"反查"根因"。
 *
 * <p>两类主要判定：
 * <ul>
 *   <li><b>NO_OUT_EDGE</b>：状态机里根本没有该意图的出边（学员操作/流程问题）</li>
 *   <li><b>GUARD_FAIL</b>：有出边但 guard 不满足（配置/变量问题）</li>
 * </ul>
 */
@Slf4j
@Component
public class BlockingDiagnoser {

    private static final Pattern VAR_IN_EXPR =
        Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_.]*)");

    private final CaseContractRegistry contractRegistry;
    private final CrossModuleReferenceScanner scanner;
    private final RejectionRecorder recorder;
    private final EmsContextManager contextManager;
    private final ObjectProvider<SpELExpressionEvaluator> spelProvider;

    public BlockingDiagnoser(CaseContractRegistry contractRegistry,
                             CrossModuleReferenceScanner scanner,
                             RejectionRecorder recorder,
                             EmsContextManager contextManager,
                             ObjectProvider<SpELExpressionEvaluator> spelProvider) {
        this.contractRegistry = contractRegistry;
        this.scanner = scanner;
        this.recorder = recorder;
        this.contextManager = contextManager;
        this.spelProvider = spelProvider;
    }

    /**
     * 主入口。若 stateId/intentId 为空，取该 case 最近一次拒绝事件。
     */
    public DiagnosticReport diagnose(String caseId,
                                     String sessionId,
                                     String stateId,
                                     String intentId) {

        DiagnosticReport report = new DiagnosticReport();

        // ---------- 1. 定位现象 ----------
        RejectionRecorder.RejectionEvent ev = null;
        if (stateId == null || intentId == null) {
            ev = recorder.latest(caseId);
            if (ev == null) {
                report.setVerdict("NO_REJECTION");
                report.setConclusion("暂无拒绝事件记录。请先复现问题。");
                return report;
            }
            caseId = ev.getCaseId();
            sessionId = ev.getSessionId();
            stateId = ev.getStateId();
            intentId = ev.getIntentId();
        }

        // ★ 重新赋值后拷贝到 final，供 lambda 使用
        final String fCaseId = caseId;
        final String fSessionId = sessionId;
        final String fStateId = stateId;
        final String fIntentId = intentId;

        DiagnosticReport.Phenomenon ph = new DiagnosticReport.Phenomenon();
        ph.setCaseId(fCaseId);
        ph.setSessionId(fSessionId);
        ph.setStateId(fStateId);
        ph.setIntentId(fIntentId);
        ph.setTaskId(ev != null ? ev.getTaskId() : null);
        ph.setTimestamp(System.currentTimeMillis());
        report.setPhenomenon(ph);

        // ---------- 2. 拿契约 ----------
        CaseContract contract = contractRegistry.get(fCaseId);
        if (contract == null) {
            report.setVerdict("CONTRACT_MISSING");
            report.setConclusion("案例 " + fCaseId + " 的契约不存在。");
            return report;
        }

        // CaseContract 的转移是 Map，用 values()
        Map<String, TransitionContract> tMap = contract.getTransitionContracts();
        Collection<TransitionContract> all = (tMap != null && !tMap.isEmpty())
            ? tMap.values()
            : Collections.emptyList();

        // ---------- 3. 找该状态的所有出边 ----------
        List<TransitionContract> outEdges = all.stream()
            .filter(t -> fStateId.equals(t.getSourceState()))
            .collect(Collectors.toList());

        if (outEdges.isEmpty()) {
            report.setVerdict("STATE_NOT_FOUND");
            report.setConclusion("状态 " + fStateId + " 在契约中没有任何出边。");
            return report;
        }

        // ---------- 4. 分类判定 ----------
        boolean anyEdgeForIntent = outEdges.stream()
            .anyMatch(t -> fIntentId.equals(t.getIntentCode()));

        if (!anyEdgeForIntent) {
            fillNoOutEdge(report, fCaseId, fStateId, fIntentId, outEdges);
        } else {
            fillGuardFail(report, fCaseId, fSessionId, fStateId, fIntentId, outEdges);
        }
        return report;
    }

    // ============================================================
    // NO_OUT_EDGE
    // ============================================================
    private void fillNoOutEdge(DiagnosticReport report, String caseId, String stateId,
                               String intentId, List<TransitionContract> outEdges) {

        Set<String> available = new LinkedHashSet<>();
        for (TransitionContract t : outEdges) {
            if (t.getIntentCode() != null && !t.getIntentCode().isBlank()) {
                available.add(t.getIntentCode());
            }
        }
        report.setAvailableIntents(new ArrayList<>(available));

        for (TransitionContract t : outEdges) {
            report.getOutEdges().add(toEdgeView(caseId, t, Collections.emptyMap()));
        }

        report.setVerdict("NO_OUT_EDGE");

        String avail = available.isEmpty()
            ? "（无意图触发的出边，只能由变量驱动离开）"
            : String.join(", ", available);

        report.setConclusion(String.format(
            "意图 %s 在状态 %s 下没有入口。该状态可执行的意图：%s",
            intentId, stateId, avail));

        if (available.isEmpty()) {
            report.setSuggestion(
                "该状态是纯条件等待态，需要变量变化驱动离开。"
                    + "请检查：是否漏了某个技能会产生触发该状态出边的变量？");
        } else {
            report.setSuggestion(
                "请先完成状态机允许的操作：" + avail
                    + "。若认为「" + intentId + "」也应允许，请检查该状态机配置是否漏了该意图的出边。");
        }
    }

    // ============================================================
    // GUARD_FAIL
    // ============================================================
    private void fillGuardFail(DiagnosticReport report, String caseId, String sessionId,
                               String stateId, String intentId,
                               List<TransitionContract> outEdges) {

        final String fIntentId = intentId;
        List<TransitionContract> relevant = outEdges.stream()
            .filter(t -> fIntentId.equals(t.getIntentCode()))
            .collect(Collectors.toList());

        Map<String, Object> snapshot = new HashMap<>();
        if (sessionId != null) {
            CaseContext ctx = contextManager.getSnapshot(sessionId);
            if (ctx != null && ctx.getVariables() != null) {
                snapshot.putAll(ctx.getVariables());
            }
        }

        DiagnosticReport.VarView suspicious = null;

        for (TransitionContract t : relevant) {
            DiagnosticReport.EdgeView view = toEdgeView(caseId, t, snapshot);
            report.getOutEdges().add(view);

            if (!Boolean.TRUE.equals(view.getSatisfied()) && suspicious == null) {
                for (DiagnosticReport.VarView v : view.getVars()) {
                    if (!v.getWriters().isEmpty()) {
                        suspicious = v;
                        break;
                    }
                }
            }
        }

        report.setVerdict("GUARD_FAIL");
        report.setConclusion(String.format(
            "意图 %s 在状态 %s 有 %d 条出边，但 guard 均未满足。",
            intentId, stateId, relevant.size()));

        if (suspicious != null) {
            DiagnosticReport.RootCause rc = buildRootCause(suspicious);
            report.setRootCause(rc);
            report.setImpact(computeImpact(caseId, suspicious.getVarName()));
            report.setSuggestion(
                "请先核对源头变量「" + rc.getVarName() + "」的写入方（"
                    + rc.getWriterSkill() + " @ " + rc.getWriterFile() + "）。");
        } else {
            report.setSuggestion(
                "Guard 依赖的变量均无写入方——可能配置缺失，或变量由运行时动态生成。");
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    /** ★ caseId 参数用于把 queryWriters 限定在本案例。 */
    private DiagnosticReport.EdgeView toEdgeView(String caseId,
                                                 TransitionContract t,
                                                 Map<String, Object> snapshot) {
        DiagnosticReport.EdgeView v = new DiagnosticReport.EdgeView();
        v.setIntentCode(t.getIntentCode());
        v.setTargetState(t.getTargetState());
        v.setPriority(t.getPriority());
        v.setTriggerType(t.getTriggerType());

        String guard = t.getGuardExpression();
        v.setGuardRaw(guard);

        if (guard == null || guard.isBlank()) {
            v.setSatisfied(true);
            return v;
        }

        v.setGuardResolved(substitute(guard, snapshot));
        v.setSatisfied(tryEval(guard, snapshot));

        Set<String> varNames = (t.getReadVariables() != null && !t.getReadVariables().isEmpty())
            ? new LinkedHashSet<>(t.getReadVariables())
            : extractVars(guard);

        for (String vn : varNames) {
            DiagnosticReport.VarView vv = new DiagnosticReport.VarView();
            vv.setVarName(vn);
            vv.setCurrentValue(snapshot.get(vn));
            vv.setWriters(queryWriters(caseId, vn));   // ★ 传 caseId
            v.getVars().add(vv);
        }
        return v;
    }

    private String substitute(String expr, Map<String, Object> snapshot) {
        if (expr == null) return null;
        String result = expr;
        Matcher m = VAR_IN_EXPR.matcher(expr);
        while (m.find()) {
            Object val = snapshot.get(m.group(1));
            String lit = val == null ? "null"
                : (val instanceof String s ? "'" + s + "'" : String.valueOf(val));
            result = result.replace(m.group(0), lit);
        }
        return result;
    }

    /**
     * 尝试调用 SpELExpressionEvaluator。
     *
     * <p>不同版本方法名可能有差异（eval / evaluate / evaluateExpression），
     * 用反射兼容。都找不到返回 null（未知），不强判。</p>
     */
    private Boolean tryEval(String expr, Map<String, Object> snapshot) {
        SpELExpressionEvaluator eval = spelProvider.getIfAvailable();
        if (eval == null) return null;

        String[] candidates = {"evaluate", "eval", "evaluateExpression", "parse"};
        for (String name : candidates) {
            try {
                Method m = eval.getClass().getMethod(name, String.class, Map.class);
                m.setAccessible(true);
                Object r = m.invoke(eval, expr, snapshot);
                return Boolean.TRUE.equals(r);
            } catch (NoSuchMethodException ignore) {
                // 试下一个
            } catch (Exception e) {
                log.debug("SpEL 求值异常: {} - {}", expr, e.getMessage());
                return null;
            }
        }
        log.debug("SpELExpressionEvaluator 未找到兼容的求值方法，跳过求值: {}", expr);
        return null;
    }

    private Set<String> extractVars(String expr) {
        Set<String> out = new LinkedHashSet<>();
        if (expr == null) return out;
        Matcher m = VAR_IN_EXPR.matcher(expr);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** ★ 按 caseId 过滤，避免混入其他案例的写入方。 */
    private List<DiagnosticReport.WriterInfo> queryWriters(String caseId, String varName) {
        List<DiagnosticReport.WriterInfo> out = new ArrayList<>();
        VariableBlood blood = scanner.scanVariable(caseId, varName);
        if (blood == null) return out;
        for (Reference r : blood.getWriters()) {
            DiagnosticReport.WriterInfo wi = new DiagnosticReport.WriterInfo();
            wi.setModule(r.getModule());
            wi.setFilePath(r.getFilePath());
            wi.setLocation(r.getLocation());
            out.add(wi);
        }
        return out;
    }

    private DiagnosticReport.RootCause buildRootCause(DiagnosticReport.VarView v) {
        DiagnosticReport.RootCause rc = new DiagnosticReport.RootCause();
        rc.setVarName(v.getVarName());
        rc.setCurrentValue(v.getCurrentValue());
        rc.setReason(String.format("变量 %s 当前值 = %s，与出边条件期望不符。",
            v.getVarName(), v.getCurrentValue()));
        if (!v.getWriters().isEmpty()) {
            DiagnosticReport.WriterInfo w = v.getWriters().get(0);
            rc.setWriterModule(w.getModule());
            rc.setWriterFile(w.getFilePath());
            rc.setWriterSkill(w.getLocation());
        }
        return rc;
    }

    private DiagnosticReport.Impact computeImpact(String caseId, String varName) {
        DiagnosticReport.Impact imp = new DiagnosticReport.Impact();
        VariableBlood blood = scanner.scanVariable(caseId, varName);
        if (blood != null) {
            imp.setAffectedModules(new ArrayList<>(blood.getModules()));
            imp.setAffectedFiles(blood.getFiles().stream()
                .map(f -> "[" + f.getModule() + "] " + f.getFilePath())
                .distinct().toList());
        }
        CaseContract c = contractRegistry.get(caseId);
        if (c != null && c.getTransitionContracts() != null) {
            Set<String> intents = new LinkedHashSet<>();
            for (TransitionContract t : c.getTransitionContracts().values()) {
                String g = t.getGuardExpression();
                if (g != null && g.contains("#" + varName) && t.getIntentCode() != null) {
                    intents.add(t.getIntentCode());
                }
            }
            imp.setAffectedIntents(new ArrayList<>(intents));
        }
        return imp;
    }
}

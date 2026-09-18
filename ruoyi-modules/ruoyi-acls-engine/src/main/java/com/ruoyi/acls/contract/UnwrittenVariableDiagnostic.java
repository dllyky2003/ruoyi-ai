package com.ruoyi.acls.contract;

import com.ruoyi.acls.skill.registery.YamlSkillLoader;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.DerivationRuleLoader;
import com.ruoyi.ems.config.loader.VariableMetaLoader;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.domain.DerivationRule;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 诊断：找出"在状态机 condition 中被引用、但没有任何途径被写入"的变量。
 *
 * <p>递归展开 derived.* 依赖，追溯到叶子变量，判断叶子变量是否有写入来源。
 *
 * <p>写入来源清单（共 7 个）：
 * <ol>
 *   <li>transition.effects[].var</li>
 *   <li>transition.effects[].valueExpression 里的变量（父级展开）</li>
 *   <li>resetDefaults</li>
 *   <li>onEnterRules</li>
 *   <li>派生规则的 target（derived.*）</li>
 *   <li>技能写回（YamlSkillLoader.SkillMeta.writes）</li>
 *   <li>exitReason 触发引擎自动写 process.returnToParent</li>
 * </ol>
 *
 * <p>不覆盖：生理引擎推进、外部系统注入、学员输入通道。
 * 这些需要在引擎侧确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnwrittenVariableDiagnostic {

    private final YamlStateMachineLoader stateMachineLoader;
    private final VariableMetaLoader variableMetaLoader;
    private final DerivationRuleLoader derivationRuleLoader;
    private final YamlSkillLoader yamlSkillLoader;

    private static final Pattern VAR_PATTERN =
        Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_.]+)");

    // ==================================================================
    // 诊断入口
    // ==================================================================
    public DiagnosticResult diagnose(String caseId) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config == null) {
            throw new IllegalArgumentException("案例不存在: " + caseId);
        }

        // 1. 收集所有 condition 引用的变量
        Map<String, Set<String>> varToTransitions = new LinkedHashMap<>();
        if (config.getTransitions() != null) {
            for (TransitionRule t : config.getTransitions()) {
                if (t.getCondition() == null || t.getCondition().isBlank()) continue;
                String tid = stableId(t);
                for (String v : extractVars(t.getCondition())) {
                    varToTransitions.computeIfAbsent(v, k -> new LinkedHashSet<>()).add(tid);
                }
            }
        }

        // 2. 收集所有写入源
        Set<String> writtenVars = collectWrittenVars(caseId, config);

        // 3. 递归展开 derived.* 依赖，收集叶子变量
        Map<String, Set<String>> leafToReaders = new LinkedHashMap<>();
        Map<String, Set<String>> leafToDerived = new LinkedHashMap<>();
        Set<String> derivedTargets = collectDerivedTargets(caseId);

        for (String v : varToTransitions.keySet()) {
            Set<String> leaves = new LinkedHashSet<>();
            if (derivedTargets.contains(v)) {
                expandDerivation(caseId, v, leaves, new HashSet<>());
            } else {
                leaves.add(v);
            }
            for (String leaf : leaves) {
                leafToReaders.computeIfAbsent(leaf, k -> new LinkedHashSet<>())
                    .addAll(varToTransitions.get(v));
                leafToDerived.computeIfAbsent(leaf, k -> new LinkedHashSet<>())
                    .add(v);
            }
        }

        // 4. 找出未写入的叶子变量
        DiagnosticResult result = new DiagnosticResult();
        result.setCaseId(caseId);

        for (Map.Entry<String, Set<String>> e : leafToReaders.entrySet()) {
            String leaf = e.getKey();
            if (isWritten(leaf, writtenVars)) continue;

            UnwrittenVar u = new UnwrittenVar();
            u.setVarName(leaf);
            u.setReadingTransitions(new ArrayList<>(e.getValue()));
            u.setViaDerived(new ArrayList<>(leafToDerived.get(leaf)));
            u.setSeverity(judgeSeverity(leaf, e.getValue(), config));
            result.getUnwrittenVars().add(u);
        }

        result.setTotalConditions(varToTransitions.size());
        result.setWrittenVarCount(writtenVars.size());

        return result;
    }

    /**
     * 判断变量是否被写入。支持父子前缀展开：
     * 如果 written 里有 intervention.adenosine，则 intervention.adenosine.count 也算已写入。
     */
    private boolean isWritten(String var, Set<String> writtenVars) {
        if (writtenVars.contains(var)) return true;
        // 前缀展开
        String p = var;
        while (p.contains(".")) {
            p = p.substring(0, p.lastIndexOf('.'));
            if (writtenVars.contains(p)) return true;
        }
        return false;
    }

    // ==================================================================
    // 收集写入源（★ 增加技能写回 + exitReason 映射）
    // ==================================================================
    private Set<String> collectWrittenVars(String caseId, ResolvedCaseTypeConfig config) {
        Set<String> written = new LinkedHashSet<>();

        // 1. transition.effects[].var
        if (config.getTransitions() != null) {
            for (TransitionRule t : config.getTransitions()) {
                if (t.getEffects() == null) continue;
                for (TransitionRule.EffectRule ef : t.getEffects()) {
                    if (ef.getVar() != null && !ef.getVar().isBlank()) {
                        written.add(ef.getVar());
                    }
                    // valueExpression 是读取，不是写入，不加进来
                }
            }
        }

        // 2. resetDefaults
        Map<String, Object> defaults = variableMetaLoader.getResetDefaults(caseId);
        if (defaults != null) {
            written.addAll(defaults.keySet());
        }

        // 3. onEnterRules
        if (config.getOnEnterRules() != null) {
            for (List<String> rules : config.getOnEnterRules().values()) {
                for (String rule : rules) {
                    written.addAll(extractVars(rule));
                }
            }
        }

        // 4. 派生规则的 target
        written.addAll(collectDerivedTargets(caseId));

        // 5. ★ 技能写回（YamlSkillLoader.SkillMeta.writes）
        try {
            Map<String, YamlSkillLoader.SkillMeta> allMeta =
                yamlSkillLoader.getAllSkillMeta();
            if (allMeta != null) {
                for (YamlSkillLoader.SkillMeta meta : allMeta.values()) {
                    if (meta.getWrites() != null && !meta.getWrites().isEmpty()) {
                        written.addAll(meta.getWrites());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取技能写回元数据失败: {}", e.getMessage());
        }

        // 6. ★ exitReason → process.returnToParent
        if (config.getTransitions() != null) {
            for (TransitionRule t : config.getTransitions()) {
                if (t.getExitReason() != null && !t.getExitReason().isBlank()) {
                    written.add("process.returnToParent");
                    break;
                }
            }
        }

        return written;
    }

    // ==================================================================
    // 派生规则依赖展开
    // ==================================================================
    private Set<String> collectDerivedTargets(String caseId) {
        Set<String> targets = new LinkedHashSet<>();
        List<DerivationRule> rules = derivationRuleLoader.getRulesForCase(caseId);
        if (rules == null) return targets;
        for (DerivationRule r : rules) {
            String target = r.getTarget();
            if (target != null) targets.add(target);
        }
        return targets;
    }

    private void expandDerivation(String caseId, String var,
                                  Set<String> leaves, Set<String> visiting) {
        if (visiting.contains(var)) {
            leaves.add("CYCLE:" + var);
            return;
        }
        visiting.add(var);

        List<DerivationRule> rules = derivationRuleLoader.getRulesForCase(caseId);
        DerivationRule match = null;
        if (rules != null) {
            for (DerivationRule r : rules) {
                if (var.equals(r.getTarget())) {
                    match = r;
                    break;
                }
            }
        }

        if (match == null || match.getDependencies() == null
            || match.getDependencies().isEmpty()) {
            leaves.add(var);
            visiting.remove(var);
            return;
        }

        for (String dep : match.getDependencies()) {
            if (dep.startsWith("derived.")) {
                expandDerivation(caseId, dep, leaves, visiting);
            } else {
                leaves.add(dep);
            }
        }
        visiting.remove(var);
    }

    // ==================================================================
    // 严重程度
    // ==================================================================
    private String judgeSeverity(String var, Set<String> readers,
                                 ResolvedCaseTypeConfig config) {
        if (readers.size() >= 3) return "HIGH";
        if (readers.size() >= 1) return "MEDIUM";
        return "LOW";
    }

    // ==================================================================
    // 工具
    // ==================================================================
    private Set<String> extractVars(String expr) {
        Set<String> vars = new LinkedHashSet<>();
        if (expr == null) return vars;
        Matcher m = VAR_PATTERN.matcher(expr);
        while (m.find()) {
            String name = m.group(1);
            if (!name.startsWith("utils") && !name.startsWith("ctx")) {
                vars.add(name);
            }
        }
        return vars;
    }

    private String stableId(TransitionRule t) {
        return String.format("T-%s-%s-p%d",
            t.getSource(), t.getTarget(),
            t.getPriority() != null ? t.getPriority() : 0);
    }

    // ==================================================================
    // DTO
    // ==================================================================
    @Data
    public static class DiagnosticResult {
        private String caseId;
        private int totalConditions;
        private int writtenVarCount;
        private List<UnwrittenVar> unwrittenVars = new ArrayList<>();

        public boolean hasUnwrittenVars() {
            return !unwrittenVars.isEmpty();
        }

        public String toReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n===== 未写入变量诊断 =====\n");
            sb.append("案例: ").append(caseId).append("\n");
            sb.append("被引用的 condition 变量数: ").append(totalConditions).append("\n");
            sb.append("有写入来源的变量数: ").append(writtenVarCount).append("\n");
            sb.append("未写入的叶子变量数: ").append(unwrittenVars.size()).append("\n");

            if (unwrittenVars.isEmpty()) {
                sb.append("✅ 所有被引用变量都有写入来源\n");
                return sb.toString();
            }

            sb.append("\n❌ 以下变量被 condition 引用，但没有任何途径被写入：\n");
            for (UnwrittenVar u : unwrittenVars) {
                sb.append("  - [").append(u.getSeverity()).append("] ")
                    .append(u.getVarName()).append("\n");
                if (u.getViaDerived() != null && !u.getViaDerived().isEmpty()) {
                    sb.append("      通过派生变量: ")
                        .append(String.join(", ", u.getViaDerived())).append("\n");
                }
                sb.append("      影响转移: ")
                    .append(String.join(", ", u.getReadingTransitions())).append("\n");
            }
            sb.append("\n提示：以下写入源不在诊断范围内，需人工确认：\n");
            sb.append("  - 生理引擎推进（phys.* 大多由引擎写）\n");
            sb.append("  - 外部系统注入\n");
            sb.append("  - 学员输入通道（clinical.* 可能由学员输入）\n");
            return sb.toString();
        }
    }

    @Data
    public static class UnwrittenVar {
        private String varName;
        private String severity;
        private List<String> readingTransitions = new ArrayList<>();
        private List<String> viaDerived = new ArrayList<>();
    }
}

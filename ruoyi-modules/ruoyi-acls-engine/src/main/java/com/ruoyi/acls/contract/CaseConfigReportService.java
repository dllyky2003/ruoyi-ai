package com.ruoyi.acls.contract;


import com.ruoyi.acls.contract.models.ContractDiagnostics;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.VariableMetaLoader;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.domain.VariableMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 案例配置报告生成服务 v4。
 *
 * <p>核心原则：**只列状态机实际引用的意图 / 技能 / 变量**。
 * 报告的每一条都能在 {@code _flow.yaml} 里找到出处。
 *
 * <p>标注约定：{@code [G]} 继承 GLOBAL，{@code [C]} 案例自有。
 *
 * <p>报告结构：
 * <ol>
 *   <li>案例概览</li>
 *   <li>状态机（主体）</li>
 *   <li>状态机引用的意图 + 意图 → Skill 映射</li>
 *   <li>状态机引用的技能（意图反查 + 变量反查的并集）</li>
 *   <li>状态机引用的变量（condition 引用）+ 读-写缺口</li>
 *   <li>状态机引用的派生变量</li>
 *   <li>契约诊断</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseConfigReportService {

    /** SpEL #变量名（支持 a.b.c） */
    private static final Pattern SPEL_VAR =
        Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)");

    /** IA_003_check_pulse → check_pulse */
    private static final Pattern INTENT_ACTION =
        Pattern.compile("^IA_\\d+_(.+)$");

    /** complex skill Map 写入关系（手工维护） */
    private static final Map<String, String> MAP_VAR_TO_SKILL = Map.of(
        "epinephrine",   "drug_push_epinephrine",
        "adenosine",     "drug_push_adenosine",
        "cardioversion", "synchronized_cardioversion",
        "defib",         "administer_shock_aed"
    );

    private final YamlStateMachineLoader stateMachineLoader;
    private final VariableMetaLoader     variableMetaLoader;
    private final SkillRegistry          skillRegistry;
    private final CaseContractRegistry   contractRegistry;

    /** 生成指定案例的 Markdown 配置报告 */
    public String generateReport(String caseId) {
        if (caseId == null || caseId.isBlank())
            throw new IllegalArgumentException("caseId 不能为空");
        if (stateMachineLoader.getConfig(caseId) == null)
            throw new IllegalArgumentException("案例不存在: " + caseId);
        return new ReportBuilder(caseId).build();
    }

    // ============================================================
    // 内部构建器
    // ============================================================
    private class ReportBuilder {

        private final String caseId;
        private final Yaml yaml = new Yaml();

        // 案例归属
        private Set<String> caseOwnStates = new HashSet<>();
        private Set<String> caseOwnTransitionKeys = new HashSet<>();

        // 全局技能索引
        private final Map<String, Set<String>> varWriters = new TreeMap<>();
        private final Map<String, String> skillType = new LinkedHashMap<>();

        // 状态机引用（build 时填充）
        private final Set<String> refIntentsC   = new LinkedHashSet<>();
        private final Set<String> refIntentsG   = new LinkedHashSet<>();
        private final Set<String> refIntentsAll = new LinkedHashSet<>();
        private final Set<String> refVars       = new TreeSet<>();
        private final Map<String, Set<String>> varToTransitions = new TreeMap<>();

        ReportBuilder(String caseId) { this.caseId = caseId; }

        String build() {
            loadCaseOwnArtifacts();
            buildSkillIndex();
            collectStateMachineReferences();

            StringBuilder sb = new StringBuilder();
            sectionHeader(sb);
            safe(sb, "1. 案例概览", () -> writeOverview(sb));
            safe(sb, "2. 状态机",   () -> writeStateMachine(sb));
            safe(sb, "3. 状态机引用的意图", () -> writeIntents(sb));
            safe(sb, "4. 状态机引用的技能", () -> writeSkills(sb));
            safe(sb, "5. 状态机引用的变量", () -> writeVariables(sb));
            safe(sb, "6. 状态机引用的派生变量", () -> writeDerivations(sb));
            safe(sb, "7. 契约诊断", () -> writeDiagnostics(sb));
            writeAiPrompt(sb);
            return sb.toString();
        }

        // ==================== 索引 ====================

        @SuppressWarnings("unchecked")
        private void loadCaseOwnArtifacts() {
            caseOwnStates = new HashSet<>();
            caseOwnTransitionKeys = new HashSet<>();
            ClassPathResource r =
                new ClassPathResource("config/acls/cases/" + caseId + "/_flow.yaml");
            if (!r.exists()) return;
            try (InputStream is = r.getInputStream()) {
                Map<String, Object> root = yaml.load(is);
                if (root == null) return;
                Map<String, Object> sm = (Map<String, Object>) root.get("stateMachine");
                if (sm == null) return;
                if (sm.get("states") instanceof List<?> list)
                    for (Object o : list) caseOwnStates.add(String.valueOf(o));
                if (sm.get("transitions") instanceof List<?> list)
                    for (Object o : list)
                        if (o instanceof Map<?, ?> m) {
                            String s = str(m.get("source")), t = str(m.get("target"));
                            if (s != null && t != null) caseOwnTransitionKeys.add(s + "→" + t);
                        }
            } catch (Exception e) {
                log.warn("读 case YAML 失败: {}", e.getMessage());
            }
        }

        private void buildSkillIndex() {
            varWriters.clear();
            skillType.clear();
            Set<String> ids = skillRegistry.getAllSkillIds();
            if (ids == null) return;
            for (String id : ids) {
                EmsSkill skill = skillRegistry.getSkill(id);
                if (skill == null) continue;
                skillType.put(id, classifySkill(skill));
                Set<String> outs = new LinkedHashSet<>();
                try {
                    Map<String, Object> o = skill.getSuccessOutputs();
                    if (o != null) outs.addAll(o.keySet());
                } catch (Exception ignored) {}
                try {
                    String svn = skill.getSuccessVarName();
                    if (svn != null && !svn.isBlank()) outs.add(svn);
                } catch (Exception ignored) {}
                for (String v : outs)
                    if (v != null && !v.isBlank())
                        varWriters.computeIfAbsent(v, k -> new TreeSet<>()).add(id);
            }
        }

        private String classifySkill(EmsSkill skill) {
            String n = skill.getClass().getSimpleName();
            return switch (n) {
                case "QuerySkill"                     -> "query";
                case "StandardActionSkill"            -> "standard";
                case "GenericAssertSkill"             -> "generic_assert";
                case "GenericActionSkill"             -> "generic";
                case "DrugPushSkill"                  -> "complex/drug-push";
                case "SynchronizedCardioversionSkill" -> "complex/cardioversion";
                case "AdministerShockAedSkill"        -> "complex/aed";
                default -> n.contains("Map") ? "complex" : n;
            };
        }

        /** ★ 扫描状态机，收集所有被引用的意图/变量 */
        private void collectStateMachineReferences() {
            ResolvedCaseTypeConfig cfg = stateMachineLoader.getConfig(caseId);
            for (TransitionRule t : optList(cfg.getTransitions())) {
                boolean isC = caseOwnTransitionKeys.contains(t.getSource() + "→" + t.getTarget());

                String intent = t.getIntentCode();
                if (intent != null && !intent.isBlank()) {
                    refIntentsAll.add(intent);
                    (isC ? refIntentsC : refIntentsG).add(intent);
                }

                if (t.getCondition() != null) {
                    Matcher m = SPEL_VAR.matcher(t.getCondition());
                    while (m.find()) {
                        String v = m.group(1);
                        refVars.add(v);
                        varToTransitions.computeIfAbsent(v, k -> new LinkedHashSet<>())
                            .add(t.getSource() + " → " + t.getTarget());
                    }
                }
            }
        }

        /** 意图命名反查技能 */
        private String guessSkillByIntent(String intentId) {
            Matcher m = INTENT_ACTION.matcher(intentId);
            if (!m.find()) return null;
            String action = m.group(1);
            Set<String> ids = skillRegistry.getAllSkillIds();
            if (ids == null) return null;
            for (String id : ids)
                if (id.equals(action) || id.contains(action) || action.contains(id)) return id;
            return null;
        }

        /** 变量反查技能集合 */
        private Set<String> lookupWriterSkills(String var) {
            Set<String> out = new LinkedHashSet<>();
            // 1) 精确
            if (varWriters.containsKey(var)) { out.addAll(varWriters.get(var)); return out; }
            // 2) 前缀回退
            for (String key : varWriters.keySet())
                if (var.startsWith(key + ".") || key.startsWith(var + "."))
                    out.addAll(varWriters.get(key));
            // 3) complex Map 手工
            if (var.startsWith("intervention.")) {
                String drug = var.substring("intervention.".length());
                if (drug.contains(".")) drug = drug.substring(0, drug.indexOf('.'));
                String skill = MAP_VAR_TO_SKILL.get(drug);
                if (skill != null) out.add(skill);
            }
            return out;
        }

        /** 状态机引用的全部技能（意图反查 ∪ 变量反查） */
        private Set<String> allReferencedSkills() {
            Set<String> result = new LinkedHashSet<>();
            for (String intent : refIntentsAll) {
                String s = guessSkillByIntent(intent);
                if (s != null) result.add(s);
            }
            for (String v : refVars) result.addAll(lookupWriterSkills(v));
            return result;
        }

        // ==================== 0. 头部 ====================
        private void sectionHeader(StringBuilder sb) {
            sb.append("# ACLS 案例配置报告 — `").append(caseId).append("`\n\n");
            sb.append("- 生成时间：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n");
            sb.append("- 主体案例：**").append(caseId).append("**（含父级 GLOBAL 合并）\n");
            sb.append("- 标注：**`[G]`** = 继承 GLOBAL，**`[C]`** = 案例自有\n");
            sb.append("- **范围**：仅列出**状态机实际引用**的意图 / 技能 / 变量\n\n");
            sb.append("---\n\n");
        }

        // ==================== 1. 概览 ====================
        private void writeOverview(StringBuilder sb) {
            sb.append("## 1. 案例概览\n\n");
            ResolvedCaseTypeConfig cfg = stateMachineLoader.getConfig(caseId);
            List<String> chain = stateMachineLoader.getInheritanceChain(caseId);
            int totalStates = safeSize(cfg.getStates());
            int totalTrans  = safeSize(cfg.getTransitions());

            sb.append("| 项 | 值 |\n|---|---|\n");
            sb.append("| caseId | `").append(caseId).append("` |\n");
            sb.append("| caseName | ").append(nvl(cfg.getCaseName())).append(" |\n");
            sb.append("| version | ").append(nvl(cfg.getVersion())).append(" |\n");
            sb.append("| parent | ").append(nvl(cfg.getParent())).append(" |\n");
            sb.append("| 继承链 | ").append(chain == null ? "-" : String.join(" → ", chain)).append(" |\n");
            sb.append("| 初始状态 | `").append(nvl(cfg.getInitialState())).append("` |\n");
            sb.append("| 合并后状态数 | ").append(totalStates)
                .append("（[C] ").append(caseOwnStates.size())
                .append(" + [G] ").append(Math.max(0, totalStates - caseOwnStates.size())).append("） |\n");
            sb.append("| 合并后转移数 | ").append(totalTrans)
                .append("（[C] ").append(caseOwnTransitionKeys.size())
                .append(" + [G] ").append(Math.max(0, totalTrans - caseOwnTransitionKeys.size())).append("） |\n");
            sb.append("| 终止状态数 | ").append(safeSize(cfg.getEndStates())).append(" |\n");
            sb.append("| **引用意图数** | ").append(refIntentsAll.size())
                .append("（[C] ").append(refIntentsC.size())
                .append(" + [G] ").append(refIntentsG.size()).append("） |\n");
            sb.append("| **引用变量数** | ").append(refVars.size()).append(" |\n");
            sb.append("| **引用技能数** | ").append(allReferencedSkills().size()).append(" |\n\n");
        }

        // ==================== 2. 状态机 ====================
        private void writeStateMachine(StringBuilder sb) {
            sb.append("## 2. 状态机\n\n");
            ResolvedCaseTypeConfig cfg = stateMachineLoader.getConfig(caseId);

            sb.append("### 2.1 状态清单\n\n");
            sb.append("| 来源 | 状态 | 说明 |\n|---|---|---|\n");
            Map<String, String> descs = cfg.getStateDescriptions();
            Set<String> endStates = new HashSet<>(optList(cfg.getEndStates()));
            for (String state : optList(cfg.getStates())) {
                String tag = caseOwnStates.contains(state) ? "**[C]**" : "**[G]**";
                String desc = (descs != null ? descs.getOrDefault(state, "") : "");
                if (endStates.contains(state)) desc = "**【终止】** " + desc;
                sb.append("| ").append(tag).append(" | `").append(state).append("` | ").append(desc).append(" |\n");
            }
            sb.append("\n");

            sb.append("### 2.2 转移明细\n\n");
            sb.append("> **关联 Skill**：`intentCode` 命名反查优先，否则从 condition 变量反查写入 Skill。\n\n");

            Map<String, List<TransitionRule>> byFrom = new LinkedHashMap<>();
            for (TransitionRule t : optList(cfg.getTransitions()))
                byFrom.computeIfAbsent(t.getSource(), k -> new ArrayList<>()).add(t);

            List<String> sorted = new ArrayList<>(byFrom.keySet());
            sorted.sort((a, b) -> {
                boolean ca = caseOwnStates.contains(a), cb = caseOwnStates.contains(b);
                if (ca != cb) return ca ? -1 : 1;
                return a.compareTo(b);
            });

            for (String src : sorted) {
                List<TransitionRule> rules = new ArrayList<>(byFrom.get(src));
                rules.sort((a, b) -> Integer.compare(nvlInt(b.getPriority()), nvlInt(a.getPriority())));

                String stateTag = caseOwnStates.contains(src) ? "[C]" : "[G]";
                sb.append("#### ").append(stateTag).append(" 状态 `").append(src).append("`\n\n");
                sb.append("| 来源 | Prio | 意图/事件 | 关联 Skill | 目标 | 条件 | exitReason | effects |\n");
                sb.append("|---|---|---|---|---|---|---|---|\n");

                for (TransitionRule t : rules) {
                    String tag = caseOwnTransitionKeys.contains(t.getSource() + "→" + t.getTarget())
                        ? "**[C]**" : "**[G]**";
                    sb.append("| ").append(tag);
                    sb.append(" | ").append(nvlInt(t.getPriority()));
                    sb.append(" | ").append(triggerLabel(t));
                    sb.append(" | ").append(relatedSkillLabel(t));
                    sb.append(" | `").append(nvl(t.getTarget())).append("`");
                    sb.append(" | ").append(code(t.getCondition()));
                    sb.append(" | ").append(nvl(t.getExitReason()));
                    sb.append(" | ").append(formatEffects(t)).append(" |\n");
                }
                sb.append("\n");
            }
        }

        private String relatedSkillLabel(TransitionRule t) {
            String intent = t.getIntentCode();
            if (intent != null && !intent.isBlank()) {
                String s = guessSkillByIntent(intent);
                return s != null ? "→ `" + s + "`" : "_(未知路由)_";
            }
            if (t.getCondition() != null) {
                Matcher m = SPEL_VAR.matcher(t.getCondition());
                while (m.find()) {
                    Set<String> sks = lookupWriterSkills(m.group(1));
                    if (!sks.isEmpty()) return "← `" + first(sks) + "`";
                }
            }
            return "_(变量驱动)_";
        }

        private String triggerLabel(TransitionRule t) {
            if (t.getIntentCode() != null && !t.getIntentCode().isBlank())
                return "`" + t.getIntentCode() + "`";
            if (t.getEvent() != null && !t.getEvent().isBlank())
                return "event:`" + t.getEvent() + "`";
            return "_(无)_";
        }

        private String code(String s) { return (s == null || s.isBlank()) ? "_(无)_" : "`" + s + "`"; }

        private String formatEffects(TransitionRule t) {
            if (t.getEffects() == null || t.getEffects().isEmpty()) return "_(无)_";
            return t.getEffects().stream()
                .map(ef -> "`" + ef.getVar() + "=" + ef.getValue() + "`")
                .collect(Collectors.joining("<br>"));
        }

        // ==================== 3. 引用的意图 ====================
        private void writeIntents(StringBuilder sb) {
            sb.append("## 3. 状态机引用的意图\n\n");
            sb.append("> 数据源：`_flow.yaml` 里 `transitions[].intentCode` 字段。\n\n");

            if (refIntentsAll.isEmpty()) {
                sb.append("_(状态机未引用任何意图，全部为条件驱动)_\n\n");
                return;
            }

            sb.append("### 3.1 意图清单（").append(refIntentsAll.size()).append(" 个）\n\n");
            sb.append("| 来源 | 意图 | 关联 Skill | 引用次数 |\n|---|---|---|---|\n");

            Map<String, Integer> intentCount = countIntentRefs();
            for (String id : refIntentsAll) {
                String src = refIntentsC.contains(id) ? "[C]" : "[G]";
                String skill = guessSkillByIntent(id);
                sb.append("| ").append(src)
                    .append(" | `").append(id).append("`")
                    .append(" | ").append(skill != null ? "`"+skill+"`" : "_(未匹配)_")
                    .append(" | ").append(intentCount.getOrDefault(id, 0))
                    .append(" |\n");
            }
            sb.append("\n");

            sb.append("### 3.2 未匹配的意图\n\n");
            List<String> unmatched = refIntentsAll.stream()
                .filter(id -> guessSkillByIntent(id) == null)
                .collect(Collectors.toList());
            if (unmatched.isEmpty()) {
                sb.append("✅ 全部意图都能反查到技能。\n\n");
            } else {
                sb.append("以下意图无法通过命名规则反查到技能，需人工核对：\n\n");
                unmatched.forEach(id -> sb.append("- `").append(id).append("`\n"));
                sb.append("\n");
            }
        }

        private Map<String, Integer> countIntentRefs() {
            Map<String, Integer> c = new LinkedHashMap<>();
            ResolvedCaseTypeConfig cfg = stateMachineLoader.getConfig(caseId);
            for (TransitionRule t : optList(cfg.getTransitions())) {
                String id = t.getIntentCode();
                if (id != null && !id.isBlank()) c.merge(id, 1, Integer::sum);
            }
            return c;
        }

        // ==================== 4. 引用的技能 ====================
        private void writeSkills(StringBuilder sb) {
            sb.append("## 4. 状态机引用的技能\n\n");
            sb.append("> **来源**：① 意图命名反查  ② condition 变量反查写入者。\n\n");

            Set<String> refs = allReferencedSkills();
            if (refs.isEmpty()) {
                sb.append("_(状态机未引用任何技能)_\n\n");
                return;
            }

            sb.append("### 4.1 技能清单（").append(refs.size()).append(" 个）\n\n");
            sb.append("| 技能 | 实现类 | 类型 | 来源 | 写出变量 |\n");
            sb.append("|---|---|---|---|---|\n");

            for (String id : refs) {
                EmsSkill skill = skillRegistry.getSkill(id);
                String cls = skill != null ? skill.getClass().getSimpleName() : "_(未注册)_";
                String type = skillType.getOrDefault(id, "?");

                Set<String> sources = new LinkedHashSet<>();
                for (String intent : refIntentsAll)
                    if (id.equals(guessSkillByIntent(intent))) sources.add("意图 `" + intent + "`");
                for (String v : refVars)
                    if (lookupWriterSkills(v).contains(id)) sources.add("变量 `" + v + "`");

                Set<String> outs = new LinkedHashSet<>();
                for (String v : refVars) if (lookupWriterSkills(v).contains(id)) outs.add(v);

                sb.append("| `").append(id).append("`")
                    .append(" | ").append(cls)
                    .append(" | ").append(type)
                    .append(" | ").append(sources.isEmpty() ? "-"
                        : sources.stream().limit(3).collect(Collectors.joining("<br>"))
                        + (sources.size() > 3 ? "<br>_...等 " + sources.size() + " 处_" : ""))
                    .append(" | ")
                    .append(outs.isEmpty() ? "_(Map 写入/无显式声明)_"
                        : outs.stream().map(v -> "`"+v+"`").collect(Collectors.joining(", ")))
                    .append(" |\n");
            }
            sb.append("\n");

            sb.append("### 4.2 未注册的引用\n\n");
            List<String> unregistered = refs.stream()
                .filter(id -> skillRegistry.getSkill(id) == null)
                .collect(Collectors.toList());
            if (unregistered.isEmpty()) {
                sb.append("✅ 全部引用的技能都已在 `SkillRegistry` 注册。\n\n");
            } else {
                sb.append("以下技能被引用但**未注册**，会导致运行期找不到：\n\n");
                unregistered.forEach(id -> sb.append("- `").append(id).append("`\n"));
                sb.append("\n");
            }
        }

        // ==================== 5. 引用的变量 ====================
        private void writeVariables(StringBuilder sb) {
            sb.append("## 5. 状态机引用的变量\n\n");
            sb.append("> 数据源：所有 `condition` 里 `#变量名`。只列出被引用的变量。\n\n");

            if (refVars.isEmpty()) {
                sb.append("_(状态机 condition 未引用任何变量)_\n\n");
                return;
            }

            // 5.1 按前缀统计
            sb.append("### 5.1 按前缀分组（共 ").append(refVars.size()).append(" 个）\n\n");
            Map<String, List<String>> byPrefix = new TreeMap<>();
            for (String v : refVars) {
                String p = v.contains(".") ? v.substring(0, v.indexOf('.')) : "(无)";
                byPrefix.computeIfAbsent(p, k -> new ArrayList<>()).add(v);
            }
            sb.append("| 前缀 | 数量 |\n|---|---|\n");
            byPrefix.forEach((p, l) -> sb.append("| `").append(p).append("` | ").append(l.size()).append(" |\n"));
            sb.append("\n");

            // 5.2 变量清单 + 默认值 + 引用数
            sb.append("### 5.2 变量清单\n\n");
            sb.append("| 变量 | 引用转移数 | 默认值 |\n|---|---|---|\n");
            Map<String, VariableMeta> metaMap = null;
            try { metaMap = variableMetaLoader.getVariablesForCase(caseId); } catch (Exception ignored) {}

            for (String v : refVars) {
                String def = "_(未定义)_";
                if (metaMap != null && metaMap.containsKey(v)) {
                    Object dv = metaMap.get(v).getDefaultValue();
                    def = dv != null ? "`" + shortVal(dv) + "`" : "_(null)_";
                }
                sb.append("| `").append(v).append("`")
                    .append(" | ").append(varToTransitions.getOrDefault(v, Collections.emptySet()).size())
                    .append(" | ").append(def)
                    .append(" |\n");
            }
            sb.append("\n");

            // 5.3 读-写缺口
            sb.append("### 5.3 变量 → 写入者（读-写缺口检测）\n\n");
            sb.append("> ⚠️ **无写入者**的变量会导致 condition 永远为 false，状态机**永久卡住**。\n\n");
            sb.append("| 变量 | 写入者 Skill | 判定 |\n|---|---|---|\n");
            for (String v : refVars) {
                Set<String> writers = lookupWriterSkills(v);
                if (writers.isEmpty()) {
                    sb.append("| `").append(v).append("` | - | ⚠️ **读-写缺口** |\n");
                } else {
                    sb.append("| `").append(v).append("` | ")
                        .append(writers.stream().map(s -> "`"+s+"`").collect(Collectors.joining(", ")))
                        .append(" | ✅ |\n");
                }
            }
            sb.append("\n");

            // 5.4 变量 → 引用转移
            sb.append("### 5.4 变量 → 引用它的转移\n\n");
            sb.append("| 变量 | 引用转移 |\n|---|---|\n");
            for (Map.Entry<String, Set<String>> e : varToTransitions.entrySet()) {
                sb.append("| `").append(e.getKey()).append("` | ")
                    .append(e.getValue().stream().map(s -> "`"+s+"`").collect(Collectors.joining("<br>")))
                    .append(" |\n");
            }
            sb.append("\n");
        }

        // ==================== 6. 引用的派生变量 ====================
        private void writeDerivations(StringBuilder sb) {
            sb.append("## 6. 状态机引用的派生变量\n\n");
            Set<String> derived = refVars.stream()
                .filter(v -> v.startsWith("derived."))
                .collect(Collectors.toCollection(TreeSet::new));

            if (derived.isEmpty()) {
                sb.append("_(状态机未引用 derived.* 变量)_\n\n");
                return;
            }

            sb.append("| 变量 | 引用转移数 | 写入者 |\n|---|---|---|\n");
            for (String v : derived) {
                Set<String> writers = lookupWriterSkills(v);
                sb.append("| `").append(v).append("`")
                    .append(" | ").append(varToTransitions.getOrDefault(v, Collections.emptySet()).size())
                    .append(" | ")
                    .append(writers.isEmpty()
                        ? "_(`_derivations.yaml` 派生规则)_"
                        : writers.stream().map(s -> "`"+s+"`").collect(Collectors.joining(", ")))
                    .append(" |\n");
            }
            sb.append("\n");
            sb.append("> 说明：`derived.*` 由 `_derivations.yaml` 自动派生，非 Skill 写入。\n\n");
        }

        // ==================== 7. 契约诊断 ====================
        private void writeDiagnostics(StringBuilder sb) {
            sb.append("## 7. 契约诊断\n\n");
            ContractDiagnostics diag;
            try { diag = contractRegistry.getDiagnostics(caseId); }
            catch (Exception e) {
                sb.append("_(读取失败: ").append(e.getMessage()).append(")_\n\n"); return;
            }
            if (diag == null) { sb.append("_(无诊断数据)_\n\n"); return; }

            try {
                List<String> dead = diag.getDeadTransitions();
                sb.append("### 7.1 死转移：").append(safeSize(dead)).append(" 条\n\n");
                sb.append("> **注意**：本项目转移为**条件驱动**，启动期静态扫描把\"无 intentCode\"标为死转移。\n");
                sb.append("> 这些转移在 §2.2 里已通过\"关联 Skill\"列重新分类，不是真正的死转移。\n\n");
            } catch (Exception ignored) {}

            try {
                List<String> undef = diag.getUndefinedDerivedVars();
                sb.append("### 7.2 未定义派生变量：").append(safeSize(undef)).append("\n\n");
                if (undef != null && !undef.isEmpty())
                    undef.forEach(v -> sb.append("- `").append(v).append("`\n"));
                else sb.append("_(无)_\n");
                sb.append("\n");
            } catch (Exception ignored) {}

            try {
                List<String> cyc = diag.getCircularDependencies();
                sb.append("### 7.3 循环依赖：").append(safeSize(cyc)).append("\n\n");
                if (cyc != null && !cyc.isEmpty())
                    cyc.forEach(v -> sb.append("- `").append(v).append("`\n"));
                else sb.append("_(无)_\n");
                sb.append("\n");
            } catch (Exception ignored) {}
        }

        // ==================== 8. AI 提示词 ====================
        private void writeAiPrompt(StringBuilder sb) {
            sb.append("---\n\n");
            sb.append("## 🤖 给 AI 的分析提示词\n\n");
            sb.append("```\n");
            sb.append("你是 ACLS 急救培训系统的配置审核专家。上面是 ").append(caseId).append(" 案例的配置。\n");
            sb.append("**注意**：本报告只列出状态机实际引用的意图/技能/变量，");
            sb.append("未列出的不代表不存在，只代表未被状态机使用。\n\n");
            sb.append("标注 [C] 案例自有、[G] 继承自 GLOBAL。请重点审核：\n\n");
            sb.append("1. §2.2 转移明细\n");
            sb.append("   - 同 source 下 priority 相同的转移（运行期会抛 IllegalStateException）\n");
            sb.append("   - 关联 Skill 为 `_(未知路由)_` 或 `_(变量驱动)_` 但有变量的转移\n\n");
            sb.append("2. §3.2 未匹配意图\n");
            sb.append("   - 命名与技能不一致的意图是否漏配路由\n\n");
            sb.append("3. §4.2 未注册技能\n");
            sb.append("   - 被引用但未注册 → 运行时必然失败\n\n");
            sb.append("4. §5.3 读-写缺口（P0 重点）\n");
            sb.append("   - 标 ⚠️ 的变量 → 状态机会永久卡住\n");
            sb.append("   - 需要新增哪个 Skill 的 getSuccessOutputs()\n\n");
            sb.append("5. §6 派生变量\n");
            sb.append("   - 引用但无派生规则 → 需补 _derivations.yaml\n\n");
            sb.append("输出：Top 10 问题清单，标注 P0/P1/P2，每条给出具体修复动作。\n");
            sb.append("```\n");
        }

        // ==================== 工具 ====================
        private void safe(StringBuilder sb, String section, Runnable action) {
            try { action.run(); }
            catch (Throwable e) {
                sb.append("## ").append(section).append("\n\n");
                sb.append("⚠️ 章节生成失败：").append(e.getMessage()).append("\n\n");
            }
        }

        private String str(Object o) { return o == null ? null : o.toString(); }
        private int nvlInt(Integer i) { return i == null ? 0 : i; }
        private String nvl(String s) { return s == null ? "" : s; }
        private int safeSize(Collection<?> c) { return c == null ? 0 : c.size(); }
        private <T> List<T> optList(List<T> l) { return l == null ? Collections.emptyList() : l; }
        private String first(Collection<String> c) { return c.iterator().next(); }
        private String shortVal(Object v) {
            if (v == null) return "null";
            String s = v.toString();
            return s.length() > 60 ? s.substring(0, 57) + "..." : s;
        }
    }
}

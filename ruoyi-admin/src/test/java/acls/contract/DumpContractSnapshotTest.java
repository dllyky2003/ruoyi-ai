package acls.contract;

import com.ruoyi.acls.contract.ContractSnapshot;

import com.ruoyi.acls.contract.ContractSnapshotExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(
    classes = org.ruoyi.RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class DumpContractSnapshotTest {

    @Autowired
    private ContractSnapshotExporter exporter;

    private static final String CASE_ID = "stable_narrow";

    @Test
    void dumpSnapshot() throws Exception {
        ContractSnapshot snap = exporter.build(CASE_ID);

        System.out.println("\n===== ContractSnapshot: " + CASE_ID + " =====");
        System.out.println("caseId              : " + snap.getCaseId());
        System.out.println("caseName            : " + snap.getCaseName());
        System.out.println("version             : " + snap.getVersion());
        System.out.println("parent              : " + snap.getParent());
        System.out.println("initialState        : " + snap.getInitialState());
        System.out.println("terminalStates      : " + snap.getTerminalStates());
        System.out.println("globalEventIntents  : " + snap.getGlobalEventIntents());
        System.out.println("alwaysAllowedIntents: " + snap.getAlwaysAllowedIntents());
        System.out.println("allowUnlistedTrans  : " + snap.isAllowUnlistedTransitions());
        System.out.println("defaultTransAction  : " + snap.getDefaultTransitionAction());
        System.out.println("snapshotVersion     : " + snap.getSnapshotVersion());
        System.out.println("generatedAt         : " + snap.getGeneratedAt());
        System.out.println();

        // ============ 状态模板 ============
        List<ContractSnapshot.StateTemplate> states = snap.getStateTemplates();
        System.out.println("【状态模板】 共 " + states.size() + " 个");
        for (ContractSnapshot.StateTemplate st : states) {
            StringBuilder sb = new StringBuilder();
            sb.append("   ").append(padRight(st.getId(), 38));
            sb.append(" terminal=").append(st.isTerminal());
            if (st.getName() != null && !st.getName().equals(st.getId())) {
                sb.append("  name=").append(st.getName());
            }
            System.out.println(sb);
        }
        System.out.println();

        // ============ 意图 ============
        List<ContractSnapshot.IntentSnapshot> intents = snap.getIntents();
        System.out.println("【意图】 共 " + intents.size() + " 个");
        for (ContractSnapshot.IntentSnapshot i : intents) {
            StringBuilder sb = new StringBuilder();
            sb.append("   ").append(padRight(i.getIntentCode(), 32));
            sb.append(" name=").append(i.getNameCn());
            if (i.getActType() != null) sb.append("  act=").append(i.getActType());
            if (i.getDomainCode() != null) sb.append("  domain=").append(i.getDomainCode());
            if (i.getSubDomain() != null) sb.append("  sub=").append(i.getSubDomain());
            sb.append("  general=").append(i.isGeneral())
                .append(" specific=").append(i.isSpecific());
            System.out.println(sb);

            if (i.getRegex() != null && !i.getRegex().isEmpty()) {
                System.out.println("        regex        : " + i.getRegex());
            }
            if (i.getNegativeRegex() != null && !i.getNegativeRegex().isEmpty()) {
                System.out.println("        negativeRegex: " + i.getNegativeRegex());
            }
            if (i.getStateTagsAny() != null && !i.getStateTagsAny().isEmpty()) {
                System.out.println("        stateTagsAny : " + i.getStateTagsAny());
            }
            if (i.getStateTagsAll() != null && !i.getStateTagsAll().isEmpty()) {
                System.out.println("        stateTagsAll : " + i.getStateTagsAll());
            }
            if (i.getContextVars() != null && !i.getContextVars().isEmpty()) {
                System.out.println("        contextVars  : " + i.getContextVars());
            }
            if (i.getOutArtifact() != null) {
                System.out.println("        outArtifact  : " + i.getOutArtifact()
                    + "  → " + i.getDownstream());
            }
        }
        System.out.println();

        // ============ 变量 ============
        ContractSnapshot.VariableSnapshot vs = snap.getVariables();
        System.out.println("【变量】");
        System.out.println("   prefixes        : " + vs.getPrefixes());
        System.out.println("   readable  (" + vs.getReadable().size() + ")");
        System.out.println("   writable  (" + vs.getWritable().size() + ")");
        System.out.println("   derived   (" + vs.getDerived().size() + ")");
        System.out.println("   derivedList : " + vs.getDerived());
        System.out.println("   defaults size: " + vs.getDefaults().size());
        System.out.println();

        // ============ 派生规则 ============
        List<ContractSnapshot.DerivationSnapshot> derivations = snap.getDerivations();
        System.out.println("【派生规则】 共 " + derivations.size() + " 条");
        for (ContractSnapshot.DerivationSnapshot d : derivations) {
            System.out.println("   " + padRight(d.getTargetVar(), 32)
                + " = " + d.getExpression()
                + "   dependsOn=" + d.getDependsOn());
        }
        System.out.println();

        // ============ 技能 ============
        List<ContractSnapshot.SkillSnapshot> skills = snap.getSkills();
        System.out.println("【技能】 共 " + skills.size() + " 个（只展示前 30 个）");
        int count = 0;
        for (ContractSnapshot.SkillSnapshot s : skills) {
            if (count++ >= 30) {
                System.out.println("   ... 还有 " + (skills.size() - 30) + " 个");
                break;
            }
            System.out.println("   " + padRight(s.getSkillId(), 32)
                + " intent=" + s.getIntentCode()
                + " writes=" + s.getWrites());
        }
        System.out.println();

        // ============ 护栏 ============
        List<ContractSnapshot.GuardRailSnapshot> guards = snap.getGuardRails();
        System.out.println("【护栏】 共 " + guards.size() + " 条");
        for (ContractSnapshot.GuardRailSnapshot g : guards) {
            System.out.println("   " + padRight(g.getRuleId(), 32)
                + " severity=" + g.getSeverity()
                + " target=" + g.getTarget());
            if (g.getCondition() != null) {
                System.out.println("        condition : " + g.getCondition());
            }
            if (g.getAction() != null) {
                System.out.println("        action    : " + g.getAction());
            }
        }
        System.out.println();

        // ============ 反射规则 ============
        List<ContractSnapshot.ReflexRuleSnapshot> reflexes = snap.getReflexRules();
        System.out.println("【反射规则】 共 " + reflexes.size() + " 条");
        for (ContractSnapshot.ReflexRuleSnapshot r : reflexes) {
            System.out.println("   " + padRight(r.getRuleId(), 20)
                + " trigger=" + r.getTrigger()
                + "  → " + r.getTargetState());
            if (r.getAction() != null) {
                System.out.println("        action: " + r.getAction());
            }
        }
        System.out.println();

        // ============ 规则 ============
        List<ContractSnapshot.RuleSnapshot> rules = snap.getRules();
        System.out.println("【规则】 共 " + rules.size() + " 条");
        for (ContractSnapshot.RuleSnapshot r : rules) {
            System.out.println("   " + padRight(r.getRuleId(), 20)
                + " type=" + r.getType()
                + " expr=" + r.getExpression());
        }
        System.out.println();

        // ============ 当前转移（按 source 分组） ============
        List<ContractSnapshot.TransitionSnapshot> transitions = snap.getCurrentTransitions();
        System.out.println("【当前转移】 共 " + transitions.size() + " 条");
        Map<String, List<ContractSnapshot.TransitionSnapshot>> byFrom = new LinkedHashMap<>();
        for (ContractSnapshot.TransitionSnapshot t : transitions) {
            byFrom.computeIfAbsent(t.getSource(), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<String, List<ContractSnapshot.TransitionSnapshot>> e : byFrom.entrySet()) {
            System.out.println("【状态】" + e.getKey());
            List<ContractSnapshot.TransitionSnapshot> list = new ArrayList<>(e.getValue());
            list.sort((a, b) -> Integer.compare(
                b.getPriority() != null ? b.getPriority() : 0,
                a.getPriority() != null ? a.getPriority() : 0));
            for (ContractSnapshot.TransitionSnapshot t : list) {
                StringBuilder sb = new StringBuilder();
                sb.append("   ");
                if (t.getIntentCode() != null && !t.getIntentCode().isBlank()) {
                    sb.append("说: ").append(t.getIntentCode()).append("  ");
                } else {
                    sb.append("(无条件意图)          ");
                }
                sb.append("⇒  ").append(t.getTarget());
                sb.append("   [prio=").append(t.getPriority()).append("]");
                System.out.println(sb);
                if (t.getCondition() != null && !t.getCondition().isBlank()) {
                    System.out.println("        ⤷ 条件: " + t.getCondition());
                }
                if (t.getExitReason() != null && !t.getExitReason().isBlank()) {
                    System.out.println("        ⤷ exitReason: " + t.getExitReason());
                }
                if (t.getEffects() != null && !t.getEffects().isEmpty()) {
                    System.out.println("        ⤷ effects: " + t.getEffects());
                }
            }
            System.out.println();
        }

        // ============ 2025 AHA 指南规则 ============
        System.out.println("【2025 AHA 指南规则】 共 " + snap.getGuidelineRules().size() + " 条");
        for (ContractSnapshot.GuidelineRule g : snap.getGuidelineRules()) {
            System.out.println("   " + padRight(g.getRuleId(), 30)
                + " severity=" + g.getSeverity());
            System.out.println("        target    : " + g.getTarget());
            System.out.println("        appliesWhen: " + g.getAppliesWhen());
            System.out.println("        require   : " + g.getRequire());
            System.out.println("        ref       : " + g.getGuidelineRef());
        }
        System.out.println();

        // ============ 终止性规则 ============
        System.out.println("【终止性规则】 共 " + snap.getTerminationRules().size() + " 条");
        for (ContractSnapshot.TerminationRule t : snap.getTerminationRules()) {
            System.out.println("   " + padRight(t.getRuleId(), 8)
                + " severity=" + t.getSeverity()
                + "  " + t.getDescription());
        }
        System.out.println();

        System.out.println("===== dump 完成 =====");
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s;
        return s + " ".repeat(n - s.length());
    }
}

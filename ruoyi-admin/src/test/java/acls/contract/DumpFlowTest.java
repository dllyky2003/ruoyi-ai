package acls.contract;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

@SpringBootTest(
    classes = org.ruoyi.RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class DumpFlowTest {

    @Autowired private YamlStateMachineLoader loader;

    @Test
    void dumpStableNarrowFlow() {
        ResolvedCaseTypeConfig cfg = loader.getConfig("stable_narrow");
        System.out.println("\n===== stable_narrow 状态机全表（含 condition） =====");
        System.out.println("初始状态: " + cfg.getInitialState());

        List<TransitionRule> transitions = cfg.getTransitions();
        System.out.println("转移总数: " + (transitions == null ? 0 : transitions.size()));
        System.out.println();

        Map<String, List<TransitionRule>> byFrom = new LinkedHashMap<>();
        for (TransitionRule t : transitions) {
            byFrom.computeIfAbsent(t.getSource(), k -> new ArrayList<>()).add(t);
        }

        for (Map.Entry<String, List<TransitionRule>> e : byFrom.entrySet()) {
            System.out.println("【状态】" + e.getKey());

            // 按 priority 降序排（先匹配高的）
            List<TransitionRule> rules = new ArrayList<>(e.getValue());
            rules.sort((a, b) -> Integer.compare(
                b.getPriority() != null ? b.getPriority() : 0,
                a.getPriority() != null ? a.getPriority() : 0));

            for (TransitionRule t : rules) {
                StringBuilder sb = new StringBuilder();
                sb.append("   ");
                if (t.getIntentCode() != null && !t.getIntentCode().isBlank()) {
                    sb.append("说: ").append(t.getIntentCode()).append("  ");
                } else {
                    sb.append("(无条件意图)          ");
                }
                sb.append("⇒  ").append(t.getTarget());
                sb.append("   [prio=").append(t.getPriority()).append("]");

                if (t.getCondition() != null && !t.getCondition().isBlank()) {
                    sb.append("\n        ⤷ 条件: ").append(t.getCondition());
                }
                if (t.getExitReason() != null && !t.getExitReason().isBlank()) {
                    sb.append("\n        ⤷ exitReason: ").append(t.getExitReason());
                }
                if (t.getEffects() != null && !t.getEffects().isEmpty()) {
                    sb.append("\n        ⤷ effects: ");
                    t.getEffects().forEach(ef ->
                        sb.append(ef.getVar()).append("=").append(ef.getValue()).append("  "));
                }
                System.out.println(sb);
            }
            System.out.println();
        }

        Set<String> global = cfg.getGlobalEventIntents();
        if (global != null && !global.isEmpty()) {
            System.out.println("【全局事件意图（任意状态可用）】");
            global.forEach(g -> System.out.println("   " + g));
        }
    }
}

package com.ruoyi.acls.tutor.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 效应映射表 — 导师视图报告生成器。
 *
 * <p><b>读者是 ACLS 教学导师，不是开发者。</b>报告只使用效应映射表里的中文字段
 * （preconditionCn / effectCn 等），不出现状态机状态ID、SpEL表达式、Java 类名——
 * 这些属于 {@code CaseConfigReportService} 的职责范围。两份报告数据源不同、
 * 读者不同，刻意不合并，也不互相引用。
 *
 * <p>本报告的核心价值不是复述表格，而是做「临床完整性检查」：概率待定、
 * 致命风险场景、默认兜底缺失、开放问题未清空——这些是导师审阅时最容易漏看的地方。
 * 这是 {@code CaseConfigReportService} §5.3「读-写缺口检测」在临床内容侧的对应物：
 * 那边检测「变量没人写会导致状态机卡死」，这边检测「表格没写完会导致审阅走过场」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectMappingReportService {

    private final EffectMappingLoader loader;

    /** 生成指定药物/操作的 Markdown 导师视图报告 */
    public String generateReport(String drug) {
        if (drug == null || drug.isBlank()) {
            throw new IllegalArgumentException("drug 不能为空");
        }
        Map<String, Object> root = loader.load(drug);
        if (root == null) {
            return "# 效应映射表尚未创建：`" + drug + "`\n\n"
                + "> 尚未有针对 `" + drug + "` 的效应映射表。若正在按设计契约推进新药物/操作，"
                + "请先起草 `config/acls/effects/" + drug + ".yaml`。\n";
        }
        return new ReportBuilder(drug, root).build();
    }

    // ============================================================
    // 内部构建器
    // ============================================================
    @SuppressWarnings("unchecked")
    private static class ReportBuilder {

        private final String drug;
        private final Map<String, Object> root;
        private final List<String> checklist = new ArrayList<>();

        ReportBuilder(String drug, Map<String, Object> root) {
            this.drug = drug;
            this.root = root;
        }

        String build() {
            StringBuilder sb = new StringBuilder();
            writeHeader(sb);
            writeOpenQuestions(sb);
            writeScenarios(sb);
            writeChecklist(sb);
            return sb.toString();
        }

        // ==================== 头部 ====================

        private void writeHeader(StringBuilder sb) {
            String version = str(root.get("version"));
            String reviewStatus = str(root.get("reviewStatus"));
            String designRef = str(root.get("designRef"));
            boolean approved = "approved".equals(reviewStatus);

            sb.append("# 效应映射表 — ").append(drug).append("\n\n");
            sb.append(approved ? "✅ 已审阅通过" : "⚠️ 待临床审阅");
            sb.append("　·　版本 ").append(nvl(version, "-"));
            if (designRef != null && !designRef.isBlank()) {
                sb.append("　·　设计依据 `").append(designRef).append("`");
            }
            sb.append("\n\n");

            if (!approved) {
                sb.append("> 本表尚未经过临床审阅，场景描述与时序参数可能调整，"
                    + "暂不作为最终教学依据。\n\n");
            }
            sb.append("---\n\n");
        }

        // ==================== 待决策问题 ====================

        private void writeOpenQuestions(StringBuilder sb) {
            List<String> questions = (List<String>) root.get("openQuestions");
            if (questions == null || questions.isEmpty()) return;

            sb.append("## 待决策问题\n\n");
            sb.append("> 以下问题未解决前，本表不应标记为「已审阅通过」。\n\n");
            for (String q : questions) {
                sb.append("- ").append(q).append("\n");
            }
            sb.append("\n---\n\n");
        }

        // ==================== 场景与效应 ====================

        private void writeScenarios(StringBuilder sb) {
            List<Map<String, Object>> scenarios = (List<Map<String, Object>>) root.get("scenarios");
            if (scenarios == null || scenarios.isEmpty()) {
                sb.append("_(尚未定义任何场景)_\n\n");
                return;
            }

            sb.append("## 场景与效应\n\n");
            for (Map<String, Object> scenario : scenarios) {
                String scenarioId = str(scenario.get("id"));
                String nameCn = str(scenario.get("nameCn"));
                sb.append("### 场景 ").append(scenarioId).append("：").append(nvl(nameCn, "_(未命名)_")).append("\n\n");

                List<Map<String, Object>> mappings = (List<Map<String, Object>>) scenario.get("mappings");
                if (mappings == null || mappings.isEmpty()) {
                    sb.append("_(本场景下暂无映射)_\n\n");
                    continue;
                }

                sb.append("| 映射ID | 前置条件 | 效应 | 时序 | 风险 | 副作用 |\n");
                sb.append("|---|---|---|---|---|---|\n");
                for (Map<String, Object> m : mappings) {
                    writeRow(sb, m);
                    runChecklist(m);
                }
                sb.append("\n");
            }
        }

        private void writeRow(StringBuilder sb, Map<String, Object> m) {
            String mappingId = str(m.get("id"));
            String preconditionCn = str(m.get("preconditionCn"));
            String effectCn = str(m.get("effectCn"));
            String timing = formatTiming((Map<String, Object>) m.get("timing"));
            String risk = nvl(str(m.get("riskFlag")), "none");
            List<String> sideEffects = (List<String>) m.get("sideEffects");
            boolean fallback = Boolean.TRUE.equals(m.get("isDefaultFallback"));

            sb.append("| `").append(mappingId).append("`");
            if (fallback) sb.append(" 🔻默认兜底");
            sb.append(" | ").append(nvl(preconditionCn, "⚠️ _(未填写)_"));
            sb.append(" | ").append(nvl(effectCn, "⚠️ _(未填写)_"));
            sb.append(" | ").append(timing);
            sb.append(" | ").append(riskBadge(risk));
            sb.append(" | ").append(sideEffects == null || sideEffects.isEmpty()
                ? "-" : String.join("、", sideEffects));
            sb.append(" |\n");
        }

        private String riskBadge(String risk) {
            return switch (risk) {
                case "fatal" -> "🔴 致命";
                case "caution" -> "🟡 警示";
                default -> "-";
            };
        }

        private String formatTiming(Map<String, Object> timing) {
            if (timing == null) return "⚠️ _(未填写)_";
            String type = str(timing.get("type"));
            if ("delayed".equals(type)) {
                Object sec = timing.get("seconds");
                return sec == null ? "延迟（未填秒数）" : "延迟 " + sec + "s";
            }
            return "立即";
        }

        // ==================== 临床完整性检查清单 ====================

        private void runChecklist(Map<String, Object> m) {
            String mappingId = nvl(str(m.get("id")), "(无ID)");

            if (isBlank(str(m.get("preconditionCn")))) {
                checklist.add(mappingId + "：缺少中文前置条件描述");
            }
            if (isBlank(str(m.get("effectCn")))) {
                checklist.add(mappingId + "：缺少中文效应描述");
            }
            if (m.get("timing") == null) {
                checklist.add(mappingId + "：缺少时序参数（immediate / delayed）");
            }

            String resolutionMode = str(m.get("resolutionMode"));
            if ("probabilistic".equals(resolutionMode)) {
                List<Map<String, Object>> outcomes = (List<Map<String, Object>>) m.get("outcomes");
                if (outcomes == null || outcomes.isEmpty()) {
                    checklist.add(mappingId + "：resolutionMode=probabilistic 但未填写 outcomes");
                } else {
                    boolean anyMissing = outcomes.stream().anyMatch(o -> o.get("probability") == null);
                    if (anyMissing) {
                        checklist.add(mappingId + "：概率值待补充（临床数据缺失）");
                    }
                }
            }

            if ("fatal".equals(str(m.get("riskFlag")))) {
                checklist.add(mappingId + "：标记为致命风险场景，建议双人临床审阅后再批准");
            }
        }

        private void writeChecklist(StringBuilder sb) {
            sb.append("---\n\n## 审阅清单\n\n");
            if (checklist.isEmpty()) {
                sb.append("✅ 未发现待办事项。\n");
                return;
            }
            sb.append("> 以下 ").append(checklist.size()).append(" 项需要临床导师确认或补充：\n\n");
            for (String item : checklist) {
                sb.append("- [ ] ").append(item).append("\n");
            }
        }

        // ==================== 工具 ====================

        private String str(Object o) { return o == null ? null : o.toString(); }
        private boolean isBlank(String s) { return s == null || s.isBlank(); }
        private String nvl(String s, String fallback) { return isBlank(s) ? fallback : s; }
    }
}

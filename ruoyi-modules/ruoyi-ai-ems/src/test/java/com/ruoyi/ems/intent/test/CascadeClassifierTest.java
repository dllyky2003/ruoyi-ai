package com.ruoyi.ems.intent.test;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.classifier.CascadeIntentClassifier;
import com.ruoyi.ems.intent.model.IntentDefinition;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 级联分类器（前三级）集中回归测试。
 *
 * <p><b>为什么不使用 {@code @SpringBootTest}</b>：
 * {@code @SpringBootTest} 会走 {@code SpringApplication} 流程，隐式开启
 * {@code @EnableAutoConfiguration}，从而加载 RuoYi 的自动配置类
 * （MybatisPlusConfig / AllUrlHandler / SpringDocConfig 等），
 * 导致测试上下文启动失败。本测试改用
 * {@code @ExtendWith(SpringExtension.class) + @ContextConfiguration}，
 * 只加载指定的 {@link CascadeTestConfig}，不触发任何自动配置。
 *
 * <p><b>为什么加 {@code @TestPropertySource}</b>：
 * 使用 {@code @ContextConfiguration} 时不会加载 {@code application.yml}，
 * 因此 {@code @Value} 拿不到配置文件的属性值。这里显式传入 embedding 相关属性，
 * 让 {@code EmbeddingModelWrapper} 能读到真实配置。
 *
 * <p>策略：
 * <ul>
 *   <li>直接调 {@link CascadeIntentClassifier#classify}，不走 LLM 兜底</li>
 *   <li>样本 = 每个意图的 {@code examples}（剔除负例） + {@code standard_script}</li>
 *   <li>按层统计：PRAGMATIC / route_v1_3 / NORMALIZER / keyword / vector</li>
 *   <li>输出三类清单：未命中 / 命中错分 / 按意图覆盖率</li>
 * </ul>
 *
 * <p>本测试不做断言，纯诊断输出。CI 想加门禁可自行包装 assert。
 */
@Slf4j
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CascadeTestConfig.class)
@TestPropertySource(properties = {
    "acls.embedding.enabled=true",
    "acls.embedding.provider=ollama",
    "acls.embedding.base-url=http://localhost:11434",
    "acls.embedding.model=bge-m3",
    "acls.embedding.dimension=1024",
    "acls.embedding.connect-timeout-ms=3000",
    "acls.embedding.read-timeout-ms=10000"
})
@ActiveProfiles("dev")
@DisplayName("级联分类器（前三级）回归测试")
class CascadeClassifierTest {

    /** 被测案例，可改成其他 caseId */
    private static final String CASE_ID = "stable_narrow";

    /** 是否只测 S 类（案例专属）；false = 通用 + 专属全测 */
    private static final boolean SPECIFIC_ONLY = false;

    @Autowired
    private CascadeIntentClassifier cascadeClassifier;

    @Autowired
    private CaseIntentDefinitionsLoader intentLoader;

    // ====================================================================
    // 主测试
    // ====================================================================

    @Test
    @DisplayName("遍历意图样本，统计级联命中率与错分清单")
    void testAllIntentsByCascade() {
        List<IntentDefinition> intents = intentLoader.getIntentsForCase(CASE_ID);
        if (SPECIFIC_ONLY) {
            intents = intents.stream()
                .filter(IntentDefinition::isSpecific)
                .collect(Collectors.toList());
        }

        if (intents.isEmpty()) {
            log.warn("案例 [{}] 无可用意图，跳过测试", CASE_ID);
            return;
        }

        log.info("");
        log.info("========== 开始级联回归测试：案例={}, 意图数={}, SPECIFIC_ONLY={} ==========",
            CASE_ID, intents.size(), SPECIFIC_ONLY);

        List<CaseResult> results = new ArrayList<>();

        for (IntentDefinition def : intents) {
            String expectedCode = def.getIntentCode();
            if (expectedCode == null || expectedCode.isBlank()) continue;

            // ① examples（剔除负例）
            if (def.getExamples() != null) {
                for (String ex : def.getExamples()) {
                    if (ex == null || ex.isBlank()) continue;
                    if (ex.contains("【负例")) continue;
                    results.add(runOne(ex, expectedCode, def, "example"));
                }
            }

            // ② standard_script
            String script = def.getStandardScript();
            if (script != null && !script.isBlank()) {
                results.add(runOne(script, expectedCode, def, "standard_script"));
            }
        }

        printReport(results);
    }

    // ====================================================================
    // 单样本执行
    // ====================================================================

    private CaseResult runOne(String text, String expectedCode,
                              IntentDefinition def, String source) {
        ClassificationContext ctx = ClassificationContext.builder()
            .caseId(CASE_ID)
            .build();

        IntentCommand cmd;
        try {
            cmd = cascadeClassifier.classify(text, ctx);
        } catch (Exception e) {
            log.error("分类异常: expected={}, text='{}'", expectedCode, text, e);
            return new CaseResult(expectedCode, def.getNameCn(), source, text,
                expectedCode, null, null, null,
                false, false, "EXCEPTION: " + e.getMessage());
        }

        if (cmd == null) {
            return new CaseResult(expectedCode, def.getNameCn(), source, text,
                expectedCode, null, null, null,
                false, false, "NULL_CMD");
        }

        String actualCode = cmd.getIntentCode();
        String mode = cmd.getClassifyMode();
        Double conf = cmd.getConfidence();

        // 元语用短路：单独标记，不计入"命中错分"
        if (actualCode != null && actualCode.startsWith("META_")) {
            return new CaseResult(expectedCode, def.getNameCn(), source, text,
                expectedCode, actualCode, mode, conf,
                true, false, "META_SHORT_CIRCUIT");
        }

        boolean matched = cmd.isMatched();
        boolean correct = matched && expectedCode.equals(actualCode);

        return new CaseResult(expectedCode, def.getNameCn(), source, text,
            expectedCode, actualCode, mode, conf,
            matched, correct, null);
    }

    // ====================================================================
    // 报告输出
    // ====================================================================

    private void printReport(List<CaseResult> results) {
        if (results.isEmpty()) {
            log.warn("无测试样本");
            return;
        }

        long total   = results.size();
        long matched = results.stream().filter(CaseResult::matched).count();
        long correct = results.stream().filter(CaseResult::correct).count();
        long metaHit = results.stream()
            .filter(r -> "META_SHORT_CIRCUIT".equals(r.note())).count();

        log.info("");
        log.info("==================================================");
        log.info("        级联分类器回归报告 (案例: {})", CASE_ID);
        log.info("==================================================");
        log.info("样本总数:     {}", total);
        log.info("级联命中:     {} ({})", matched, pct(matched, total));
        log.info("分类正确:     {} ({})", correct, pct(correct, total));
        log.info("需走 LLM:     {} ({})", total - matched, pct(total - matched, total));
        log.info("元语用短路:   {} ({})", metaHit, pct(metaHit, total));

        // ---- 按层命中分布 ----
        Map<String, Long> byMode = results.stream()
            .filter(CaseResult::matched)
            .collect(Collectors.groupingBy(
                r -> r.actualMode() != null ? r.actualMode() : "UNKNOWN",
                LinkedHashMap::new, Collectors.counting()));
        log.info("");
        log.info("---------- 按层命中分布 ----------");
        if (byMode.isEmpty()) {
            log.info("  (无)");
        } else {
            byMode.forEach((m, c) ->
                log.info("  {} {} ({})", padRight(m, 16), c, pct(c, total)));
        }

        // ---- 按样本来源 ----
        Map<String, long[]> bySource = new LinkedHashMap<>();
        for (CaseResult r : results) {
            long[] arr = bySource.computeIfAbsent(r.source(), k -> new long[3]);
            arr[0]++;                      // 总数
            if (r.matched()) arr[1]++;     // 命中
            if (r.correct()) arr[2]++;     // 正确
        }
        log.info("");
        log.info("---------- 按样本来源统计 ----------");
        bySource.forEach((src, arr) -> log.info(
            "  {} 总={} 命中={} ({}) 正确={} ({})",
            padRight(src, 16), arr[0], arr[1], pct(arr[1], arr[0]),
            arr[2], pct(arr[2], arr[0])));

        // ---- 未命中清单 ----
        List<CaseResult> notMatched = results.stream()
            .filter(r -> !r.matched()).collect(Collectors.toList());
        log.info("");
        log.info("---------- 级联未命中（会走 LLM 兜底）共 {} 条 ----------", notMatched.size());
        for (CaseResult r : notMatched) {
            log.warn("  [{}] {} | '{}'", r.expectedCode(), r.source(), r.text());
        }

        // ---- 命中但错分清单 ----
        List<CaseResult> wrong = results.stream()
            .filter(r -> r.matched() && !r.correct())
            .collect(Collectors.toList());
        log.info("");
        log.info("---------- 命中但错分 共 {} 条 ----------", wrong.size());
        for (CaseResult r : wrong) {
            String tag = "META_SHORT_CIRCUIT".equals(r.note()) ? "[元语用短路] " : "";
            log.warn("  {}{} {} | '{}' → 实得 {} (mode={})",
                tag, r.expectedCode(), r.source(), r.text(),
                r.actualCode(), r.actualMode());
        }

        // ---- 按意图覆盖率（命中率升序）----
        Map<String, List<CaseResult>> byIntent = results.stream()
            .collect(Collectors.groupingBy(CaseResult::expectedCode,
                LinkedHashMap::new, Collectors.toList()));
        log.info("");
        log.info("---------- 按意图覆盖率（命中率升序，⚠️=未全命中或存在错分）----------");
        byIntent.entrySet().stream()
            .sorted(Comparator.comparingDouble(e -> {
                long hit = e.getValue().stream().filter(CaseResult::matched).count();
                return (double) hit / e.getValue().size();
            }))
            .forEach(e -> {
                List<CaseResult> list = e.getValue();
                long hit = list.stream().filter(CaseResult::matched).count();
                long ok  = list.stream().filter(CaseResult::correct).count();
                String flag = (hit < list.size() || ok < list.size()) ? "⚠️" : "✅";
                log.info("  {} {} {} 样本={} 命中={} 正确={}",
                    flag, padRight(e.getKey(), 24),
                    padRight(list.get(0).nameCn(), 20),
                    list.size(), hit, ok);
            });

        log.info("");
        log.info("==================================================");
    }

    private String pct(long part, long total) {
        if (total == 0) return "0.0%";
        return String.format("%5.1f%%", 100.0 * part / total);
    }

    /** 简单右填充，避免用 SLF4J 的 {} 宽度语法（它不支持） */
    private String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    // ====================================================================
    // 结果 DTO
    // ====================================================================

    public record CaseResult(
        String expectedCode,
        String nameCn,
        String source,
        String text,
        String expected,
        String actualCode,
        String actualMode,
        Double confidence,
        boolean matched,
        boolean correct,
        String note
    ) {}
}

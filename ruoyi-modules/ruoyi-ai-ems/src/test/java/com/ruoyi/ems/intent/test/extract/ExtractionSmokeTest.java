package com.ruoyi.ems.intent.test.extract;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.test.CascadeTestConfig;
import com.ruoyi.ems.intent.utils.ParameterExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;

/**
 * 参数抽取冒烟测试。
 *
 * <p>对每个已加载意图，用 examples + standard_script 作为样本输入，
 * 验证 ParameterExtractor.extract() 是否能产出非空参数。</p>
 *
 * <p>输出分类：</p>
 * <ol>
 *   <li>[1] 有 extraction 定义且抽取成功</li>
 *   <li>[2] 有 extraction 定义但抽取失败</li>
 *   <li>[2.5] 无 extraction 但有 trg.slots 且抽取成功（路 B 回退）</li>
 *   <li>[2.6] 无 extraction 但有 trg.slots 但抽取失败</li>
 *   <li>[3] 无 extraction 且无 trg.slots（待补）</li>
 *   <li>[4] 无样本可测</li>
 * </ol>
 */
@SpringBootTest
@ContextConfiguration(classes = {
    CascadeTestConfig.class,
    ParameterExtractorTestConfig.class
})
@TestPropertySource(properties = {
    "acls.embedding.enabled=false",
    "acls.embedding.provider=ollama",
    "acls.embedding.model=bge-m3",
    "acls.embedding.base-url=http://localhost:11434",
    "acls.embedding.dim=1024",
    "acls.embedding.read-timeout-ms=10000"
})
public class ExtractionSmokeTest {

    @Autowired
    private CaseIntentDefinitionsLoader defLoader;

    @Autowired
    private ParameterExtractor parameterExtractor;

    /**
     * 用 mock 顶替真实的 EmsContextManager，避免拉起 Redis / MySQL 等重依赖。
     * extract() 里对 caseContext == null 有分支处理，mock 掉不影响抽取逻辑。
     */
    @MockitoBean
    private EmsContextManager contextManager;

    private final List<String> testSessions = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String sid : testSessions) {
            try { contextManager.clearContext(sid); } catch (Exception ignored) {}
        }
        testSessions.clear();
    }

    @Test
    void smokeExtractAllIntents() {
        Collection<IntentDefinition> all = defLoader.getAllIntents().values();

        List<String> hasExtractionOk = new ArrayList<>();
        List<String> hasExtractionFail = new ArrayList<>();
        List<String> hasSlotsOk = new ArrayList<>();
        List<String> hasSlotsFail = new ArrayList<>();
        List<String> noExtraction = new ArrayList<>();
        List<String> emptySamples = new ArrayList<>();

        for (IntentDefinition def : all) {
            String code = def.getIntentCode();
            if (code == null) continue;

            String sessionId = "ext-test-" + code + "-" + UUID.randomUUID();
            contextManager.getOrCreate(sessionId);
            testSessions.add(sessionId);

            List<String> samples = collectSamples(def);
            if (samples.isEmpty()) {
                emptySamples.add(code);
                continue;
            }

            // 先跑一次 probe，提前暴露 isValid() 字段没填对的问题
            IntentCommand probe = new IntentCommand();
            probe.setRawText(samples.get(0));
            probe.setIntentCode(code);
            probe.setMatchedDefinition(def);
            probe.setValid(true);
            probe.setConfidence(0.95);
            if (!probe.isValid()) {
                System.err.println("[INVALID] intent=" + code
                    + " isValid=false，请检查 IntentCommand 字段");
                continue;
            }

            Map<String, Object> lastResult = Collections.emptyMap();
            for (String text : samples) {
                IntentCommand cmd = new IntentCommand();
                cmd.setRawText(text);
                cmd.setIntentCode(code);
                cmd.setMatchedDefinition(def);
                cmd.setValid(true);
                cmd.setConfidence(0.95);
                cmd.setClassifySource("test");
                cmd.setClassifyMode("TEST");

                try {
                    Map<String, Object> result = parameterExtractor.extract(cmd, sessionId);
                    if (result != null && !result.isEmpty()) {
                        lastResult = result;
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[EXCEPTION] intent=" + code
                        + ", text=" + text + ", err=" + e.getMessage());
                }
            }

            boolean hasExt = def.hasExtraction();
            boolean hasSlots = def.getSlots() != null && !def.getSlots().isEmpty();

            if (hasExt) {
                if (!lastResult.isEmpty()) {
                    hasExtractionOk.add(code + " -> " + lastResult);
                } else {
                    hasExtractionFail.add(code);
                }
            } else if (hasSlots) {
                if (!lastResult.isEmpty()) {
                    hasSlotsOk.add(code + " -> " + lastResult);
                } else {
                    hasSlotsFail.add(code);
                }
            } else {
                noExtraction.add(code);
            }
        }

        // ========== 输出 ==========
        System.out.println("\n=================== Extraction Smoke Test ===================");
        System.out.println("总意图数: " + all.size());

        System.out.println("\n--- [1] 有 extraction 定义且抽取成功: "
            + hasExtractionOk.size() + " 个 ---");
        hasExtractionOk.forEach(s -> System.out.println("  ✅ " + s));

        System.out.println("\n--- [2] 有 extraction 定义但抽取失败: "
            + hasExtractionFail.size() + " 个 ---");
        hasExtractionFail.forEach(s -> System.out.println("  ❌ " + s));

        System.out.println("\n--- [2.5] 无 extraction 但有 trg.slots 且抽取成功: "
            + hasSlotsOk.size() + " 个 ---");
        hasSlotsOk.forEach(s -> System.out.println("  ✅ " + s));

        System.out.println("\n--- [2.6] 无 extraction 但有 trg.slots 但抽取失败: "
            + hasSlotsFail.size() + " 个 ---");
        hasSlotsFail.forEach(s -> System.out.println("  ⚠️ " + s));

        System.out.println("\n--- [3] 无 extraction 且无 trg.slots（待补）: "
            + noExtraction.size() + " 个 ---");
        noExtraction.forEach(s -> System.out.println("  ⚪ " + s));

        System.out.println("\n--- [4] 无样本可测（examples / standard_script 均为空或仅负例）: "
            + emptySamples.size() + " 个 ---");
        emptySamples.forEach(s -> System.out.println("  ⚠️ " + s));

        System.out.println("\n=============================================================\n");
    }

    private List<String> collectSamples(IntentDefinition def) {
        LinkedHashSet<String> samples = new LinkedHashSet<>();

        if (def.getStandardScript() != null && !def.getStandardScript().isBlank()) {
            samples.add(def.getStandardScript().trim());
        }

        if (def.getExamples() != null) {
            for (String ex : def.getExamples()) {
                if (ex == null || ex.isBlank()) continue;
                String trimmed = ex.trim();
                if (trimmed.startsWith("【负例】")) continue;
                samples.add(trimmed);
            }
        }

        return new ArrayList<>(samples);
    }
}

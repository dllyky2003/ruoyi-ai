package com.ruoyi.ems.intent.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.config.loader.IntentDictLoader;
import com.ruoyi.ems.intent.dict.IntentNormalizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntentNormalizerBatchTest {

    private static final String DICT_PATH = "classpath:/config/acls/GLOBAL/inverted_intents.json";
    private static final String CASES_PATH = "classpath:/config/acls/GLOBAL/normalize_test_cases.json";

    private static IntentNormalizer normalizer;
    private static JsonNode cases;

    @BeforeAll
    static void setup() throws Exception {
        ObjectMapper om = new ObjectMapper();
        DefaultResourceLoader rl = new DefaultResourceLoader();

        IntentDictLoader loader = new IntentDictLoader(rl, om);
        loader.setDictPath(DICT_PATH);
        loader.load();
        assertFalse(loader.getAllSurfaces().isEmpty(), "词典加载失败");
        normalizer = new IntentNormalizer(loader);

        try (InputStream is = rl.getResource(CASES_PATH).getInputStream()) {
            cases = om.readTree(is).get("categories");
        }
    }

    @Test
    @DisplayName("批量测试：所有场景")
    void testAllCategories() {
        List<String> failures = new ArrayList<>();

        cases.fields().forEachRemaining(catEntry -> {
            String category = catEntry.getKey();
            JsonNode catNode = catEntry.getValue();
            JsonNode caseList = catNode.get("cases");
            if (caseList == null) return;

            for (JsonNode c : caseList) {
                String input = c.path("input").asText("");
                List<String> expectAny = toList(c.get("expect_any"));
                List<String> expectAll = toList(c.get("expect_all"));

                List<IntentNormalizer.NormalizedMatch> result = normalizer.normalize(input);
                List<String> actualCodes = result.stream()
                    .map(IntentNormalizer.NormalizedMatch::intentCode)
                    .distinct()
                    .toList();

                // expect_any 检查：至少命中一个
                if (!expectAny.isEmpty()) {
                    boolean anyHit = expectAny.stream().anyMatch(actualCodes::contains);
                    if (!anyHit) {
                        failures.add(String.format(
                            "[%s] input='%s' 期望命中 %s 中任意一个，实际 %s",
                            category, input, expectAny, actualCodes));
                    }
                } else if (c.has("expect_any")) {
                    // 显式空 expect_any：不应高分命中
                    if (!result.isEmpty() && result.get(0).confidence() >= 0.9) {
                        failures.add(String.format(
                            "[%s] input='%s' 期望无高分命中，实际 top=%s %.3f",
                            category, input, result.get(0).intentCode(), result.get(0).confidence()));
                    }
                }

                // expect_all 检查：全部命中
                if (!expectAll.isEmpty()) {
                    for (String code : expectAll) {
                        if (!actualCodes.contains(code)) {
                            failures.add(String.format(
                                "[%s] input='%s' 期望命中 %s，实际 %s",
                                category, input, code, actualCodes));
                        }
                    }
                }
            }
        });

        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            failures.forEach(f -> System.out.println("  " + f));
        }
        assertTrue(failures.isEmpty(), failures.size() + " 条用例失败，见控制台");
    }

    private static List<String> toList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode n : arr) out.add(n.asText());
        return out;
    }
}

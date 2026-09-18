package com.ruoyi.ems.intent.test;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.config.loader.IntentDictLoader;
import com.ruoyi.ems.intent.dict.IntentNormalizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntentNormalizer 纯单元测试，不依赖 Spring 容器。
 *
 * 前置：
 *   - classpath 里有 inverted_intents.json
 *     或者用 file: 前缀指向实际路径
 *   - pinyin4j 依赖已引入
 */
class IntentNormalizerTest {

    // 用 classpath，把 inverted_intents.json 放到 src/test/resources/acls/
    // 或者改成 file:D:/.../inverted_intents.json 用绝对路径
    private static final String DICT_PATH = "classpath:/config/acls/GLOBAL/inverted_intents.json";

    private static IntentNormalizer normalizer;

    @BeforeAll
    static void setup() throws Exception {
        ObjectMapper om = new ObjectMapper();
        DefaultResourceLoader rl = new DefaultResourceLoader();

        IntentDictLoader loader = new IntentDictLoader(rl, om);
        loader.setDictPath(DICT_PATH);
        loader.load();  // 手动触发 @PostConstruct 逻辑

        assertFalse(loader.getAllSurfaces().isEmpty(), "词典加载失败，检查 DICT_PATH");

        normalizer = new IntentNormalizer(loader);
    }

    // ---------- 精确匹配 ----------

    @Test
    @DisplayName("精确匹配：标准表述")
    void testExactStandard() {
        List<IntentNormalizer.NormalizedMatch> result = normalizer.normalize("检查呼吸");
        assertFalse(result.isEmpty());
        assertTrue(contains(result, "IA_004_check_breathing"),
            "应命中 IA_004_check_breathing，实际: " + result);
    }

    @Test
    @DisplayName("精确匹配：口语")
    void testExactSpoken() {
        List<IntentNormalizer.NormalizedMatch> result = normalizer.normalize("摸脉");
        assertFalse(result.isEmpty());
        assertTrue(contains(result, "IA_003_check_pulse"),
            "应命中 IA_003_check_pulse，实际: " + result);
    }

    @Test
    @DisplayName("精确匹配：短词")
    void testExactShortWord() {
        List<IntentNormalizer.NormalizedMatch> result = normalizer.normalize("脉搏");
        assertTrue(contains(result, "IA_003_check_pulse"));
    }

    // ---------- 拼音匹配 ----------

    @Test
    @DisplayName("拼音匹配：呼息 -> 呼吸")
    void testPinyinHomophone() {
        // "呼息" 不在词典里，但拼音与 "呼吸" 相同（huxi）
        List<IntentNormalizer.NormalizedMatch> result = normalizer.normalize("看呼息");
        assertFalse(result.isEmpty(), "拼音匹配应有结果");
        assertTrue(contains(result, "IA_004_check_breathing") || contains(result, "IA_047_check_breathing_cpr"),
            "应通过拼音命中检查呼吸，实际: " + result);
    }

    // ---------- 模糊匹配 ----------

    @Test
    @DisplayName("编辑距离：摸麦 -> 摸脉")
    void testFuzzy() {
        // "摸麦" 与 "摸脉" 编辑距离 1，长度相同
        List<IntentNormalizer.NormalizedMatch> result = normalizer.normalize("摸麦");
        // 如果 pinyinMap 里有同音，会走拼音；否则走模糊。都算通过
        assertFalse(result.isEmpty(), "模糊/拼音匹配应有结果，实际: " + result);
    }

    // ---------- 分句 + 剥标点 ----------

    @Test
    @DisplayName("带标点的整句")
    void testPunctuation() {
        List<IntentNormalizer.NormalizedMatch> result =
            normalizer.normalize("快点！看他有没有呼吸。");
        assertFalse(result.isEmpty());
        assertTrue(contains(result, "IA_004_check_breathing")
                || contains(result, "IA_047_check_breathing_cpr"),
            "应命中检查呼吸相关，实际: " + result);
    }

    @Test
    @DisplayName("全角标点")
    void testFullWidthPunct() {
        List<IntentNormalizer.NormalizedMatch> result =
            normalizer.normalize("快看，有没有脉搏？");
        assertTrue(contains(result, "IA_003_check_pulse"),
            "应命中 IA_003_check_pulse，实际: " + result);
    }

    @Test
    @DisplayName("多句混合")
    void testMultiClause() {
        List<IntentNormalizer.NormalizedMatch> result =
            normalizer.normalize("先看反应，再摸脉。");
        assertTrue(contains(result, "IA_003_check_pulse"),
            "应命中 IA_003_check_pulse，实际: " + result);
    }

    // ---------- 边界 ----------

    @Test
    @DisplayName("空输入")
    void testEmpty() {
        assertTrue(normalizer.normalize("").isEmpty());
        assertTrue(normalizer.normalize(null).isEmpty());
        assertTrue(normalizer.normalize("   ").isEmpty());
    }

    @Test
    @DisplayName("无关文本")
    void testIrrelevant() {
        List<IntentNormalizer.NormalizedMatch> result = normalizer.normalize("今天天气不错");
        // 不应有高分命中；允许低分噪声
        if (!result.isEmpty()) {
            assertTrue(result.get(0).confidence() < 0.9,
                "无关文本不应高分命中，实际: " + result);
        }
    }

    @Test
    @DisplayName("结果按 confidence 降序")
    void testSorted() {
        List<IntentNormalizer.NormalizedMatch> result =
            normalizer.normalize("检查呼吸");
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).confidence() >= result.get(i).confidence(),
                "结果未按 confidence 降序: " + result);
        }
    }

    @Test
    @DisplayName("同一 intent+surface 不重复")
    void testDedup() {
        List<IntentNormalizer.NormalizedMatch> result =
            normalizer.normalize("检查呼吸");
        long distinct = result.stream()
            .map(m -> m.intentCode() + "|" + m.surface())
            .distinct()
            .count();
        assertEquals(result.size(), distinct, "存在重复的 intent+surface");
    }

    // ---------- 工具方法 ----------

    private static boolean contains(List<IntentNormalizer.NormalizedMatch> list, String intentCode) {
        return list.stream().anyMatch(m -> intentCode.equals(m.intentCode()));
    }
}

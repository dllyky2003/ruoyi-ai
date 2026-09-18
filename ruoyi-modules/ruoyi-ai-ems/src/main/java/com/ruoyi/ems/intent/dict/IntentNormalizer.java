package com.ruoyi.ems.intent.dict;

import com.ruoyi.ems.config.loader.IntentDictLoader;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class IntentNormalizer {

    private final IntentDictLoader loader;

    private static final int MIN_FUZZY_LEN = 3;
    private static final int MAX_FUZZY_DIST = 1;
    private static final int NGRAM_MIN = 2;
    private static final int NGRAM_MAX = 8;

    private static final double PINYIN_WEIGHT = 0.90;
    private static final double FUZZY_WEIGHT_STEP = 0.15;

    public IntentNormalizer(IntentDictLoader loader) {
        this.loader = loader;
    }

    public record NormalizedMatch(
        String surface,
        String intentCode,
        String intentName,
        String normalized,
        String type,
        double confidence,
        String matchMode      // "exact" | "pinyin" | "fuzzy"
    ) {}

    public List<NormalizedMatch> normalize(String asrText) {
        if (asrText == null || asrText.isBlank()) return List.of();

        Map<String, NormalizedMatch> best = new HashMap<>();  // key: intentCode|surface

        String[] clauses = TextNormalizer.splitClauses(asrText);
        for (String clause : clauses) {
            String text = TextNormalizer.normalize(clause);
            if (text.isEmpty()) continue;

            exactMatch(text, best);
            pinyinMatch(text, best);
            fuzzyMatch(text, best);
        }

        List<NormalizedMatch> result = new ArrayList<>(best.values());
        result.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        return result;
    }

    /** 精确匹配：从长到短切 n-gram，命中即记录 */
    private void exactMatch(String text, Map<String, NormalizedMatch> out) {
        int maxN = Math.min(NGRAM_MAX, text.length());
        for (int n = maxN; n >= NGRAM_MIN; n--) {
            for (int i = 0; i + n <= text.length(); i++) {
                String sub = text.substring(i, i + n);
                List<InvertedHit> hits = loader.getExactMap().get(sub);
                if (hits == null) continue;
                for (InvertedHit h : hits) {
                    putBest(out, new NormalizedMatch(
                        sub, h.intentCode(), h.intentName(), h.normalized(),
                        h.type(), h.confidence(), "exact"
                    ));
                }
            }
        }
    }

    /** 拼音匹配：每个中文 n-gram 转拼音，查 pinyinMap */
    private void pinyinMatch(String text, Map<String, NormalizedMatch> out) {
        int maxN = Math.min(NGRAM_MAX, text.length());
        for (int n = maxN; n >= NGRAM_MIN; n--) {
            for (int i = 0; i + n <= text.length(); i++) {
                String sub = text.substring(i, i + n);
                String py = PinyinUtil.toPinyin(sub);
                if (py.isEmpty()) continue;

                Set<String> surfaces = loader.getPinyinMap().get(py);
                if (surfaces == null) continue;

                for (String surface : surfaces) {
                    if (surface.equals(sub)) continue;  // 精确已处理
                    List<InvertedHit> hits = loader.getExactMap().get(surface);
                    if (hits == null) continue;
                    for (InvertedHit h : hits) {
                        putBest(out, new NormalizedMatch(
                            surface, h.intentCode(), h.intentName(), h.normalized(),
                            h.type(), h.confidence() * PINYIN_WEIGHT, "pinyin"
                        ));
                    }
                }
            }
        }
    }

    /** 编辑距离模糊匹配：只在长度接近的候选里比 */
    private void fuzzyMatch(String text, Map<String, NormalizedMatch> out) {
        int maxN = Math.min(NGRAM_MAX, text.length());
        for (int n = maxN; n >= MIN_FUZZY_LEN; n--) {
            for (int i = 0; i + n <= text.length(); i++) {
                String sub = text.substring(i, i + n);

                for (int sl = n - MAX_FUZZY_DIST; sl <= n + MAX_FUZZY_DIST; sl++) {
                    List<String> bucket = loader.getSurfacesByLen().get(sl);
                    if (bucket == null) continue;

                    for (String surface : bucket) {
                        if (surface.equals(sub)) continue;  // 精确已处理
                        int dist = PinyinUtil.editDistance(sub, surface);
                        if (dist == 0 || dist > MAX_FUZZY_DIST) continue;

                        List<InvertedHit> hits = loader.getExactMap().get(surface);
                        if (hits == null) continue;
                        double weight = 1.0 - FUZZY_WEIGHT_STEP * dist;
                        for (InvertedHit h : hits) {
                            putBest(out, new NormalizedMatch(
                                surface, h.intentCode(), h.intentName(), h.normalized(),
                                h.type(), h.confidence() * weight, "fuzzy"
                            ));
                        }
                    }
                }
            }
        }
    }

    private void putBest(Map<String, NormalizedMatch> out, NormalizedMatch m) {
        String key = m.intentCode() + "|" + m.surface();
        NormalizedMatch exist = out.get(key);
        if (exist == null || m.confidence() > exist.confidence()) {
            out.put(key, m);
        }
    }
}

package com.ruoyi.ems.intent.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 急救场景分句器（规则版）。
 *
 * <p>处理流程：
 * <ol>
 *   <li>归一化：全角转半角、去空白</li>
 *   <li>标点切分</li>
 *   <li>连接词切分（然后/接着/再/先）</li>
 *   <li>过短片段合并</li>
 *   <li>可疑检测：切出单句但过长，或存在超长句</li>
 * </ol>
 *
 * <p>不调用 LLM。可疑结果由上层决定是否走 LLM 兜底。
 */
@Slf4j
@Component
public class SentenceSplitter {

    /** 句末标点 */
    private static final Pattern PUNCTUATION = Pattern.compile("[，。！？；、,.!?;]");

    /** 连接词，在词前切分 */
    private static final Pattern CONNECTOR = Pattern.compile(
        "(?=然后|接着|之后|随后|最后|另外|先|再(?!确|次|见|三|说)|并且|同时)"
    );

    /** 最短有效句长（字符数） */
    private static final int MIN_LENGTH = 3;

    /** 单句最长阈值，超过视为"可能没切干净" */
    private static final int MAX_SENTENCE_LENGTH = 25;

    /** 整段长度阈值，单句超过它视为可疑 */
    private static final int WHOLE_TEXT_THRESHOLD = 30;

    /**
     * 规则分句。
     */
    public List<String> split(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }

        String text = normalize(rawText);

        // 1. 标点切分
        String[] punctSegments = PUNCTUATION.split(text);

        // 2. 连接词切分
        List<String> candidates = new ArrayList<>();
        for (String seg : punctSegments) {
            seg = seg.trim();
            if (seg.isEmpty()) continue;
            for (String c : CONNECTOR.split(seg)) {
                c = c.trim();
                if (!c.isEmpty()) candidates.add(c);
            }
        }

        // 3. 合并过短片段
        List<String> merged = mergeShort(candidates);

        // 4. 空则整段返回
        if (merged.isEmpty()) {
            merged.add(rawText.trim());
        }

        log.debug("[SentenceSplitter] '{}' -> {}", rawText, merged);
        return merged;
    }

    /**
     * 判断规则分句结果是否可疑。
     *
     * <p>可疑条件（任一）：
     * <ul>
     *   <li>只切出 1 句但原文超过 {@link #WHOLE_TEXT_THRESHOLD}</li>
     *   <li>存在单句超过 {@link #MAX_SENTENCE_LENGTH}</li>
     * </ul>
     */
    public boolean isSuspicious(String rawText, List<String> sentences) {
        if (rawText == null || sentences == null) return false;

        String text = normalize(rawText);

        if (sentences.size() == 1 && text.length() > WHOLE_TEXT_THRESHOLD) {
            return true;
        }
        for (String s : sentences) {
            if (s != null && s.length() > MAX_SENTENCE_LENGTH) {
                return true;
            }
        }
        return false;
    }

    // ==================== 内部方法 ====================

    private List<String> mergeShort(List<String> segments) {
        List<String> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String s : segments) {
            if (s.length() < MIN_LENGTH) {
                buf.append(s);
                continue;
            }
            if (buf.length() > 0) {
                result.add(buf.toString());
                buf.setLength(0);
            }
            result.add(s);
        }
        if (buf.length() > 0) {
            if (!result.isEmpty()) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + buf);
            } else {
                result.add(buf.toString());
            }
        }
        return result;
    }

    private static String normalize(String text) {
        String s = text.trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else if (c == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("\\s+", "");
    }
}

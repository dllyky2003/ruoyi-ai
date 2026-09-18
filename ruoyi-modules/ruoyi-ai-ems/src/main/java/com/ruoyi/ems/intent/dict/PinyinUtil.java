package com.ruoyi.ems.intent.dict;


import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.Map;

public final class PinyinUtil {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    /**
     * 多音字覆盖表。
     * pinyin4j 默认取数组第一个读音，医疗语境下常与 ASR 期望读音不一致，
     * 这里显式指定，保证词典与 ASR 输出用同一套读音。
     *
     * 只收录医疗急救场景中会出错的字，遇到新问题再补。
     */
    private static final Map<Character, String> FORCE_PINYIN = Map.ofEntries(
        // 除颤/房颤/室颤 —— 医学固定读 chan，pinyin4j 常给 zhan
        Map.entry('颤', "chan"),
        // 心率/脉率 —— 医学读 lv，pinyin4j 可能给 shuai
        Map.entry('率', "lv"),
        // 桡动脉 —— 读 rao，pinyin4j 可能给 nao
        Map.entry('桡', "rao"),
        // 颈动脉 —— 读 jing，pinyin4j 可能给 geng
        Map.entry('颈', "jing"),
        // 给气/给氧 —— 读 gei，pinyin4j 常给 ji
        Map.entry('给', "gei"),
        // 重复短语 —— 读 chong，pinyin4j 常给 zhong
        Map.entry('重', "chong"),
        // 呼吸次数 —— 读 shu，pinyin4j 可能给 shuo
        Map.entry('数', "shu"),
        // 说话 —— 读 shuo，pinyin4j 可能给 shui
        Map.entry('说', "shuo"),
        // 鼻咽通气管 —— 读 yan，pinyin4j 可能给 ye
        Map.entry('咽', "yan"),
        // 恶心 —— 读 e，pinyin4j 可能给 wu
        Map.entry('恶', "e"),
        // 检查/查看 —— 读 cha，pinyin4j 可能给 zha
        Map.entry('查', "cha"),
        // 测量 —— 读 liang，pinyin4j 可能给 liang4 之外
        Map.entry('量', "liang"),
        // 大小便 —— 读 bian，pinyin4j 可能给 pian
        Map.entry('便', "bian"),
        // 藏 —— 读 cang，与心包相关时读 zang，这里统一 cang
        Map.entry('藏', "cang")
    );

    private PinyinUtil() {}

    /**
     * 汉字转无声调拼音。非汉字字母数字原样保留，其余丢弃。
     *
     * 多音字优先查 FORCE_PINYIN，保证词典和 ASR 用同一读音。
     */
    public static String toPinyin(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) continue;

            // 1. 优先查多音字覆盖表
            String forced = FORCE_PINYIN.get(c);
            if (forced != null) {
                sb.append(forced);
                continue;
            }

            // 2. 常规拼音
            try {
                String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                if (arr != null && arr.length > 0) {
                    sb.append(arr[0]);
                } else if (Character.isLetterOrDigit(c)) {
                    sb.append(Character.toLowerCase(c));
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                // 忽略
            }
        }
        return sb.toString();
    }

    public static int editDistance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }
}

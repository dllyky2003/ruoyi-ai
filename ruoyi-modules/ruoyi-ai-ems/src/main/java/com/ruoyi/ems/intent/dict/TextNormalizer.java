package com.ruoyi.ems.intent.dict;

import java.util.regex.Pattern;

public final class TextNormalizer {

    private static final Pattern PUNCT = Pattern.compile(
        "[\\p{P}\\p{S}\\s\\u3000\\u00a0]+"
    );
    private static final Pattern CLAUSE_SEP = Pattern.compile(
        "[。！？!?；;，,\\n\\r]+"
    );

    private TextNormalizer() {}

    /** 剥标点、去空白、全角转半角 */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return PUNCT.matcher(toHalfWidth(text)).replaceAll("");
    }

    /** 按标点切句，返回所有子句（未剥标点） */
    public static String[] splitClauses(String text) {
        if (text == null || text.isEmpty()) return new String[0];
        return CLAUSE_SEP.split(text);
    }

    /** 全角转半角 */
    public static String toHalfWidth(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\u3000') {
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

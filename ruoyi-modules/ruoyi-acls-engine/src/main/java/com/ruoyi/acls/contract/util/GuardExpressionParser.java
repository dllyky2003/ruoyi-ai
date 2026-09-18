package com.ruoyi.acls.contract.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GuardExpressionParser {

    private static final Pattern VAR_PATTERN =
        Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)");

    private static final Set<String> KNOWN_PREFIXES = Set.of(
        "phys", "process", "derived", "device",
        "intervention", "clinical", "assert", "report",
        "evaluation", "case", "context"
    );

    public Set<String> extractVariables(String expression) {
        Set<String> vars = new LinkedHashSet<>();
        if (expression == null || expression.isBlank()) return vars;

        Matcher m = VAR_PATTERN.matcher(expression);
        while (m.find()) {
            String full = m.group(1);
            int endIdx = m.end();
            if (endIdx < expression.length()) {
                char next = skipWhitespace(expression, endIdx);
                if (next == '(') continue;
            }
            String prefix = full.contains(".")
                ? full.substring(0, full.indexOf('.')) : full;
            if (!KNOWN_PREFIXES.contains(prefix)) continue;
            if (full.startsWith("context.")) continue;
            vars.add(full);
        }
        return vars;
    }

    /**
     * 从赋值语句中提取"写入目标"变量。
     *
     * 支持：
     *   - 单赋值： #a = expr
     *   - 多赋值： #a = 1; #b = 2
     *
     * 不识别：
     *   - Bean 名（如 actionLogTransition）
     *   - 方法调用
     */
    public Set<String> extractWriteTargets(String expression) {
        Set<String> result = new LinkedHashSet<>();
        if (expression == null || expression.isBlank()) return result;

        String[] parts = expression.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            int eqIdx = findAssignmentOperator(trimmed);
            if (eqIdx <= 0) continue;
            String left = trimmed.substring(0, eqIdx).trim();
            Matcher m = Pattern.compile("^#([a-zA-Z_][a-zA-Z0-9_.]*)$").matcher(left);
            if (m.matches()) result.add(m.group(1));
        }
        return result;
    }

    /**
     * 提取"读取"的变量：赋值语句的右侧；非赋值表达式的全部。
     */
    public Set<String> extractReadVariables(String expression) {
        if (expression == null || expression.isBlank()) return new LinkedHashSet<>();

        if (isAssignment(expression)) {
            Set<String> result = new LinkedHashSet<>();
            String[] parts = expression.split(";");
            for (String part : parts) {
                int eqIdx = findAssignmentOperator(part);
                if (eqIdx <= 0) {
                    result.addAll(extractVariables(part));
                } else {
                    String right = part.substring(eqIdx + 1).trim();
                    result.addAll(extractVariables(right));
                }
            }
            return result;
        } else {
            return extractVariables(expression);
        }
    }

    public boolean isAssignment(String expression) {
        return findAssignmentOperator(expression) > 0;
    }

    private int findAssignmentOperator(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '=') continue;
            if (i > 0) {
                char prev = s.charAt(i - 1);
                if (prev == '!' || prev == '<' || prev == '>' || prev == '=') continue;
            }
            if (i + 1 < s.length() && s.charAt(i + 1) == '=') continue;
            return i;
        }
        return -1;
    }

    private char skipWhitespace(String s, int idx) {
        while (idx < s.length() && Character.isWhitespace(s.charAt(idx))) idx++;
        return idx < s.length() ? s.charAt(idx) : '\0';
    }
}

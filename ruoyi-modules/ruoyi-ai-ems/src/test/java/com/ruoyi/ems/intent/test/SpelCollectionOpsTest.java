package com.ruoyi.ems.intent.test;

import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.*;

public class SpelCollectionOpsTest {

    public static void main(String[] args) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setPropertyAccessors(List.of(new SimpleMapAccessor(), new ReflectivePropertyAccessor()));

        // ---- 准备测试数据 ----
        List<Map<String, Object>> log = new ArrayList<>();
        log.add(newMap("code", "ECG_12LEAD", "time", 1000L, "actor", "A"));
        log.add(newMap("code", "DEFIB",      "time", 2000L, "actor", "B"));
        log.add(newMap("code", "ECG_12LEAD", "time", 3000L, "actor", "C"));
        log.add(newMap("code", "DRUG_PUSH",  "actor", "D"));  // 缺 time 字段

        Map<String, Object> caseVar = new HashMap<>();
        caseVar.put("eventLog", log);
        ctx.setVariable("case", caseVar);

        runCase(parser, ctx, "Case 1: 单个匹配取 [0]",
            "#case.eventLog.?[code == 'DEFIB'].![time][0]");

        runCase(parser, ctx, "Case 2: 多个匹配取 [0]",
            "#case.eventLog.?[code == 'ECG_12LEAD'].![time][0]");

        runCase(parser, ctx, "Case 3: 无匹配取 [0]",
            "#case.eventLog.?[code == 'ROSC'].![time][0]");

        runCase(parser, ctx, "Case 4: 元素缺字段",
            "#case.eventLog.?[code == 'DRUG_PUSH'].![time][0]");

        // Case 5: 空数组
        Map<String, Object> emptyCase = new HashMap<>();
        emptyCase.put("eventLog", new ArrayList<>());
        ctx.setVariable("case", emptyCase);
        runCase(parser, ctx, "Case 5: 空数组",
            "#case.eventLog.?[code == 'ECG_12LEAD'].![time][0]");

        // Case 6: null 数组
        Map<String, Object> nullCase = new HashMap<>();
        nullCase.put("eventLog", null);
        ctx.setVariable("case", nullCase);
        runCase(parser, ctx, "Case 6: null 数组",
            "#case.eventLog.?[code == 'ECG_12LEAD'].![time][0]");

        // Case 7: 只投影不索引
        ctx.setVariable("case", caseVar);
        runCase(parser, ctx, "Case 7: 只投影不索引",
            "#case.eventLog.?[code == 'ECG_12LEAD'].![time]");

        // Case 8: isEmpty 保护
        runCase(parser, ctx, "Case 8: isEmpty 保护",
            "#case.eventLog.?[code == 'ECG_12LEAD'].isEmpty() ? null : #case.eventLog.?[code == 'ECG_12LEAD'][0].time");

        // Case 9: 空数组 + isEmpty 保护
        ctx.setVariable("case", emptyCase);
        runCase(parser, ctx, "Case 9: 空数组 + isEmpty 保护",
            "#case.eventLog.?[code == 'ECG_12LEAD'].isEmpty() ? null : #case.eventLog.?[code == 'ECG_12LEAD'][0].time");

        // Case 10: null 数组 + isEmpty 保护
        ctx.setVariable("case", nullCase);
        runCase(parser, ctx, "Case 10: null 数组 + isEmpty 保护",
            "#case.eventLog == null || #case.eventLog.?[code == 'ECG_12LEAD'].isEmpty() ? null : #case.eventLog.?[code == 'ECG_12LEAD'][0].time");
    }

    private static void runCase(ExpressionParser parser, StandardEvaluationContext ctx,
                                String label, String expr) {
        try {
            Object r = parser.parseExpression(expr).getValue(ctx);
            String type = r == null ? "null" : r.getClass().getSimpleName();
            System.out.println(label + "  →  " + r + "  (" + type + ")");
        } catch (Exception e) {
            System.out.println(label + "  →  异常: " + e.getClass().getSimpleName()
                + " - " + shortMessage(e.getMessage()));
        }
    }

    private static String shortMessage(String msg) {
        if (msg == null) return "";
        int idx = msg.indexOf('\n');
        return idx > 0 ? msg.substring(0, idx) : msg;
    }

    private static Map<String, Object> newMap(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /**
     * 内联 MapAccessor：Map 的属性访问走 map.get(name)
     * 用匿名实现类的等价写法，避免依赖项目中的 MapAccessor。
     */
    static class SimpleMapAccessor implements PropertyAccessor {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class[]{Map.class};
        }

        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            return target instanceof Map;
        }

        @Override
        public TypedValue read(EvaluationContext context, Object target, String name) {
            return new TypedValue(((Map<?, ?>) target).get(name));
        }

        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) {
            return false;
        }

        @Override
        public void write(EvaluationContext context, Object target, String name, Object value) {
            throw new UnsupportedOperationException("read-only");
        }
    }
}

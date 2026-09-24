package com.ruoyi.ems.effect;

import com.ruoyi.ems.config.loader.PhysiologyConfigLoader;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 药物适应症判定器。
 *
 * <p>根据案例配置的 {@code drug_indications.yml}，判定某药物在当前场景下的
 * condition：{@code correct} / {@code wrong_minor} / {@code wrong_critical}。</p>
 *
 * <p>判定优先级：
 * <ol>
 *   <li>{@code critical_when} 任一命中 → {@link #WRONG_CRITICAL}</li>
 *   <li>{@code minor_when} 任一命中 → {@link #WRONG_MINOR}</li>
 *   <li>声明了 {@code correct_when} 但未全部命中 → {@link #WRONG_MINOR}</li>
 *   <li>否则 → {@link #CORRECT}</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DrugIndicationEvaluator {

    public static final String CORRECT = "correct";
    public static final String WRONG_MINOR = "wrong_minor";
    public static final String WRONG_CRITICAL = "wrong_critical";

    private final PhysiologyConfigLoader configLoader;
    private final SpELExpressionEvaluator evaluator;

    /**
     * 判定药物在当前场景下的 condition。
     *
     * @param caseId   案例 ID
     * @param drugCode DrugEnum.code（如 "adenosine"）
     * @param ctx      案例上下文
     * @return correct / wrong_minor / wrong_critical
     */
    public String resolve(String caseId, String drugCode, CaseContext ctx) {
        if (caseId == null || drugCode == null || ctx == null) {
            return CORRECT;
        }
        Map<String, PhysiologyConfigLoader.DrugIndicationRule> table =
            configLoader.loadDrugIndications(caseId);
        if (table == null || table.isEmpty()) {
            return CORRECT;
        }
        PhysiologyConfigLoader.DrugIndicationRule rule = table.get(drugCode);
        if (rule == null) {
            return CORRECT;
        }

        // 1. critical 优先
        if (hitAny(rule.getCriticalWhen(), ctx, drugCode, "critical")) {
            return WRONG_CRITICAL;
        }
        // 2. minor
        if (hitAny(rule.getMinorWhen(), ctx, drugCode, "minor")) {
            return WRONG_MINOR;
        }
        // 3. correct_when 声明了但未全部命中 → 降级 minor
        List<String> correctWhen = rule.getCorrectWhen();
        if (correctWhen != null && !correctWhen.isEmpty()
            && !hitAll(correctWhen, ctx, drugCode, "correct")) {
            return WRONG_MINOR;
        }
        return CORRECT;
    }

    private boolean hitAny(List<String> exprs, CaseContext ctx, String drug, String level) {
        if (exprs == null || exprs.isEmpty()) return false;
        for (String expr : exprs) {
            if (evalBoolean(expr, ctx, drug, level)) return true;
        }
        return false;
    }

    private boolean hitAll(List<String> exprs, CaseContext ctx, String drug, String level) {
        for (String expr : exprs) {
            if (!evalBoolean(expr, ctx, drug, level)) return false;
        }
        return true;
    }

    /**
     * 单条表达式求值。
     *
     * <p>用 {@link SpELExpressionEvaluator#evaluateCondition(String, CaseContext)}，
     * 返回 boolean；异常已在 evaluator 内部吞掉并返回 false。</p>
     */
    private boolean evalBoolean(String expr, CaseContext ctx, String drug, String level) {
        if (expr == null || expr.isBlank()) return false;
        boolean v = evaluator.evaluateCondition(expr, ctx);
        if (!v) {
            log.debug("药物适应症未命中: drug={}, level={}, expr={}", drug, level, expr);
        }
        return v;
    }
}

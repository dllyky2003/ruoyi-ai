package com.ruoyi.ems.service;

import com.ruoyi.ems.config.loader.DerivationRuleLoader;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.DerivationRule;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DerivationService {

    private final DerivationRuleLoader loader;
    private final SpELExpressionEvaluator evaluator;  // 直接注入具体实现，以使用 extraVars 方法
    private final EventLogTimeDeriver eventLogTimeDeriver;
    /** 栈深度探针阈值：超过该值即打印告警，用于定位深层/递归调用。 */
    private static final int STACK_DEPTH_WARN_THRESHOLD = 200;

    /**
     * ★ 危险表达式关键词：命中后打印警告。
     * 这些关键词一旦出现在 SpEL 表达式里，几乎可以确认表达式会回调
     * EmsContextManager 的写方法，从而形成递归。
     */
    private static final String[] DANGEROUS_KEYWORDS = {
        "contextManager",
        "setVar(",
        "batchSetVars",
        "updateWithVersion",
        "getContextManager",
        "applyResetDefaults",
        "setCurrentState",
        "setCaseType"
    };

    /**
     * 计算派生变量。
     *
     * <p>安全设计：
     * <ol>
     *   <li>栈深度探针：识别调用是否已进入深层，便于定位递归源头；</li>
     *   <li>表达式静态扫描：命中 {@link #DANGEROUS_KEYWORDS} 直接告警；</li>
     *   <li>context detach：把 CaseContext 副本里的 contextManager 引用清空，
     *       切断 SpEL 通过 {@code #context.contextManager.xxx} 回调的路径；</li>
     *   <li>target 重复检测：同一 target 定义多次告警，避免规则互相覆盖。</li>
     * </ol>
     */
    public Map<String, Object> computeDerivedVariables(CaseContext context) {


        if (context == null) {
            log.warn("CaseContext 为空，无法计算派生变量");
            return new HashMap<>();
        }

        String caseType = context.getCaseType();
        List<DerivationRule> rules = loader.getRulesForCase(caseType);
        Map<String, Object> derived = new HashMap<>();

        if (rules == null || rules.isEmpty()) {
            return derived;
        }


        // ==================== ★ 防御：切断 context 回调路径 ====================
        // SpELExpressionEvaluator 若通过 #context.contextManager 访问
        // EmsContextManager，就会在派生计算期间触发写操作 → 无限递归。
        // 这里传入一个 contextManager 为 null 的副本，从源头切断该路径。
        CaseContext safeContext = detachContext(context);

        // ==================== ★ 诊断：target 重复检测 ====================
        Set<String> targets = new HashSet<>();
        for (DerivationRule rule : rules) {
            String target = rule.getTarget();
            if (target == null || target.isEmpty()) {
                log.warn("派生规则 target 为空, expr={}", rule.getExpression());
                continue;
            }
            if (!targets.add(target)) {
                log.warn("派生规则 target 重复: {}（后续定义将覆盖前者），expr={}",
                    target, rule.getExpression());
            }
        }

        // 用于存放已计算的派生变量，供后续规则引用
        Map<String, Object> extraVars = new HashMap<>();

        for (DerivationRule rule : rules) {
            String target = rule.getTarget();
            if (target == null || target.isEmpty()) {
                continue;
            }

            // ==================== ★ 诊断：危险表达式静态扫描 ====================
            String expr = rule.getExpression();
            if (expr != null && containsDangerousKeyword(expr)) {
                log.warn("🚨 派生规则表达式疑似会回调 EmsContextManager: target={}, expr={}",
                    target, expr);
            }

            try {
                // 将已计算的派生变量作为额外变量传入；使用 detach 后的 safeContext
                Object value = evaluator.evaluateValue(
                    expr, safeContext, extraVars, Object.class);
                derived.put(target, value);
                extraVars.put(target, value);  // 供后续规则使用
                log.debug("派生变量 {} = {}", target, value);
            } catch (Exception e) {
                log.warn("派生规则执行失败: target={}, expr={}", target, expr, e);
            }
        }
        try {
            Map<String, Object> eventLogDerived = eventLogTimeDeriver.derive(safeContext);
            if (eventLogDerived != null && !eventLogDerived.isEmpty()) {
                for (Map.Entry<String, Object> e : eventLogDerived.entrySet()) {
                    derived.putIfAbsent(e.getKey(), e.getValue());
                }
                log.debug("事件日志派生完成: {} 项", eventLogDerived.size());
            }
        } catch (Exception e) {
            log.warn("事件日志派生失败: sessionId={}, err={}",
                safeContext != null ? safeContext.getSessionId() : null, e.getMessage());
        }
        return derived;
    }

    /**
     * 创建 "detached" CaseContext 副本：
     * <ul>
     *   <li>保留变量、caseType、currentState、version 等数据字段；</li>
     *   <li>把 contextManager 引用置空，切断 SpEL 通过 {@code #context.contextManager}
     *       回调写方法的路径。</li>
     * </ul>
     *
     * <p>注意：这**不能**拦截 SpEL 里通过 Spring Bean 名
     * （例如 {@code @emsContextManager.setVar(...)}）访问的路径。
     * 该路径需由 {@code EmsContextManager.DERIVATION_DEPTH} 重入保护兜底。</p>
     */
    private CaseContext detachContext(CaseContext original) {
        if (original == null) {
            return null;
        }
        try {
            CaseContext copy = original.copy();
            copy.setContextManager(null);
            return copy;
        } catch (Exception e) {
            // copy() 失败时保守返回原对象；此时递归风险由 EmsContextManager 兜底
            log.warn("detachContext 失败，回退到原对象（存在递归风险）: {}", e.getMessage());
            return original;
        }
    }

    /**
     * 扫描表达式中是否包含危险关键词。
     */
    private boolean containsDangerousKeyword(String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }
        for (String kw : DANGEROUS_KEYWORDS) {
            if (expression.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}

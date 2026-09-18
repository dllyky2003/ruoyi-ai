package com.ruoyi.ems.spel;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.rule.IRuleEvaluator;
import com.ruoyi.ems.utils.AhaGuidelines;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SpELExpressionEvaluator implements IRuleEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Autowired
    private AhaGuidelines ahaGuidelines;

    @Autowired
    private EmsContextManager emsContextManager;

    @PostConstruct
    public void init() {
        log.info("✅ SpEL 表达式评估器初始化完成（支持动态变量 + 额外变量）");
    }

    // ==================== IRuleEvaluator 接口实现（CaseContext 版本） ====================
    @Override
    public boolean evaluateCondition(String expression, CaseContext context) {


        return evaluateCondition(expression, context, null);
    }

    public boolean evaluateCondition(String expression, CaseContext context, Map<String, Object> extraVars) {
        if (expression == null || expression.isEmpty()) return true;
        try {
            StandardEvaluationContext evalCtx = buildContext(context, extraVars);
            Boolean result = parser.parseExpression(expression).getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Guard 表达式评估失败: {}", expression, e);
            return false;
        }
    }

    @Override
    public void executeAction(String expression, CaseContext context) {

        executeAction(expression, context, null);
    }

    public void executeAction(String expression, CaseContext context, Map<String, Object> extraVars) {

        if (expression == null || expression.isEmpty()) return;
        try {
            StandardEvaluationContext evalCtx = buildContext(context, extraVars);
            parser.parseExpression(expression).getValue(evalCtx);
        } catch (Exception e) {
            log.error("Action 表达式评估失败: {}", expression, e);
        }
    }

    @Override
    public <T> T evaluateValue(String expression, CaseContext context, Class<T> targetType) {
        return evaluateValue(expression, context, null, targetType);
    }

    public <T> T evaluateValue(String expression, CaseContext context, Map<String, Object> extraVars, Class<T> targetType) {


        if (expression == null || expression.isEmpty()) return null;
        try {
            StandardEvaluationContext evalCtx = buildContext(context, extraVars);
            return parser.parseExpression(expression).getValue(evalCtx, targetType);
        } catch (Exception e) {
            log.error("值表达式评估失败: {}", expression, e);
            return null;
        }
    }

    // ==================== IRuleEvaluator 接口实现（Map 版本，已禁用） ====================
    @Override
    public <T> T evaluateValue(String expression, Map<String, Object> variables, Class<T> targetType) {
        throw new UnsupportedOperationException(
            "Map 版本的 evaluateValue 已被禁用。请使用 evaluateValue(expression, caseContext, Class) 传递 CaseContext 对象。"
        );
    }

    @Override
    public Object evaluateValue(String expression, Map<String, Object> variables) {
        throw new UnsupportedOperationException(
            "Map 版本的 evaluateValue 已被禁用。请使用 evaluateValue(expression, caseContext) 传递 CaseContext 对象。"
        );
    }

    // ==================== StandardEvaluationContext 版本（高级用法，保留） ====================
    public <T> T evaluateValue(String expression, StandardEvaluationContext evalCtx, Class<T> targetType) {
        if (expression == null || expression.isEmpty()) return null;
        if (evalCtx == null) {
            log.warn("StandardEvaluationContext 为空");
            return null;
        }
        try {
            return parser.parseExpression(expression).getValue(evalCtx, targetType);
        } catch (Exception e) {
            log.error("SpEL 表达式评估失败: {}", expression, e);
            return null;
        }
    }

    public Object evaluateValue(String expression, StandardEvaluationContext evalCtx) {
        if (expression == null || expression.isEmpty()) return null;
        if (evalCtx == null) {
            log.warn("StandardEvaluationContext 为空");
            return null;
        }
        try {
            return parser.parseExpression(expression).getValue(evalCtx);
        } catch (Exception e) {
            log.error("SpEL 表达式评估失败: {}", expression, e);
            return null;
        }
    }

    // ==================== 构建上下文（核心） ====================
    private StandardEvaluationContext buildContext(CaseContext context, Map<String, Object> extraVars) {
        StandardEvaluationContext evalCtx = createBaseContext();

        if (context == null) {
            log.warn("CaseContext 为空，使用空上下文");
            return evalCtx;
        }

        String sessionId = context.getSessionId();
        if (sessionId == null) {
            log.warn("CaseContext 中的 sessionId 为空");
            return evalCtx;
        }

        evalCtx.setVariable("context", context);
        evalCtx.setVariable("elapsedSeconds", context.getElapsedSeconds());
        evalCtx.setVariable("clockStatus", context.getClockStatus());

        evalCtx.setVariable("phys", new DynamicVariableMap(sessionId, emsContextManager, "phys"));
        evalCtx.setVariable("process", new DynamicVariableMap(sessionId, emsContextManager, "process"));
        evalCtx.setVariable("intervention", new DynamicVariableMap(sessionId, emsContextManager, "intervention"));
        evalCtx.setVariable("device", new DynamicVariableMap(sessionId, emsContextManager, "device"));
        evalCtx.setVariable("clinical", new DynamicVariableMap(sessionId, emsContextManager, "clinical"));
        evalCtx.setVariable("derived", new DynamicVariableMap(sessionId, emsContextManager, "derived"));
        evalCtx.setVariable("variables", context.getVariables());

        if (extraVars != null) {
            extraVars.forEach(evalCtx::setVariable);
        }

        registerParsers(evalCtx);
        return evalCtx;
    }

    // ==================== 辅助方法 ====================
    private StandardEvaluationContext createBaseContext() {
        StandardEvaluationContext ctx = new StandardEvaluationContext();

        List<PropertyAccessor> accessors = new ArrayList<>();
        accessors.add(new MapAccessor());
        accessors.add(new ReflectivePropertyAccessor());
        ctx.setPropertyAccessors(accessors);

        ctx.setVariable("utils", new SpELUtils());
        ctx.setVariable("aha", ahaGuidelines);
        return ctx;
    }
    /**
     * 评估 Guard 条件，支持传入额外变量（如从意图中提取的 slots）
     *
     * @param expression 条件表达式
     * @param context    CaseContext
     * @param extraVars  额外变量（例如 slots）
     * @return 条件是否满足
     */
    public boolean evaluateGuardWithVars(String expression, CaseContext context, Map<String, Object> extraVars) {
        return evaluateCondition(expression, context, extraVars);
    }
    private void registerParsers(StandardEvaluationContext ctx) {
        // 可扩展
    }
}

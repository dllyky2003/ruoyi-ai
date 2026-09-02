package com.ruoyi.ems.spel;

import com.ruoyi.ems.context.VariableMetaManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.VariableMeta;
import com.ruoyi.ems.rule.RuleEvaluator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SpELExpressionEvaluator implements RuleEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final VariableMetaManager variableMetaManager = new VariableMetaManager();

    @PostConstruct
    public void init() {
        log.info("✅ SpEL 表达式评估器初始化完成");
    }

    @Override
    public boolean evaluateGuard(String expression, CaseContext context) {
        if (expression == null || expression.isEmpty()) {
            return true;
        }
        try {
            StandardEvaluationContext evalCtx = buildContext(context);
            Boolean result = parser.parseExpression(expression).getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Guard 表达式评估失败: {}", expression, e);
            return false;
        }
    }

    @Override
    public void evaluateAction(String expression, CaseContext context) {
        if (expression == null || expression.isEmpty()) return;
        try {
            StandardEvaluationContext evalCtx = buildContext(context);
            parser.parseExpression(expression).getValue(evalCtx);
        } catch (Exception e) {
            log.error("Action 表达式评估失败: {}", expression, e);
        }
    }

    @Override
    public <T> T evaluateValue(String expression, CaseContext context, Class<T> targetType) {
        if (expression == null || expression.isEmpty()) return null;
        try {
            StandardEvaluationContext evalCtx = buildContext(context);
            return parser.parseExpression(expression).getValue(evalCtx, targetType);
        } catch (Exception e) {
            log.error("值表达式评估失败: {}", expression, e);
            return null;
        }
    }

    private StandardEvaluationContext buildContext(CaseContext context) {
        StandardEvaluationContext evalCtx = new StandardEvaluationContext();

        // 注册工具类
        evalCtx.setVariable("utils", new SpELUtils());

        if (context != null) {
            // 注册 context 对象本身
            evalCtx.setVariable("context", context);

            // 从 VariableMetaManager 获取该案例类型的所有变量名
            Map<String, VariableMeta> varsMeta = variableMetaManager.getVariablesForCase(context.getCaseType());
            if (varsMeta != null) {
                for (String varName : varsMeta.keySet()) {
                    Object value = context.getVar(varName, Object.class);
                    // 如果值为 null，也可以设置默认值（但默认值可能从元数据获取）
                    evalCtx.setVariable(varName, value);
                }
            }
        }

        return evalCtx;
    }
}

package com.ruoyi.ems.rule;

import com.ruoyi.ems.domain.CaseContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Component
public class RuleEvaluatorImpl implements IRuleEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public boolean evaluateCondition(String expression, CaseContext context) {
        StandardEvaluationContext evalContext = buildContext(context);
        return parser.parseExpression(expression).getValue(evalContext, Boolean.class);
    }

    @Override
    public void executeAction(String actionExpression, CaseContext context) {
        StandardEvaluationContext evalContext = buildContext(context);
        parser.parseExpression(actionExpression).getValue(evalContext);
    }

    @Override
    public <T> T evaluateValue(String expression, CaseContext context, Class<T> targetType) {
        StandardEvaluationContext evalContext = buildContext(context);
        return parser.parseExpression(expression).getValue(evalContext, targetType);
    }

    private StandardEvaluationContext buildContext(CaseContext context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        if (context.getVariables() != null) {
            context.getVariables().forEach(evalContext::setVariable);
        }
        return evalContext;
    }
}

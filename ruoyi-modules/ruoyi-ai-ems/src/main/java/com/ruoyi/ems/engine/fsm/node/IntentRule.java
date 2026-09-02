package com.ruoyi.ems.engine.fsm.node;

import lombok.Data;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 意图→事件的映射规则
 */
@Data
public class IntentRule {
    // 匹配的意图码（支持 "*" 通配）
    private String intentCode;

    // SpEL 条件表达式（如 "#isUnstable == true"），为空则无条件
    private String condition;

    // 事件模板，支持 ${key} 占位符（如 "S1-3_${isUnstable}"）
    private String eventTemplate;

    // 优先级（数字越小优先级越高）
    private int priority = 0;

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /**
     * 判断当前意图是否匹配此规则
     */
    public boolean matches(String code, Map<String, Object> params) {
        // 1. 通配或精确匹配
        if (!"*".equals(this.intentCode) && !this.intentCode.equals(code)) {
            return false;
        }

        // 2. 无条件则直接匹配
        if (!StringUtils.hasText(condition)) {
            return true;
        }

        // 3. 解析 SpEL
        try {
            Expression exp = SPEL_PARSER.parseExpression(condition);
            return Boolean.TRUE.equals(exp.getValue(params, Boolean.class));
        } catch (Exception e) {
            // 解析失败视为不匹配（也可考虑抛异常，但生产环境建议容错）
            return false;
        }
    }

    /**
     * 根据参数渲染事件名（替换 ${key} 占位符）
     */
    public String buildEvent(Map<String, Object> params) {
        if (!StringUtils.hasText(eventTemplate)) {
            return intentCode;
        }
        String result = eventTemplate;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (isSimpleType(value)) {
                result = result.replace("${" + entry.getKey() + "}", value.toString());
            }
        }
        return result;
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
    }
}

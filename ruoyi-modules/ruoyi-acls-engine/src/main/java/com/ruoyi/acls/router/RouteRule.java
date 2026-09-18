package com.ruoyi.acls.router;

import lombok.Data;

import java.util.Map;

/**
 * 语义路由规则
 * 定义在特定场景下，某个意图应映射到哪个技能
 */
@Data
public class RouteRule {

    /**
     * 规则唯一标识
     */
    private String id;

    /**
     * 适用的案例类型（如 narrow_qrs_stable）
     * 支持通配符 "*"
     */
    private String caseType;

    /**
     * 适用的状态（如 svt_diagnosed）
     * 支持通配符 "*"
     */
    private String state;

    /**
     * 意图编码
     */
    private String intent;

    /**
     * 目标类型：SKILL, COMPLEX, ATOMIC, CHAIN
     */
    private String targetType;

    /**
     * 目标 ID（如 skillId）
     */
    private String targetId;

    /**
     * 标准化参数
     */
    private Map<String, Object> params;

    /**
     * 参数映射规则（从 slots 到技能参数的转换）
     * 例如：{ "dose": "$slots.dose ?: '6mg'" }
     */
    private Map<String, String> paramMapping;

    /**
     * SpEL 条件表达式（动态判断是否匹配）
     * 例如："#context.getVar('adenosineGiven') == true"
     */
    private String condition;

    /**
     * 优先级（数值越大优先级越高）
     */
    private int priority = 0;

    /**
     * 检查当前上下文是否匹配该规则
     */
    public boolean matches(String intent, String caseType, String state) {
        if (!this.intent.equals(intent)) {
            return false;
        }
        if (!"*".equals(this.caseType) && !this.caseType.equals(caseType)) {
            return false;
        }
        // 如果 this.state 为 null，视为通配符，匹配任何状态
        if (this.state != null && !"*".equals(this.state) && !this.state.equals(state)) {
            return false;
        }
        return true;
    }
}

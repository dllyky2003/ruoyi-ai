package com.ruoyi.acls.guard;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 安全护栏规则（对齐 2.1 版 YAML 结构）
 */
@Data
public class GuardRule {

    /* ---------- 基础元数据 ---------- */
    private String id;
    private String group;
    private Integer priority;
    private Boolean enabled;
    private String timing;        // pre_intent | post_intent | during_intent
    private String description;

    /* ---------- 触发 ---------- */
    private List<String> intents;

    /* ---------- 条件 ---------- */
    private Condition condition;

    /* ---------- 严重级别 ---------- */
    private Severity severity;

    /* ---------- 动作 ---------- */
    private Action action;

    /* ---------- 旁路 ---------- */
    private OverrideConfig override;

    /* ---------- 审计 ---------- */
    private AuditConfig audit;

    /* ---------- 引用 ---------- */
    private List<String> references;

    /* ---------- 兼容旧字段（可选） ---------- */
    private String caseType;
    private List<String> states;
    private String message;
    private String suggestion;

    public enum Severity {
        BLOCK, WARN, INFO, LOG
    }

    @Data
    public static class Action {
        private String type;      // block | warn | info | coach
        private String message;
    }

    @Data
    public static class OverrideConfig {
        private Boolean allowed;
        private List<String> roles;
        private Boolean requireReason;
    }

    @Data
    public static class AuditConfig {
        private String level;
        private List<String> fields;
    }

    /**
     * 静态匹配：只看 intent 是否命中
     */
    public boolean matchesIntent(String intentCode) {
        if (Boolean.FALSE.equals(enabled)) {
            return false;
        }
        return intents != null && intents.contains(intentCode);
    }

    /**
     * 动态匹配：intent + 变量条件求值
     */
    public boolean matches(String intentCode, Map<String, Object> variables) {
        if (!matchesIntent(intentCode)) {
            return false;
        }
        if (condition == null) {
            return true;
        }
        return condition.evaluate(variables);
    }

    /**
     * 兼容旧签名（仅意图匹配时使用）
     */
    public boolean matches(String caseType, String currentState, String intentCode) {
        if (Boolean.FALSE.equals(enabled)) {
            return false;
        }
        if (this.caseType != null && !"*".equals(this.caseType) && !this.caseType.equals(caseType)) {
            return false;
        }
        if (states != null && !states.isEmpty() && !states.contains(currentState)) {
            return false;
        }
        return matchesIntent(intentCode);
    }

    /**
     * 获取最终展示给用户的消息
     */
    public String resolveMessage() {
        if (action != null && action.getMessage() != null) {
            return action.getMessage();
        }
        return message;
    }
}

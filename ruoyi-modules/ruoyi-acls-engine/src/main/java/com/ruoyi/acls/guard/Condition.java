package com.ruoyi.acls.guard;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 条件 DSL 节点
 * 支持 all / any / not 组合，以及叶子条件（var / op / value / onMissing）
 */
@Data
public class Condition {

    /** 逻辑与：全部为真 */
    private List<Condition> all;

    /** 逻辑或：任一为真 */
    private List<Condition> any;

    /** 逻辑非 */
    private Condition not;

    /** 叶子条件：变量名 */
    private String var;

    /** 叶子条件：操作符 */
    private String op;

    /** 叶子条件：比较值 */
    private Object value;

    /** 变量缺失时的策略：fail_closed / fail_open / skip */
    private String onMissing;

    public boolean isLeaf() {
        return var != null && op != null;
    }

    /**
     * 条件求值
     */
    public boolean evaluate(Map<String, Object> variables) {
        if (all != null && !all.isEmpty()) {
            for (Condition c : all) {
                if (!c.evaluate(variables)) {
                    return false;
                }
            }
            return true;
        }
        if (any != null && !any.isEmpty()) {
            for (Condition c : any) {
                if (c.evaluate(variables)) {
                    return true;
                }
            }
            return false;
        }
        if (not != null) {
            return !not.evaluate(variables);
        }
        return evaluateLeaf(variables);
    }

    private boolean evaluateLeaf(Map<String, Object> variables) {
        if (!isLeaf()) {
            // 空条件视为真（不阻拦）
            return true;
        }

        boolean missing = (variables == null) || !variables.containsKey(var) || variables.get(var) == null;
        if (missing) {
            String strategy = (onMissing == null || onMissing.isEmpty()) ? "fail_closed" : onMissing;
            switch (strategy.toLowerCase()) {
                case "fail_open":
                case "skip":
                    return false;
                case "fail_closed":
                default:
                    return true;
            }
        }

        Object actual = variables.get(var);
        return compare(actual, op, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean compare(Object actual, String op, Object expected) {
        if (op == null) {
            return false;
        }
        switch (op.toLowerCase()) {
            case "eq":
                return equalsLoose(actual, expected);
            case "ne":
                return !equalsLoose(actual, expected);
            case "is_null":
                return actual == null;
            case "is_not_null":
                return actual != null;
            case "in":
                if (expected instanceof Collection) {
                    return ((Collection<?>) expected).contains(actual);
                }
                return false;
            case "not_in":
                if (expected instanceof Collection) {
                    return !((Collection<?>) expected).contains(actual);
                }
                return false;
            case "gt":
                return compareNumber(actual, expected) > 0;
            case "gte":
                return compareNumber(actual, expected) >= 0;
            case "lt":
                return compareNumber(actual, expected) < 0;
            case "lte":
                return compareNumber(actual, expected) <= 0;
            case "exists":
                return actual != null;
            case "not_exists":
                return actual == null;
            default:
                return false;
        }
    }

    private boolean equalsLoose(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return a.equals(b);
        }
        if (a instanceof Boolean && b instanceof String) {
            return a.equals(Boolean.parseBoolean((String) b));
        }
        if (a instanceof String && b instanceof Boolean) {
            return Boolean.parseBoolean((String) a) == (Boolean) b;
        }
        return a.toString().equals(b.toString());
    }

    private int compareNumber(Object a, Object b) {
        if (!(a instanceof Number) || !(b instanceof Number)) {
            return 0;
        }
        return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
    }
}

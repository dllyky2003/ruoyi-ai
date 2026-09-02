package com.ruoyi.ems.exception;

import com.ruoyi.ems.domain.VariableMeta;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 变量校验异常
 *
 * 当变量值不符合元数据约束时抛出：
 * - 类型不匹配（期望 Integer，实际为 String）
 * - 枚举值不在允许列表中
 * - 数值超出 min/max 范围
 * - 格式不正确（如日期格式）
 *
 * 使用场景：
 * - ContextManager.setVar() 执行值校验时
 * - SlotToVariableMapper 映射槽位时
 * - 任何尝试设置非法变量值的场景
 */
@NoArgsConstructor
public class VariableValidationException extends RuntimeException {

    public VariableValidationException(String message) {
        super(message);
    }

    public VariableValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public VariableValidationException(Throwable cause) {
        super(cause);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 类型不匹配异常
     */
    public static VariableValidationException typeMismatch(String varName, Object value, String expectedType) {
        return new VariableValidationException(
            String.format("变量 '%s' 类型不匹配: 期望 %s, 实际 %s (值: %s)",
                varName, expectedType, value != null ? value.getClass().getSimpleName() : "null", value)
        );
    }

    /**
     * 枚举值无效异常（基于枚举选项列表）
     */
    public static VariableValidationException invalidEnumValue(String varName, Object value, List<VariableMeta.EnumOption> enumOptions) {
        List<String> allowedValues = enumOptions.stream()
            .map(VariableMeta.EnumOption::getValue)
            .collect(Collectors.toList());
        return new VariableValidationException(
            String.format("变量 '%s' 值 '%s' 不在允许列表中: %s",
                varName, value, allowedValues)
        );
    }

    /**
     * 数值超出范围异常
     */
    public static VariableValidationException outOfRange(String varName, Number value, Number min, Number max) {
        return new VariableValidationException(
            String.format("变量 '%s' 值 %s 超出范围 [%s, %s]",
                varName, value, min, max)
        );
    }

    /**
     * 必填变量为空异常
     */
    public static VariableValidationException requiredNull(String varName) {
        return new VariableValidationException(
            String.format("变量 '%s' 为必填项，不能为空", varName)
        );
    }

    /**
     * 格式不匹配异常
     */
    public static VariableValidationException invalidFormat(String varName, String value, String expectedFormat) {
        return new VariableValidationException(
            String.format("变量 '%s' 值 '%s' 格式不匹配，期望格式: %s",
                varName, value, expectedFormat)
        );
    }

    /**
     * 根据变量元数据构建校验异常
     * 适配新的 enumOptions 结构
     */
    public static VariableValidationException fromMeta(VariableMeta meta, Object value) {
        if (meta == null) {
            return new VariableValidationException("未知变量");
        }

        String varName = meta.getName();
        String type = meta.getType();

        if (value == null) {
            return requiredNull(varName);
        }

        if (type == null) {
            return new VariableValidationException(
                String.format("变量 '%s' 未定义类型", varName)
            );
        }

        switch (type.toLowerCase()) {
            case "integer":
                if (!(value instanceof Number)) {
                    return typeMismatch(varName, value, "Integer");
                }
                int intVal = ((Number) value).intValue();
                if (meta.getMin() != null && intVal < meta.getMin()) {
                    return outOfRange(varName, intVal, meta.getMin(), meta.getMax());
                }
                if (meta.getMax() != null && intVal > meta.getMax()) {
                    return outOfRange(varName, intVal, meta.getMin(), meta.getMax());
                }
                break;

            case "string":
                if (!(value instanceof String)) {
                    return typeMismatch(varName, value, "String");
                }
                // 使用新的 enumOptions
                if (meta.getEnumOptions() != null && !meta.getEnumOptions().isEmpty()) {
                    boolean isValid = meta.getEnumOptions().stream()
                        .anyMatch(opt -> opt.getValue().equals(value));
                    if (!isValid) {
                        return invalidEnumValue(varName, value, meta.getEnumOptions());
                    }
                }
                break;

            case "boolean":
                if (!(value instanceof Boolean) &&
                    !"true".equalsIgnoreCase(String.valueOf(value)) &&
                    !"false".equalsIgnoreCase(String.valueOf(value))) {
                    return typeMismatch(varName, value, "Boolean");
                }
                break;

            case "datetime":
                if (!(value instanceof LocalDateTime) &&
                    !(value instanceof Instant) &&
                    !(value instanceof Date)) {
                    return invalidFormat(varName, String.valueOf(value), "LocalDateTime/Instant/Date");
                }
                break;

            default:
                // 未知类型，跳过校验
                break;
        }

        return new VariableValidationException(
            String.format("变量 '%s' 值 '%s' 校验失败", varName, value)
        );
    }
}

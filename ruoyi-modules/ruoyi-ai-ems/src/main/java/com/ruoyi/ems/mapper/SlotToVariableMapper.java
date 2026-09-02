package com.ruoyi.ems.mapper;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.context.VariableMetaManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.VariableMeta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * NLU 槽位到上下文变量的映射器
 *
 * 职责：
 * 1. 将分类器输出的 slots 映射为上下文变量名
 * 2. 执行变量类型转换（String → Integer, Boolean 等）
 * 3. 校验变量写入权限（仅允许 CLASSIFIER 写入 L2 学员陈述变量）
 * 4. 将映射后的变量写入 CaseContext
 *
 * 映射规则来源：
 * - _variables_general.json 中的 slot_mappings（通用）
 * - _variables_specific.json 中的 slot_mappings（专属，覆盖通用）
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Component
@Slf4j
public class SlotToVariableMapper {

    @Autowired
    private VariableMetaManager metaManager;

    @Autowired
    private EmsContextManager contextManager;

    /**
     * 常量：分类器写入者标识
     */
    private static final String WRITER_CLASSIFIER = "CLASSIFIER";

    /**
     * 默认的槽位→变量映射（硬编码兜底）
     * 当 JSON 中未定义映射时使用
     */
    private static final Map<String, String> DEFAULT_MAPPINGS = new LinkedHashMap<>();

    static {
        // S类意图槽位映射
        DEFAULT_MAPPINGS.put("qrs_width", "asserted_qrs_width");
        DEFAULT_MAPPINGS.put("rhythm_regular", "asserted_rhythm_regular");
        DEFAULT_MAPPINGS.put("is_stable", "asserted_stable");
        DEFAULT_MAPPINGS.put("isUnstable", "asserted_stable");  // 反向处理
        DEFAULT_MAPPINGS.put("outcome", "treatment_outcome");
        DEFAULT_MAPPINGS.put("rhythm_type", "treatment_rhythm_type");
        DEFAULT_MAPPINGS.put("action_status", "operation_status");
        DEFAULT_MAPPINGS.put("actionStatus", "operation_status");

        // G类生命体征槽位映射
        DEFAULT_MAPPINGS.put("systolic_bp", "reported_systolic_bp");
        DEFAULT_MAPPINGS.put("systolicBP", "reported_systolic_bp");
        DEFAULT_MAPPINGS.put("diastolic_bp", "reported_diastolic_bp");
        DEFAULT_MAPPINGS.put("diastolicBP", "reported_diastolic_bp");
        DEFAULT_MAPPINGS.put("heart_rate", "reported_heart_rate");
        DEFAULT_MAPPINGS.put("heartRate", "reported_heart_rate");
        DEFAULT_MAPPINGS.put("spo2", "reported_spo2");
        DEFAULT_MAPPINGS.put("consciousness", "reported_consciousness");
        DEFAULT_MAPPINGS.put("gcs", "reported_gcs");

        // 治疗参数槽位映射
        DEFAULT_MAPPINGS.put("dose", "treatment_dose_pending");
        DEFAULT_MAPPINGS.put("energy", "cardioversion_energy_pending");
        DEFAULT_MAPPINGS.put("drug", "treatment_drug_pending");
        DEFAULT_MAPPINGS.put("sedation", "sedation_agent_pending");
        DEFAULT_MAPPINGS.put("procedure", "procedure_type_pending");

        // S3-1 恶化宣告
        DEFAULT_MAPPINGS.put("symptoms", "reported_symptoms");
    }

    /**
     * 映射槽位到变量并写入上下文
     *
     * @param caseInstanceId 案例实例ID
     * @param slots 分类器输出的槽位映射
     * @param context 当前上下文（可为 null，若为 null 则自动获取）
     * @return 实际写入的变量映射（变量名 → 值）
     */
    public Map<String, Object> mapSlotsToVariables(String caseInstanceId,
                                                   Map<String, Object> slots,
                                                   CaseContext context) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyMap();
        }

        // 获取或创建上下文
        CaseContext ctx = context != null ? context : contextManager.getOrCreate(caseInstanceId);
        String caseType = ctx.getCaseType();

        // 获取该案例的变量元数据
        Map<String, VariableMeta> vars = metaManager.getVariablesForCase(caseType);

        // 记录实际写入的变量
        Map<String, Object> writtenVars = new LinkedHashMap<>();

        for (Map.Entry<String, Object> slotEntry : slots.entrySet()) {
            String slotName = slotEntry.getKey();
            Object rawValue = slotEntry.getValue();

            if (rawValue == null) {
                log.debug("槽位 {} 值为 null，跳过", slotName);
                continue;
            }

            // 1. 解析变量名（槽位 → 变量名）
            String varName = resolveSlotToVariable(caseType, slotName);
            if (varName == null) {
                log.debug("槽位 {} 无映射定义，跳过", slotName);
                continue;
            }

            // 2. 获取变量元数据
            VariableMeta meta = vars.get(varName);
            if (meta == null) {
                log.warn("变量 {} 未在元数据中定义（来自槽位 {}），跳过", varName, slotName);
                continue;
            }

            // 3. 检查写入权限（分类器只能写入 L2 学员陈述变量）
            if (!metaManager.checkWritePermission(caseType, varName, WRITER_CLASSIFIER)) {
                log.warn("变量 {} 不允许分类器写入，跳过", varName);
                continue;
            }

            // 4. 类型转换
            Object convertedValue = convertValue(rawValue, meta);
            if (convertedValue == null && rawValue != null) {
                log.warn("变量 {} 值转换失败: {} -> {}", varName, rawValue, meta.getType());
                continue;
            }

            // 5. 值校验
            if (!metaManager.validateValue(caseType, varName, convertedValue)) {
                log.warn("变量 {} 值校验失败: {}", varName, convertedValue);
                continue;
            }

            // 6. 特殊逻辑：isUnstable 需要反向处理
            if ("isUnstable".equals(slotName) && convertedValue instanceof Boolean) {
                // isUnstable == true → asserted_stable == false
                convertedValue = !((Boolean) convertedValue);
                varName = "asserted_stable";
            }

            // 7. 写入上下文
            try {
                ctx.setVar(varName, convertedValue, WRITER_CLASSIFIER);
                writtenVars.put(varName, convertedValue);
                log.debug("槽位映射成功: {} → {}.{} = {}",
                    slotName, varName, meta.getType(), convertedValue);
            } catch (Exception e) {
                log.error("变量 {} 写入失败: {}", varName, e.getMessage());
            }
        }

        // 如果有写入，更新上下文
        if (!writtenVars.isEmpty()) {
            contextManager.update(caseInstanceId, ctx);
        }

        return writtenVars;
    }

    /**
     * 解析槽位到变量名
     * 优先级：专属映射 > 通用映射 > 默认映射
     */
    private String resolveSlotToVariable(String caseType, String slotName) {
        // 1. 专属映射
        Map<String, String> specificMappings = metaManager.getSpecificSlotMappings(caseType);
        if (specificMappings != null && specificMappings.containsKey(slotName)) {
            return specificMappings.get(slotName);
        }

        // 2. 通用映射
        Map<String, String> generalMappings = metaManager.getGeneralSlotMappings();
        if (generalMappings != null && generalMappings.containsKey(slotName)) {
            return generalMappings.get(slotName);
        }

        // 3. 默认映射
        return DEFAULT_MAPPINGS.get(slotName);
    }

    /**
     * 类型转换
     */
    private Object convertValue(Object rawValue, VariableMeta meta) {
        String targetType = meta.getType();
        if (targetType == null) {
            return rawValue;
        }

        // 已经是目标类型，直接返回
        if (isTypeMatch(rawValue, targetType)) {
            return rawValue;
        }

        try {
            switch (targetType) {
                case "integer":
                    return convertToInteger(rawValue);
                case "long":
                    return convertToLong(rawValue);
                case "double":
                case "float":
                    return convertToDouble(rawValue);
                case "boolean":
                    return convertToBoolean(rawValue);
                case "string":
                    return rawValue.toString();
                case "datetime":
                    return convertToDateTime(rawValue);
                case "list":
                case "array":
                    return convertToList(rawValue);
                case "map":
                    return convertToMap(rawValue);
                default:
                    return rawValue;
            }
        } catch (Exception e) {
            log.warn("类型转换失败: {} -> {}, 错误: {}", rawValue, targetType, e.getMessage());
            return null;
        }
    }

    private boolean isTypeMatch(Object value, String targetType) {
        if (value == null) return false;
        switch (targetType) {
            case "integer": return value instanceof Integer || value instanceof Number;
            case "long": return value instanceof Long || value instanceof Number;
            case "double": return value instanceof Double || value instanceof Number;
            case "boolean": return value instanceof Boolean;
            case "string": return value instanceof String;
            case "datetime": return value instanceof LocalDateTime;
            case "list": return value instanceof List;
            case "map": return value instanceof Map;
            default: return false;
        }
    }

    private Integer convertToInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        return null;
    }

    private Long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            return "true".equals(str) || "yes".equals(str) || "1".equals(str);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        return null;
    }

    private LocalDateTime convertToDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof String) {
            try {
                return LocalDateTime.parse((String) value);
            } catch (DateTimeParseException e) {
                try {
                    return LocalDateTime.parse((String) value,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException e2) {
                    return null;
                }
            }
        }
        return null;
    }

    private List<?> convertToList(Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        if (value instanceof String str) {
            // 尝试解析 JSON 数组
            try {
                // 简单处理：按逗号分割
                if (str.startsWith("[") && str.endsWith("]")) {
                    str = str.substring(1, str.length() - 1);
                }
                return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * 批量映射（便捷方法）
     */
    public Map<String, Object> mapSlotsToVariables(String caseInstanceId, Map<String, Object> slots) {
        return mapSlotsToVariables(caseInstanceId, slots, null);
    }

    /**
     * 仅解析不写入（用于预览）
     */
    public Map<String, String> resolveMapping(String caseType, Map<String, Object> slots) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String slotName : slots.keySet()) {
            String varName = resolveSlotToVariable(caseType, slotName);
            if (varName != null) {
                result.put(slotName, varName);
            }
        }
        return result;
    }
}

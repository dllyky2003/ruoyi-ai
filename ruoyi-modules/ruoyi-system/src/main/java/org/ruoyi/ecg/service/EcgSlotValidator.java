package org.ruoyi.ecg.service;

import org.ruoyi.ecg.domain.intent.*;

import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 槽位校验器
 */
@Service
public class EcgSlotValidator {

    @Resource
    private EcgRecognitionRuntime runtime;

    /**
     * 校验意图的槽位
     */
    public SlotValidationResult validate(String intentId, String userInput) {
        SlotValidationResult result = new SlotValidationResult();
        result.setValid(true);
        result.setExtractedParams(new HashMap<>());
        result.setMissingSlots(new ArrayList<>());
        result.setInvalidSlots(new ArrayList<>());

        // 1. 获取该意图需要校验的槽位列表
        List<EcgIntentSlotBinding> bindings = runtime.getSlotBindings(intentId);
        if (bindings == null || bindings.isEmpty()) {
            return result;
        }

        // 2. 从用户输入中提取参数（实际场景中可用NER或LLM）
        Map<String, Object> extractedParams = extractParams(userInput, bindings);
        result.setExtractedParams(extractedParams);

        // 3. 校验每个槽位
        for (EcgIntentSlotBinding binding : bindings) {
            String slotName = binding.getSlotName();
            Object value = extractedParams.get(slotName);

            // 获取槽位定义
            EcgSlotDefinition slotDef = runtime.getSlotDefinition(slotName);
            if (slotDef == null) {
                continue;
            }

            // 必填校验
            boolean isRequired = "required".equals(binding.getBindingType());
            if (isRequired && (value == null || value.toString().isEmpty())) {
                result.setValid(false);
                result.getMissingSlots().add(slotName);
                continue;
            }

            // 如果值为空，跳过后续校验
            if (value == null || value.toString().isEmpty()) {
                continue;
            }

            // 类型校验
            if (!validateType(slotDef, value)) {
                result.setValid(false);
                result.getInvalidSlots().add(slotName);
                result.setErrorMessage("槽位 " + slotName + " 类型不匹配");
                continue;
            }

            // 范围/枚举校验
            if (!validateValue(slotDef, value)) {
                result.setValid(false);
                result.getInvalidSlots().add(slotName);
                result.setErrorMessage("槽位 " + slotName + " 值不在允许范围内");
                continue;
            }

            // 正则校验
            if (!validateRegex(slotDef, value)) {
                result.setValid(false);
                result.getInvalidSlots().add(slotName);
                result.setErrorMessage("槽位 " + slotName + " 格式不正确");
                continue;
            }
        }

        return result;
    }

    /**
     * 提取参数（待实现）
     */
    private Map<String, Object> extractParams(
            String userInput,
            List<EcgIntentSlotBinding> bindings) {
        // TODO: 使用 NER 或 LLM 从用户输入中提取参数
        return new HashMap<>();
    }

    /**
     * 类型校验
     */
    private boolean validateType(EcgSlotDefinition slotDef, Object value) {
        String dataType = slotDef.getDataType();
        if (dataType == null) {
            return true;
        }

        switch (dataType) {
            case "number":
            case "integer":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "enum":
                return true;  // 由 validateValue 处理
            case "string":
            default:
                return value instanceof String;
        }
    }

    /**
     * 值校验（枚举、范围）
     */
    private boolean validateValue(EcgSlotDefinition slotDef, Object value) {
        // 枚举校验
        String allowedValues = slotDef.getAllowedValues();
        if (allowedValues != null && !allowedValues.isEmpty()) {
            try {
                List<String> values = parseJsonArray(allowedValues);
                if (!values.contains(value.toString())) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        // 范围校验（数值类型）
        if (value instanceof Number num) {
            if (slotDef.getMinValue() != null
                && num.doubleValue() < slotDef.getMinValue().doubleValue()) {
                return false;
            }
            return slotDef.getMaxValue() == null
                || !(num.doubleValue() > slotDef.getMaxValue().doubleValue());
        }

        return true;
    }

    /**
     * 正则校验
     */
    private boolean validateRegex(EcgSlotDefinition slotDef, Object value) {
        String regex = slotDef.getRegexPattern();
        if (regex == null || regex.isEmpty()) {
            return true;
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            return pattern.matcher(value.toString()).matches();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析JSON数组
     */
    private List<String> parseJsonArray(String json) {
        // TODO: 使用 Jackson 或 Hutool 解析
        return new ArrayList<>();
    }
}

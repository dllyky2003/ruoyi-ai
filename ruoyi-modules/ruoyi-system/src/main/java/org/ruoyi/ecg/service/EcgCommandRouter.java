package org.ruoyi.ecg.service;

import org.ruoyi.ecg.domain.*;
import org.ruoyi.ecg.domain.intent.*;

import org.ruoyi.ecg.service.impl.EcgRuntimeBuilder;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指令路由引擎
 * 职责：将学员的语音文本转换为标准化的 ActionRequest
 * 数据来源：EcgRecognitionRuntime（内存聚合根）
 */
@Service
public class EcgCommandRouter {

    private static final Logger log = LoggerFactory.getLogger(EcgCommandRouter.class);

    @Resource
    private EcgRuntimeBuilder runtimeBuilder;

    /**
     * 路由指令（主入口）
     * @param asrText 语音转文本后的内容
     * @param currentPhase 当前阶段（S0/S1/S2/...）
     * @return ActionRequest 或 null（无法识别）
     */
public ActionRequest route(String asrText, String currentPhase) {
    log.info("===== 开始路由指令 =====");
    log.info("原始ASR文本: {}", asrText);

    // ★ 1. 文本清洗
    String cleanedText = asrText;
    log.info("清洗后文本: {}", cleanedText);

    // 2. 获取聚合根
    EcgRecognitionRuntime runtime = runtimeBuilder.getRuntime();
    if (runtime == null) {
        log.error("EcgRecognitionRuntime 未初始化");
        return null;
    }

    // 3. 候选意图
    List<String> candidateIntentIds = runtime.getAllowedIntentIds(currentPhase);
    if (candidateIntentIds == null || candidateIntentIds.isEmpty()) {
        candidateIntentIds = runtime.getAllIntentIds();
    }
    if (candidateIntentIds == null || candidateIntentIds.isEmpty()) {
        log.error("没有可用意图");
        return null;
    }

    // 4. 语义匹配（使用清洗后的文本）
    String matchedIntentId = matchSemantic(cleanedText, candidateIntentIds, runtime);
    if (matchedIntentId == null) {
        log.warn("无法匹配意图，文本: {}", cleanedText);
        return null;
    }
    log.info("匹配到意图: {}", matchedIntentId);

    // 5. 获取意图定义
    EcgIntentDefinition intentDef = runtime.getIntentDefinition(matchedIntentId);
    if (intentDef == null) {
        log.warn("意图定义不存在: {}", matchedIntentId);
        return null;
    }

    // 6. 参数提取（使用清洗后的文本）
    List<EcgIntentSlotBinding> bindings = runtime.getSlotBindings(matchedIntentId);
    Map<String, Object> params = extractParams(cleanedText, bindings);
    log.info("提取参数: {}", params);

    // 7. 参数校验
    String validationError = validateParams(params, bindings, runtime);
    if (validationError != null) {
        log.warn("参数校验失败: {}", validationError);
        return null;
    }

    // 8. 构建返回
    return ActionRequest.builder()
            .actionType(intentDef.getActionType())
            .params(params)
            .intent(matchedIntentId)
            .confidence(0.85)
            .originalTranscript(asrText)  // 保留原始文本用于复盘
            .build();
}
    /**
     * 语义匹配（实际项目中调用大模型，目前使用简单的字符串包含匹配）
     */
    private String matchSemantic(String asrText, List<String> candidates, EcgRecognitionRuntime runtime) {
        if (candidates == null || candidates.isEmpty()) {
            log.warn("候选意图列表为空");
            return null;
        }

        // 优先检查是否有直接匹配的意图ID
        for (String intentId : candidates) {
            if (asrText.contains(intentId)) {
                log.info("通过意图ID直接匹配: {}", intentId);
                return intentId;
            }
        }

        // 遍历候选意图的语料进行匹配
        for (String intentId : candidates) {
            List<EcgUtterancePattern> utterances = runtime.getUtterancePatterns(intentId);
            if (utterances == null || utterances.isEmpty()) {
                continue;
            }

            for (EcgUtterancePattern utter : utterances) {
                // 检查语料是否被禁用
                if (utter.getIsActive() == null || utter.getIsActive() == 0) {
                    continue;
                }

                String utteranceText = utter.getUtterance();
                if (utteranceText == null || utteranceText.isEmpty()) {
                    continue;
                }

                // 不区分大小写的包含匹配
                if (asrText.toLowerCase().contains(utteranceText.toLowerCase())) {
                    log.info("通过语料匹配成功: intentId={}, utterance={}", intentId, utteranceText);
                    // 增加命中次数（如果Mapper有该方法）
                    // utteranceMapper.incrementHitCount(utter.getId());
                    return intentId;
                }
            }
        }

        log.warn("未匹配到任何语料，asrText: {}", asrText);
        return null;
    }

    /**
     * 参数校验
     * @return 错误信息，null表示校验通过
     */
    private String validateParams(Map<String, Object> params, List<EcgIntentSlotBinding> bindings,
                                   EcgRecognitionRuntime runtime) {
        if (bindings == null || bindings.isEmpty()) {
            return null; // 无需校验
        }

        for (EcgIntentSlotBinding binding : bindings) {
            EcgSlotDefinition slotDef = runtime.getSlotDefinition(binding.getSlotName());
            if (slotDef == null) {
                continue;
            }

            Object value = params.get(binding.getSlotName());

            // 必填校验
            if ("required".equals(binding.getBindingType()) && value == null) {
                return "缺少必填参数: " + binding.getSlotName();
            }

            // 类型校验
            if (value != null) {
                String dataType = slotDef.getDataType();
                if ("number".equals(dataType) || "integer".equals(dataType)) {
                    if (!(value instanceof Number)) {
                        return "参数 " + binding.getSlotName() + " 类型错误，期望数字";
                    }
                }

                // 数值范围校验
                if (value instanceof Number num) {
                    if (slotDef.getMinValue() != null && num.doubleValue() < slotDef.getMinValue().doubleValue()) {
                        return "参数 " + binding.getSlotName() + " 值 " + num + " 小于最小值 " + slotDef.getMinValue();
                    }
                    if (slotDef.getMaxValue() != null && num.doubleValue() > slotDef.getMaxValue().doubleValue()) {
                        return "参数 " + binding.getSlotName() + " 值 " + num + " 大于最大值 " + slotDef.getMaxValue();
                    }
                }
            }
        }

        return null;
    }

    /**
     * 参数提取（支持多种槽位类型）
     */
    private Map<String, Object> extractParams(String asrText, List<EcgIntentSlotBinding> bindings) {
        Map<String, Object> params = new HashMap<>();
        if (bindings == null || asrText == null) {
            return params;
        }

        for (EcgIntentSlotBinding binding : bindings) {
            String slotName = binding.getSlotName();

            // ★ 除颤能量
            if ("energy_joules".equals(slotName)) {
                extractNumberParam(asrText, params, slotName, "\\d+\\s*[Jj]");
                continue;
            }

            // ★ 药物剂量
            if ("dose_mg".equals(slotName)) {
                extractNumberParam(asrText, params, slotName, "\\d+(?:\\.\\d+)?\\s*(?:mg|毫克)");
                continue;
            }

            // ★ 给药途径
            if ("route".equals(slotName)) {
                if (asrText.contains("静推") || asrText.contains("静脉")) {
                    params.put("route", "iv");
                } else if (asrText.contains("骨髓") || asrText.contains("io")) {
                    params.put("route", "io");
                } else if (asrText.contains("肌注")) {
                    params.put("route", "im");
                }
                continue;
            }

            // ★ 药物名称
            if ("drug_name".equals(slotName)) {
                if (asrText.contains("肾上腺素") || asrText.contains("付肾")) {
                    params.put("drug_name", "epinephrine");
                } else if (asrText.contains("胺碘酮") || asrText.contains("可达龙")) {
                    params.put("drug_name", "amiodarone");
                } else if (asrText.contains("阿托品")) {
                    params.put("drug_name", "atropine");
                } else if (asrText.contains("利多卡因")) {
                    params.put("drug_name", "lidocaine");
                }
                continue;
            }

            // ★ 导管型号
            if ("tube_size".equals(slotName)) {
                extractNumberParam(asrText, params, slotName, "[678]\\.?[05]?");
                continue;
            }

            // ★ 其他类型槽位（通用数字提取）
            if (slotName.contains("value") || slotName.contains("amount") || slotName.contains("rate")) {
                extractNumberParam(asrText, params, slotName, "\\d+(?:\\.\\d+)?");
            }
        }

        // 如果 params 为空，但 bindings 不为空，记录一条日志
        if (params.isEmpty() && bindings != null && !bindings.isEmpty()) {
            log.debug("未能从文本中提取任何参数: {}", asrText);
        }

        return params;
    }

    /**
     * 通用数字参数提取
     */
    private void extractNumberParam(String asrText, Map<String, Object> params, String slotName, String regex) {
        try {
            // ★ 如果传入的是自定义正则，先用自定义正则匹配
            if (regex != null && !regex.isEmpty()) {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(asrText);
                if (matcher.find()) {
                    String matched = matcher.group().replaceAll("[^\\d.]", "");
                    try {
                        if (matched.contains(".")) {
                            params.put(slotName, Double.parseDouble(matched));
                        } else {
                            params.put(slotName, Integer.parseInt(matched));
                        }
                        return;
                    } catch (NumberFormatException e) {
                        // 继续尝试
                    }
                }
            }

            // ★ 通用数字提取：匹配所有数字（包括中文单位）
            Pattern pattern = Pattern.compile("(\\d+)(?:\\.(\\d+))?\\s*(?:[Jj]|焦耳|瓦|W)?");
            Matcher matcher = pattern.matcher(asrText);
            if (matcher.find()) {
                String matched = matcher.group().replaceAll("[^\\d.]", "");
                try {
                    if (matched.contains(".")) {
                        params.put(slotName, Double.parseDouble(matched));
                    } else {
                        params.put(slotName, Integer.parseInt(matched));
                    }
                    log.info("提取参数成功: {} = {}", slotName, params.get(slotName));
                } catch (NumberFormatException e) {
                    log.warn("参数提取转换失败: {} -> {}", matched, slotName);
                }
            }
        } catch (Exception e) {
            log.warn("提取参数 {} 时发生异常: {}", slotName, e.getMessage());
        }
    }
}

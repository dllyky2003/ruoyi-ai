package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.constant.ProcessKeys;
import com.ruoyi.ems.domain.Drugs;
import com.ruoyi.ems.intent.model.ExtractionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用药物参数抽取器
 * <p>职责：从文本中抽取药物名称、剂量、序列、操作状态等参数
 * <p>不承担：业务校验（如剂量上限验证），校验由上层处理
 */
@Slf4j
@Component
public class DrugDoseParser {

    /**
     * 生理盐水冲管抽取
     * 返回键：lastDrugDose, operationStatus, salineFlushDone
     */
    public Map<String, Object> parseSaline(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();

        String dose = "20ml";
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            int val = Integer.parseInt(m.group(1));
            if (val <= 100) {
                dose = val + "ml";
            }
        }
        result.put("lastDrugDose", dose);

        String status = "start";
        if (raw.contains("冲完了") || raw.contains("已冲") || raw.contains("完成")) {
            status = "done";
        } else if (raw.contains("准备冲") || raw.contains("马上冲")) {
            status = "start";
        }
        result.put(ProcessKeys.PROCESS_OPERATION_STATUS, status);
        result.put("salineFlushDone", "done".equals(status));

        return result;
    }

    /**
     * 阶梯剂量药物抽取（腺苷、阿托品等）
     * 返回键：drug, dose, doseSequence, operationStatus
     */
    public Map<String, Object> parseSequenceDrug(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();

        // 1. 识别药物（由 Drugs 工具类驱动）
        String drugCode = Drugs.matchDrugCode(raw);
        if (drugCode == null) {
            log.warn("无法从文本中识别阶梯剂量药物: {}", raw);
            return result;
        }
        result.put("drug", drugCode);

        // 2. 从记忆中读取上次剂量计数
        String memoryKey = drugCode + "_dose_count";
        Object lastCountObj = ctx.getFromMemory(memoryKey);
        int lastCount = (lastCountObj instanceof Integer) ? (int) lastCountObj : 0;

        // 3. 推断剂量序列（首剂/次剂）
        String sequence = "first";
        if (raw.contains("再") || raw.contains("第二") || raw.contains("次剂") || raw.contains("再来")) {
            sequence = "second";
        } else if (lastCount == 1) {
            sequence = "second";
        }
        result.put("doseSequence", sequence);

        // 4. 提取剂量（仅抽取，不校验）
        String doseStr = extractDose(raw);
        if (doseStr != null) {
            result.put("dose", doseStr);
        } else {
            log.debug("未提取到剂量，drugCode={}, 由上层补全", drugCode);
        }

        // 5. 更新记忆（记录本次剂量计数）
        int newCount = "first".equals(sequence) ? 1 : 2;
        ctx.putToMemory(memoryKey, newCount);

        // 6. 操作状态
        String status = "pending";
        if (raw.contains("推完") || raw.contains("完成") || raw.contains("好了")) {
            status = "done";
        } else if (raw.contains("准备") || raw.contains("马上")) {
            status = "start";
        }
        result.put("operationStatus", status);

        return result;
    }

    /**
     * 按体重计算的二线药物抽取
     * 返回键：drug, dose, operationStatus
     */
    public Map<String, Object> parseWeightBasedDrug(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();

        // 1. 识别药物
        String drugCode = Drugs.matchDrugCode(raw);
        if (drugCode == null) {
            log.warn("无法从文本中识别药物: {}", raw);
            return result;
        }
        result.put("drug", drugCode);

        // 2. 提取剂量（仅抽取，不校验）
        String dose = extractDose(raw);
        if (dose != null) {
            result.put("dose", dose);
        }

        // 3. 操作状态
        String status = parseStatus(raw);
        result.put("operationStatus", status);

        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 提取剂量字符串（支持 mg、mg/kg）
     */
    private String extractDose(String raw) {
        // 优先匹配 mg/kg
        Pattern p = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(mg/kg)");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            return m.group(1) + "mg/kg";
        }
        // 其次匹配 mg 或 毫克
        p = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(mg|毫克)");
        m = p.matcher(raw);
        if (m.find()) {
            return m.group(1) + "mg";
        }
        return null;
    }

    /**
     * 解析操作状态
     */
    private String parseStatus(String raw) {
        if (raw.contains("推完") || raw.contains("完成") || raw.contains("好了")) {
            return "done";
        }
        if (raw.contains("准备") || raw.contains("马上") || raw.contains("开始")) {
            return "start";
        }
        return "pending";
    }
}

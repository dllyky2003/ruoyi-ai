package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备类参数解析器：除颤仪同步模式、能量、充电状态。
 * 供 S-102-2-1 / S-102-2-2 / S-102-2-4 意图的 processor 调用。
 */
@Component
public class DeviceParser {

    /** 同步模式：确认同步模式、打开同步 */
    public Map<String, Object> parseSync(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw == null) {
            result.put("defibSyncMode", null);
            return result;
        }
        if (raw.contains("同步") || raw.contains("sync") || raw.contains("SYNC")) {
            // 明确"关同步"或"取消同步"时返回 false
            if (raw.contains("关") || raw.contains("取消") || raw.contains("off")) {
                result.put("defibSyncMode", false);
            } else {
                result.put("defibSyncMode", true);
            }
        } else {
            result.put("defibSyncMode", null);
        }
        return result;
    }

    /** 能量选择：选择 100 焦耳、调到 150J */
    public Map<String, Object> parseEnergy(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw == null) {
            result.put("defibEnergy", null);
            return result;
        }
        // 匹配数字 + J/焦耳/焦
        Pattern p = Pattern.compile("(\\d{1,3})\\s*(?:J|j|焦耳|焦)");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            int energy = Integer.parseInt(m.group(1));
            if (energy < 1 || energy > 360) {
                // ASR 异常值，丢弃
                result.put("defibEnergy", null);
            } else {
                result.put("defibEnergy", energy);
            }
        } else {
            result.put("defibEnergy", null);
        }
        return result;
    }

    /** 充电状态：充电到 100J、充电完成 */
    public Map<String, Object> parseCharged(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw == null) {
            result.put("defibCharged", null);
            return result;
        }
        if (raw.contains("充电") || raw.contains("charge")) {
            // 明确"没充电"/"未充电"时返回 false
            if (raw.contains("没") || raw.contains("未") || raw.contains("失败")) {
                result.put("defibCharged", false);
            } else {
                result.put("defibCharged", true);
            }
        } else {
            result.put("defibCharged", null);
        }
        return result;
    }
}

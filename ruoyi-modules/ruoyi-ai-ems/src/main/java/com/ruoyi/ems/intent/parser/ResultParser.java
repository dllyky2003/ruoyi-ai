package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class ResultParser {
    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();

        if (raw.contains("转复") || raw.contains("成功") || raw.contains("恢复")) {
            result.put("vagalEffectObserved", "success");
        } else if (raw.contains("未转复") || raw.contains("无效") || raw.contains("失败")) {
            result.put("vagalEffectObserved", "failure");
        } else {
            result.put("vagalEffectObserved", "failure");
        }

        // 心律类型
        if (raw.contains("窦性")) result.put("rhythmConfirmed", "sinus");
        else if (raw.contains("房颤")) result.put("rhythmConfirmed", "afib");
        else if (raw.contains("房扑")) result.put("rhythmConfirmed", "aflutter");
        else if (raw.contains("室上速")) result.put("rhythmConfirmed", "svt");
        else if (raw.contains("室速")) result.put("rhythmConfirmed", "vt");
        else result.put("rhythmConfirmed", "unknown");

        // ★ 新增：由 rhythmConfirmed 派生布尔 rhythmConverted，供状态机出边条件使用
        boolean converted = "sinus".equals(result.get("rhythmConfirmed"));
        result.put("rhythmConverted", converted);

        return result;
    }

    /**
     * 复律结果陈述：转复为窦性心律了。
     * 供 S-102-2-8 意图的 processor 调用。
     */
    public Map<String, Object> parseCardioversion(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        String raw = ctx.getRawText();
        if (raw == null) {
            result.put("rhythmConverted", null);
            result.put("cardioversionWithoutSedation", null);
            return result;
        }

        // 转复成功判定
        if (raw.contains("转复") || raw.contains("恢复窦性") || raw.contains("窦性心律")) {
            result.put("rhythmConverted", true);
        } else if (raw.contains("未转复") || raw.contains("没转")) {
            result.put("rhythmConverted", false);
        } else {
            result.put("rhythmConverted", null);
        }

        // 未镇静即复律（由 Tutor 规则 R_NO_SEDATION_WARN 判定，此处仅透传）
        if (raw.contains("未镇静") || raw.contains("没有镇静") || raw.contains("清醒")) {
            result.put("cardioversionWithoutSedation", true);
        } else {
            result.put("cardioversionWithoutSedation", null);
        }
        return result;
    }
}

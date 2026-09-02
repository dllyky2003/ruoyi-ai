package com.ruoyi.ems.intent.utils;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.intent.model.IntentDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IntentDescriptionProvider {

    @Autowired
    private CaseIntentDefinitionsLoader intentLoader;

    /**
     * 将意图编码集合转换为中文描述
     * 例如：[G3-1, G3-2] → "G3-1（开启监护仪）、G3-2（连接电极片）"
     */
    public String buildDescription(Set<String> intentCodes) {
        if (intentCodes == null || intentCodes.isEmpty()) {
            return "无可用操作";
        }

        return intentCodes.stream()
            .map(code -> {
                IntentDefinition def = intentLoader.getIntent(code);
                if (def != null && def.getNameCn() != null) {
                    return code + "（" + def.getNameCn() + "）";
                }
                return code;
            })
            .collect(Collectors.joining("、"));
    }

    /**
     * 带引导语（用于提示词）
     */
    public String buildPromptDescription(Set<String> intentCodes) {
        String desc = buildDescription(intentCodes);
        if (desc.isEmpty() || "无可用操作".equals(desc)) {
            return "当前状态下无可用操作";
        }
        return "可执行操作：" + desc;
    }
}

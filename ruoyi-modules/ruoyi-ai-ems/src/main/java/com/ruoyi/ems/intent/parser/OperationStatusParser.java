package com.ruoyi.ems.intent.parser;

import org.springframework.stereotype.Component;

@Component
public class OperationStatusParser {
    public String parse(String rawText) {
        if (rawText.contains("推完") || rawText.contains("完成") || rawText.contains("好了")) {
            return "done";
        } else if (rawText.contains("准备") || rawText.contains("马上") || rawText.contains("开始")) {
            return "start";
        }
        return "pending";
    }
}

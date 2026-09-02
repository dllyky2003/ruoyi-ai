package com.ruoyi.ems.intent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiPromptPair {
    private String systemPrompt;
    private String userPrompt;
}

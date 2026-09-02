package org.ruoyi.system.domain.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateDraftRequest {
    private String prompt;
    private String model;   // 可选，默认配置
    private String systemPrompt;
}

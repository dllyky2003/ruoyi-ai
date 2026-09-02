package com.ruoyi.ems.intent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "acls.classifier")
public class IntentClassifierProperties {
    /** 分类模式：one-stage 或 two-stage */
    private String mode = "one-stage";
    /** LLM失败时是否启用兜底 */
    private boolean fallbackOnError = true;
}

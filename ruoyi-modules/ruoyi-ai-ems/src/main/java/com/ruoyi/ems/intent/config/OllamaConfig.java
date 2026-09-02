package com.ruoyi.ems.intent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ollama")
public class OllamaConfig {
    private String baseUrl = "http://localhost:11434";
    private String model = "qwen2.5:latest";
    private Options options = new Options();

    @Data
    public static class Options {
        private Double temperature = 0.0;
        private Double topP = 0.1;
        private Integer topK = 10;
        private Integer numPredict = 256;
        private Double repeatPenalty = 1.0;
        private String format = "json";
    }
}

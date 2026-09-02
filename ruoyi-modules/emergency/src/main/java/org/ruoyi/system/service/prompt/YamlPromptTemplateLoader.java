package org.ruoyi.system.service.prompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class YamlPromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlPromptTemplateLoader.class);

    // 缓存：同一个 YAML 路径只解析一次
    private final ConcurrentHashMap<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    /**
     * @param resourcePath 例如: "prompts/medicationHistoryPrompt.yml"
     */
    public PromptTemplate load(String resourcePath) {
        return cache.computeIfAbsent(resourcePath, this::readFromClasspath);
    }

    private PromptTemplate readFromClasspath(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                throw new IllegalStateException("未找到提示词文件: " + resourcePath);
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try (InputStream is = resource.getInputStream()) {
                PromptTemplate template = mapper.readValue(is, PromptTemplate.class);

                if (template == null
                    || Objects.isNull(template.getSystemPrompt())
                    || Objects.isNull(template.getUserPromptTemplate())) {
                    throw new IllegalStateException("YAML 字段缺失: systemPrompt/userPromptTemplate -> " + resourcePath);
                }
                return template;
            }
        } catch (Exception e) {
            log.error("加载 YAML 提示词失败: {}", resourcePath, e);
            throw new IllegalStateException(e);
        }
    }
}

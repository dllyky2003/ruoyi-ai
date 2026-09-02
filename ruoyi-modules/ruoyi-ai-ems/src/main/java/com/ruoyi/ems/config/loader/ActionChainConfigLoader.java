package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.orchestrator.model.OrchestrationConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ActionChainConfigLoader {

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, StepTemplate> stepTemplateMap = new ConcurrentHashMap<>();
    private final Map<String, OrchestrationConfig> orchestrationConfigMap = new ConcurrentHashMap<>();

    private boolean loaded = false;

    @PostConstruct
    public void init() {
        try {
            loadAll();
            loaded = true;
            log.info("✅ 动作链配置加载完成，步骤模板: {} 个，案例编排: {} 个",
                stepTemplateMap.size(), orchestrationConfigMap.size());
        } catch (Exception e) {
            log.warn("⚠️ 动作链配置加载失败（可能文件不存在），系统将以传统模式运行", e);
        }
    }

    public synchronized void loadAll() throws Exception {
        loadStepLibrary();
        loadAllChainConfigs();
    }

    private void loadStepLibrary() {
        try {
            Resource resource = new PathMatchingResourcePatternResolver()
                .getResource("classpath:config/acls/GLOBAL/step_library.json");
            if (!resource.exists()) {
                log.warn("⚠️ step_library.json 不存在，跳过加载");
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                JsonNode templatesNode = root.path("templates");
                if (templatesNode.isMissingNode()) {
                    log.warn("⚠️ step_library.json 缺少 'templates' 字段");
                    return;
                }
                Map<String, StepTemplate> map = objectMapper.convertValue(templatesNode,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, StepTemplate>>() {});
                stepTemplateMap.putAll(map);
                log.info("📚 已加载 {} 个步骤模板", stepTemplateMap.size());
            }
        } catch (Exception e) {
            log.error("加载 step_library.json 失败", e);
        }
    }

    private void loadAllChainConfigs() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:config/acls/cases/**/orchestration/chain.json");
            if (resources.length == 0) {
                log.debug("未找到任何 chain.json 文件");
                return;
            }
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    OrchestrationConfig config = objectMapper.readValue(is, OrchestrationConfig.class);
                    String path = resource.getURL().getPath();
                    String caseType = extractCaseTypeFromPath(path);
                    if (caseType == null) {
                        caseType = config.getCaseType();
                    }
                    if (caseType != null) {
                        orchestrationConfigMap.put(caseType, config);
                        log.info("📋 已加载案例 {} 的动作链编排，包含 {} 个动作链",
                            caseType,
                            config.getActionChains() != null ? config.getActionChains().size() : 0);
                    } else {
                        log.warn("无法确定案例类型，跳过文件: {}", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.error("加载 chain.json 失败: {}", resource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.error("扫描 chain.json 文件失败", e);
        }
    }

    private String extractCaseTypeFromPath(String path) {
        int casesIdx = path.indexOf("cases/");
        if (casesIdx == -1) return null;
        String afterCases = path.substring(casesIdx + 6);
        int orchIdx = afterCases.indexOf("/orchestration");
        if (orchIdx == -1) return null;
        String casePath = afterCases.substring(0, orchIdx);
        String[] parts = casePath.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    // ==================== 查询方法 ====================

    public StepTemplate getStepTemplate(String stepId) {
        return stepTemplateMap.get(stepId);
    }

    public OrchestrationConfig getOrchestrationConfig(String caseType) {
        return orchestrationConfigMap.get(caseType);
    }

    public Map<String, OrchestrationConfig> getAllOrchestrationConfigs() {
        return orchestrationConfigMap;
    }

    public boolean isLoaded() {
        return loaded;
    }

    // ★ 新增：判断是否为动作链
    public boolean isActionChain(String caseType, String intentCode) {
        OrchestrationConfig config = getOrchestrationConfig(caseType);
        if (config == null || config.getActionChains() == null) {
            return false;
        }
        return config.getActionChains().containsKey(intentCode);
    }

    // ★ 新增：获取步骤列表
    public List<String> getStepsForIntent(String caseType, String intentCode) {
        OrchestrationConfig config = getOrchestrationConfig(caseType);
        if (config == null || config.getActionChains() == null) {
            return Collections.emptyList();
        }
        OrchestrationConfig.ChainConfig chain = config.getActionChains().get(intentCode);
        return chain != null && chain.getSteps() != null ? chain.getSteps() : Collections.emptyList();
    }

    // ==================== 热重载 ====================

    @SuppressWarnings("unused")
    public void reloadAll() {
        stepTemplateMap.clear();
        orchestrationConfigMap.clear();
        try {
            loadAll();
            log.info("🔄 动作链配置全量重载完成");
        } catch (Exception e) {
            log.error("全量重载失败", e);
        }
    }

    @SuppressWarnings("unused")
    public void reloadCase(String caseType) {
        reloadAll(); // 简化实现
    }

    // ==================== 内部类 ====================

    @lombok.Data
    public static class StepTemplate {
        @JsonProperty("ref_atomic")
        private String refAtomic;

        @JsonProperty("duty")
        private String duty;

        @JsonProperty("vr_animation")
        private String vrAnimation;

        @JsonProperty("durationSec")
        private Integer durationSec;

        @JsonProperty("description")
        private String description;
    }
}

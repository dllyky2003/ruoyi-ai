package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.ems.config.CaseDefinition;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaseLoaderService {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    // 可选：添加缓存，避免重复加载
    private final ConcurrentHashMap<String, CaseDefinition> cache = new ConcurrentHashMap<>();

    /**
     * 从 classpath 加载指定的 case 配置文件
     *
     * @param path 例如 "config/acls/cases/stable_narrow/case.yaml"
     * @return CaseDefinition 对象
     */
    public CaseDefinition loadCaseFromClasspath(String path) {
        Resource resource = new PathMatchingResourcePatternResolver().getResource("classpath:" + path);
        try (InputStream is = resource.getInputStream()) {
            return yamlMapper.readValue(is, CaseDefinition.class);
        } catch (IOException e) {
            throw new RuntimeException("加载案例配置文件失败，路径: " + path, e);
        }
    }

    /**
     * 从文件系统加载
     */
    public CaseDefinition loadCaseFromFile(String filePath) throws IOException {
        return yamlMapper.readValue(new java.io.File(filePath), CaseDefinition.class);
    }

    /**
     * 根据 caseId 从 resources/config/acls/cases/{caseId}/case.yaml 加载配置（带缓存）
     *
     * @param caseId 案例ID，例如 "stable_narrow"
     * @return CaseDefinition 对象
     */
    public CaseDefinition loadCaseByCaseId(String caseId) {
        // 构建标准路径
        String path = "config/acls/cases/" + caseId + "/case.yaml";
        // 使用缓存（按需启用）
        return cache.computeIfAbsent(path, this::loadCaseFromClasspath);
    }

    /**
     * 清除缓存（可用于重新加载）
     */
    public void clearCache() {
        cache.clear();
    }
}

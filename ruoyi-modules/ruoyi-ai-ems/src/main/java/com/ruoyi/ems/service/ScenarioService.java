package com.ruoyi.ems.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.ems.domain.entity.EmsScenario;
import com.ruoyi.ems.mapper.EmsScenarioMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景管理服务
 */
@Slf4j
@Service
public class ScenarioService {

    private final EmsScenarioMapper scenarioMapper;
    private final Map<String, Map<String, Object>> scenarioConfigCache = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 构造器注入 EmsScenarioMapper
     */
    public ScenarioService(EmsScenarioMapper scenarioMapper) {
        this.scenarioMapper = scenarioMapper;
    }

    /**
     * 获取场景配置
     */
    public Map<String, Object> getScenarioConfig(String scenarioId) {
        return scenarioConfigCache.get(scenarioId);
    }

    /**
     * 获取所有场景
     */
    public List<EmsScenario> listAllScenarios() {
        return scenarioMapper.selectList(null);
    }

      /**
     * 根据ID获取场景实体
     */
    public EmsScenario getById(String id) {
        return scenarioMapper.selectById(id);
    }
}

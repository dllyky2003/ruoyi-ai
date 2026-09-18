package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.domain.PhysiologyConfig;
import com.ruoyi.ems.domain.StatePhysiology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.FieldProperty;
import org.yaml.snakeyaml.introspector.MethodProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.error.YAMLException;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PhysiologyConfigService {

    private final ResourceLoader resourceLoader;
    private final Map<String, PhysiologyConfig> cache = new ConcurrentHashMap<>();
    private final Yaml yaml;

    public PhysiologyConfigService() {
        this.resourceLoader = new DefaultResourceLoader();
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(PhysiologyConfig.class, options);
        // 使用自定义 PropertyUtils 支持下划线 → 驼峰自动映射
        constructor.setPropertyUtils(new UnderscoreToCamelPropertyUtils());
        this.yaml = new Yaml(constructor);
    }

    /**
     * 根据案例 ID 加载生理配置，自动缓存
     * 支持从外部文件系统（file:）或 classpath 加载
     */
    public PhysiologyConfig loadCase(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId 不能为空");
        }
        PhysiologyConfig cached = cache.get(caseId);
        if (cached != null) {
            return cached;
        }
        // 尝试加载
        PhysiologyConfig config = null;
        // 优先外部路径
        String externalPath = "file:./config/acls/cases/" + caseId + "/_physiology.yaml";
        Resource resource = resourceLoader.getResource(externalPath);
        if (!resource.exists()) {
            String classPath = "classpath:config/acls/cases/" + caseId + "/_physiology.yaml";
            resource = resourceLoader.getResource(classPath);
            if (!resource.exists()) {
                log.error("❌ 生理配置文件不存在: 外部路径 {}, classpath 路径 {}", externalPath, classPath);
                return null; // 不缓存 null，以免后续无法重新尝试
            }
        }
        try (InputStream in = resource.getInputStream()) {
            config = yaml.load(in);
            cache.put(caseId, config);
        } catch (Exception e) {
            log.error("❌ 加载生理配置失败: caseId={}, 错误: {}", caseId, e.getMessage(), e);
            // 不缓存失败结果，返回 null
        }
        return config;
    }

    /**
     * 获取该案例下所有状态的生理基线（即干预措施拓扑）
     */
    public Map<String, StatePhysiology> getInterventionStates(String caseId) {
        PhysiologyConfig config = loadCase(caseId);
        return config.getStatePhysiology();
    }

    /**
     * 获取特定状态的生理基线
     */
    /**
     * 获取特定状态的生理基线
     * @return 状态生理基线对象，若状态不存在则返回 null（不抛异常）
     */
    public StatePhysiology getState(String caseId, String stateId) {
        Map<String, StatePhysiology> states = getInterventionStates(caseId);
        if (states == null) {
            return null;
        }
        return states.get(stateId);
    }

    /**
     * 清除指定案例的缓存
     */
    public void evictCache(String caseId) {
        cache.remove(caseId);
        log.debug("🧹 清除案例 {} 的生理配置缓存", caseId);
    }

    /**
     * 清除所有缓存（全量重载时使用）
     */
    public void evictAll() {
        cache.clear();
        log.info("🧹 生理配置缓存已全部清除");
    }

    /**
     * 自定义 PropertyUtils，支持 YAML 键名下划线转驼峰映射到 Java 字段/getter/setter
     */
    public static class UnderscoreToCamelPropertyUtils extends PropertyUtils {

        public Property getProperty(Class<?> type, String name) {
            // 1. 直接按原名查找
            Property property = findFieldProperty(type, name);
            if (property != null) return property;

            // 2. 下划线转驼峰后查找
            String camelName = toCamelCase(name);
            property = findFieldProperty(type, camelName);
            if (property != null) return property;

            // 3. 若名称包含点，取点后的部分（去掉前缀）再尝试
            if (name.contains(".")) {
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                // 先直接查找简单名
                property = findFieldProperty(type, simpleName);
                if (property != null) return property;
                // 再转驼峰（但简单名通常已经是驼峰，如heartRate）
                // 但可能简单名也有下划线，所以再转一次
                String simpleCamel = toCamelCase(simpleName);
                property = findFieldProperty(type, simpleCamel);
                if (property != null) return property;
            }

            // 4. 尝试按驼峰名查找 getter/setter
            property = findMethodProperty(type, camelName);
            if (property != null) return property;

            // 5. 如果还是找不到，抛出异常
            throw new YAMLException("Unable to find property '" + name + "' on class: " + type.getName());
        }

        private Property findFieldProperty(Class<?> type, String name) {
            try {
                Field field = type.getDeclaredField(name);
                return new FieldProperty(field);
            } catch (NoSuchFieldException e) {
                return null;
            }
        }

        private Property findMethodProperty(Class<?> type, String name) {
            try {
                // 使用 PropertyDescriptor 来获取 getter/setter
                PropertyDescriptor pd = new PropertyDescriptor(name, type);
                // 检查是否有读或写方法
                if (pd.getReadMethod() != null || pd.getWriteMethod() != null) {
                    return new MethodProperty(pd);
                }
            } catch (IntrospectionException e) {
                // 忽略，说明没有该名称的属性
            }
            return null;
        }

        /**
         * 将下划线命名转换为驼峰命名，例如 "default_transition_duration" → "defaultTransitionDuration"
         */
        private String toCamelCase(String name) {
            if (name == null || name.isEmpty()) {
                return name;
            }
            StringBuilder sb = new StringBuilder();
            boolean nextUpper = false;
            for (char c : name.toCharArray()) {
                if (c == '_') {
                    nextUpper = true;
                } else {
                    sb.append(nextUpper ? Character.toUpperCase(c) : c);
                    nextUpper = false;
                }
            }
            return sb.toString();
        }
    }
}

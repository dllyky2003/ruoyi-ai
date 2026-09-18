package com.ruoyi.ems.intent.test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 级联分类器测试专用 Spring 上下文。
 *
 * <p>用普通 {@code @Configuration}，配合 {@code @ContextConfiguration}
 * （而不是 {@code @SpringBootTest}），不触发 Spring Boot 的任何自动配置，
 * 因此不会加载 RuoYi 的 MybatisPlusConfig / AllUrlHandler / SpringDocConfig 等。
 *
 * <p>因为不加载自动配置，Jackson 的 {@link ObjectMapper} 需要手动提供，
 * 否则 {@code IntentDictLoader} 会因缺 ObjectMapper 而启动失败。
 *
 * <p>{@link CaseIntentDefinitionsLoader#init()} 不是 {@code @PostConstruct}，
 * 原项目由 {@code CaseConfigLoader} 显式调用。本测试用 {@link PostConstruct}
 * 手动触发，否则意图字典为空、所有意图加载为 0。
 */
@Slf4j
@Configuration
@ComponentScan(
    basePackages = {
        "com.ruoyi.ems.intent.classifier",
        "com.ruoyi.ems.intent.dict",
        "com.ruoyi.ems.intent.model",
        "com.ruoyi.ems.config.loader"
    },
    excludeFilters = {
        @Filter(
            type = FilterType.REGEX,
            pattern = "com\\.ruoyi\\.ems\\.intent\\.classifier\\.(OneStageClassifier|TwoStageClassifier|FallbackClassifier)"
        )
    }
)
public class CascadeTestConfig {

    @Autowired
    private CaseIntentDefinitionsLoader intentLoader;

    /**
     * 手动初始化意图字典。
     *
     * <p>{@code init()} 是幂等的（内部有 loaded 标志），重复调用安全。
     */
    @PostConstruct
    public void initIntents() {
        log.info("[CascadeTestConfig] 手动初始化 CaseIntentDefinitionsLoader...");
        intentLoader.init();
        log.info("[CascadeTestConfig] 意图字典初始化完成，意图总数: {}",
            intentLoader.getAllIntentList().size());
    }

    /**
     * 手动提供 ObjectMapper。
     *
     * <p>配置与 {@code CaseIntentDefinitionsLoader} 里自带的一致：
     * <ul>
     *   <li>FAIL_ON_UNKNOWN_PROPERTIES = false</li>
     *   <li>SNAKE_CASE 命名策略</li>
     * </ul>
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}

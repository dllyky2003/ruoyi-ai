package com.ruoyi.acls.tutor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Tutor 模块装配入口。
 *
 * <p>第一步只建立骨架，不注册任何实现类。
 * 后续步骤在 engine/、service/、integration/ 等子包中添加实现，
 * 由本类的 @ComponentScan 自动发现。</p>
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.ruoyi.acls.tutor")
public class TutorAutoConfiguration {

    public TutorAutoConfiguration() {
        log.info("[ACLS Tutor] ruoyi-acls-tutor 模块已装配");
    }
}

package com.ruoyi.ems.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SPI 默认实现装配。
 *
 * <p>tutor 未部署时注入 No-Op，保证原状态机行为不变。
 * tutor 部署后其自身实现会覆盖（{@code @ConditionalOnMissingBean} 使 No-Op 回退）。</p>
 */
@Slf4j
@Configuration
public class SpiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ClinicalRuleService.class)
    public ClinicalRuleService noOpClinicalRuleService() {
        log.info("[ACLS SPI] ClinicalRuleService 未提供实现，启用 No-Op（tutor 未部署）");
        return new NoOpClinicalRuleService();
    }

    @Bean
    @ConditionalOnMissingBean(TemporalEventSink.class)
    public TemporalEventSink noOpTemporalEventSink() {
        log.info("[ACLS SPI] TemporalEventSink 未提供实现，启用 No-Op（tutor 未部署）");
        return new NoOpTemporalEventSink();
    }

    @Bean
    @ConditionalOnMissingBean(StateTransitionHook.class)
    public StateTransitionHook noOpStateTransitionHook() {
        log.info("[ACLS SPI] StateTransitionHook 未提供实现，启用 No-Op（tutor 未部署）");
        return new NoOpStateTransitionHook();
    }
}

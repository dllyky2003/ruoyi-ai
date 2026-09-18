package com.ruoyi.ems.asr;


import com.ruoyi.ems.asr.AsrWebSocketHandler;
import com.ruoyi.ems.asr.model.AsrClient;
import com.ruoyi.ems.dispatcher.SessionCommandDispatcher;
import com.ruoyi.ems.service.EvaluationService;
import com.ruoyi.ems.service.SessionService;
import com.ruoyi.ems.tts.KokoroTtsClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AsrWebSocketConfig implements ApplicationContextAware {

    private ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.ctx = applicationContext;
    }

    @PostConstruct
    public void init() {
        try {
            AsrWebSocketHandler.injectBeans(
                ctx.getBean(AsrClient.class),
                ctx.getBean(SessionService.class),
                ctx.getBean(EvaluationService.class),
                ctx.getBean(SessionCommandDispatcher.class),
                ctx.getBean(KokoroTtsClient.class)
            );
            log.info("✅ AsrWebSocketHandler 依赖注入完成");
        } catch (Exception e) {
            log.error("❌ AsrWebSocketHandler 依赖注入失败", e);
        }
    }
}

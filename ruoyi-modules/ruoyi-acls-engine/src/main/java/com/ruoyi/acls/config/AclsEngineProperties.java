package com.ruoyi.acls.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ACLS引擎配置属性
 * 对应 application.yml 中的 acls.engine.* 和 acls.cognitive.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "acls")
public class AclsEngineProperties {

    /** 引擎全局配置 */
    private Engine engine = new Engine();

    /** 认知调度配置 */
    private Cognitive cognitive = new Cognitive();

    /** 调度器配置 */
    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Engine {
        /** 是否启用新引擎，默认 true */
        private boolean enabled = true;
        /** 运行模式：hybrid（新引擎优先）或 legacy（完全走旧逻辑） */
        private String mode = "hybrid";
    }

    @Data
    public static class Cognitive {
        /** 是否启用组员LLM思考 */
        private boolean enabled = true;
        /** 单个LLM推理超时（毫秒） */
        private long queueTimeout = 10000L;
        /** 普通思考冷却（毫秒） */
        private long normalCooldown = 3000L;
        /** 紧急思考冷却（毫秒） */
        private long criticalCooldown = 500L;
        /** 是否启用硬编码反射规则 */
        private boolean reflectionEnabled = true;
    }

    @Data
    public static class Scheduler {
        /** 自主心跳间隔（毫秒） */
        private long tickInterval = 100L;
        /** 任务超时缓冲秒数（在预计时长基础上额外允许的秒数） */
        private int taskTimeoutBuffer = 10;
    }
}

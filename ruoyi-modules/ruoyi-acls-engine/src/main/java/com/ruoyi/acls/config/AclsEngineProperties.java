package com.ruoyi.acls.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "acls")
public class AclsEngineProperties {

    private Engine engine = new Engine();
    private Cognitive cognitive = new Cognitive();
    private Scheduler scheduler = new Scheduler();

    /** ★ 组员模式：executor（仅命令执行）| human（模拟真人） */
    private String memberMode = "executor";   // 默认改为 executor

    private List<RuleConfig> rules = new ArrayList<>();

    @Data
    public static class Engine {
        private boolean enabled = true;
        private String mode = "hybrid";
    }

    @Data
    public static class Cognitive {
        private boolean enabled = true;
        private long queueTimeout = 10000L;
        private long normalCooldown = 3000L;
        private long criticalCooldown = 500L;
        private boolean reflectionEnabled = true;
    }

    @Data
    public static class Scheduler {
        private long tickInterval = 100L;
        private int taskTimeoutBuffer = 10;
        private Long timeoutBuffer = 5000L;
    }

    @Data
    public static class RuleConfig {
        private String id;
        private String duty;
        private String condition;
        private String speech;
        private String animation;
        private int priority;
    }
}

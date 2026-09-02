package com.ruoyi.ems.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestrationContextRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, OrchestrationInterpreter> interpreterMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建会话对应的解释器
     */
    public OrchestrationInterpreter getOrCreate(String sessionId) {
        return interpreterMap.computeIfAbsent(sessionId, id -> {
            log.info("🆕 为会话 {} 创建新的编排解释器", id);
            return applicationContext.getBean(OrchestrationInterpreter.class);
        });
    }

    public OrchestrationInterpreter get(String sessionId) {
        return interpreterMap.get(sessionId);
    }

    public void remove(String sessionId) {
        OrchestrationInterpreter interpreter = interpreterMap.remove(sessionId);
        if (interpreter != null) {
            interpreter.interrupt("SYSTEM_SHUTDOWN");
        }
        log.info("🗑️ 已清理会话 {} 的编排上下文", sessionId);
    }

    public void sendAck(String sessionId, boolean confirmed) {
        OrchestrationInterpreter interpreter = interpreterMap.get(sessionId);
        if (interpreter != null) {
            interpreter.sendAck(confirmed);
        } else {
            log.warn("⚠️ 会话 {} 无活跃的解释器实例", sessionId);
        }
    }
}

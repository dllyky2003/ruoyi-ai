package com.ruoyi.acls.adapter;

import com.ruoyi.acls.engine.AclsCommandEngine;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.spi.IntelligentCommandProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ACLS 新引擎适配器 —— 实现旧模块定义的策略接口
 * 这是新旧模块之间的唯一桥梁
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AclsIntelligentProcessor implements IntelligentCommandProcessor {

    private final AclsCommandEngine engine;

    /**
     * 仅处理 ACLS 相关的意图（以 "ACLS_" 或 "S2-" 开头的）
     * 其他意图仍然走旧逻辑
     */
    @Override
    public boolean supports(String intentId) {
        return intentId != null && (
            intentId.startsWith("ACLS_") ||
                intentId.startsWith("S2-") ||
                intentId.equals("START_CPR") ||
                intentId.equals("DEFIBRILLATE") ||
                intentId.equals("GIVE_EPINEPHRINE")
        );
    }

    @Override
    public Map<String, Object> processCommand(String sessionId, IntentCommand command, Object webSocketSession) {
        log.info("🧠 [新引擎] 处理指令: session={}, intent={}, text={}",
            sessionId, command.getIntentCode(), command.getRawText());

        try {
            // 调用新引擎
            CommandResult result = engine.processCommand(
                command.getIntentCode(),
                sessionId,
                command.getRawText() != null ? command.getRawText() : ""
            );

            // 构造响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("responseText", result.getResponseText());
            response.put("animations", result.getAnimations());
            response.put("contextUpdates", result.getContextUpdates());

            // 如果有错误信息，记录日志
            if (!result.isSuccess() && result.getErrorMessage() != null) {
                log.warn("新引擎执行失败: {}", result.getErrorMessage());
            }

            return response;

        } catch (Exception e) {
            log.error("新引擎执行异常，将降级到旧逻辑", e);
            // 抛出异常让调用方降级到旧逻辑
            throw new RuntimeException("新引擎执行失败: " + e.getMessage(), e);
        }
    }
}

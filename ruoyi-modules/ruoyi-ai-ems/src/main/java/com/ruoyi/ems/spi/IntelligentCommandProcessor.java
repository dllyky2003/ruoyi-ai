package com.ruoyi.ems.spi;

import com.ruoyi.ems.domain.IntentCommand;

import java.util.Map;

/**
 * 智能指令处理策略接口
 * 由 ruoyi-acls-engine 实现，SessionService 通过 ObjectProvider 调用
 *
 * 定义在旧模块中，但旧模块不依赖任何新模块的类，绝对安全
 */
public interface IntelligentCommandProcessor {

    /**
     * 判断当前处理器是否支持该意图
     */
    boolean supports(String intentId);

    /**
     * 处理指令
     * @param sessionId 会话ID
     * @param command 已识别好的意图命令
     * @param webSocketSession WebSocket会话（用于实时推送）
     * @return 处理结果，若返回 null 或抛出异常，则降级到旧逻辑
     */
    Map<String, Object> processCommand(String sessionId, IntentCommand command, Object webSocketSession);
}

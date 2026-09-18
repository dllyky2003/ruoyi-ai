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


    public Map<String, Object> processCommand(String sessionId, IntentCommand command);
}

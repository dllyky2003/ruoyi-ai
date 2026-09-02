package com.ruoyi.ems.engine.multiagent;

import lombok.Data;

import java.util.Map;

/**
 * AgentInput扩展数据
 */
@Data
public class AgentInputExtra {
    /**
     * 其他智能体输出（用于反馈智能体）
     */
    private Map<String, Object> agentOutputs;

    /**
     * 场景配置
     */
    private Map<String, Object> scenarioConfig;

    /**
     * 额外上下文
     */
    private Map<String, Object> extra;
}

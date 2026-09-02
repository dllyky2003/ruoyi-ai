package com.ruoyi.ems.engine.multiagent.supervisor;


import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;

import java.util.Map;

/**
 * 结果融合策略接口
 */
public interface FusionStrategy {
    SupervisorResult fuse(Map<String, AgentOutput> agentOutputs);
}

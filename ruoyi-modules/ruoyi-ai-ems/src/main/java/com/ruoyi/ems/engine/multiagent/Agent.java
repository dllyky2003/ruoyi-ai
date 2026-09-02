package com.ruoyi.ems.engine.multiagent;


import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;

/**
 * 智能体统一接口
 */
public interface Agent {

    /**
     * 获取智能体名称
     */
    String getName();

    /**
     * 执行智能体分析
     */
    AgentOutput execute(AgentInput input);

    /**
     * 获取智能体描述
     */
    default String getDescription() {
        return "ACLS专业评估智能体";
    }
}

package com.ruoyi.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.ems.domain.entity.EmsAgentEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EmsAgentEvaluationMapper extends BaseMapper<EmsAgentEvaluation> {

    /**
     * 根据会话ID查询所有智能体评估记录
     */
    @Select("SELECT * FROM ems_agent_evaluation WHERE session_id = #{sessionId} AND deleted = 0")
    List<EmsAgentEvaluation> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据会话ID和智能体名称查询评估记录
     */
    @Select("SELECT * FROM ems_agent_evaluation WHERE session_id = #{sessionId} AND agent_name = #{agentName} AND deleted = 0")
    EmsAgentEvaluation selectBySessionAndAgent(@Param("sessionId") String sessionId,
                                               @Param("agentName") String agentName);
}

package com.ruoyi.ems.room.dto;


import lombok.Data;

import java.util.Map;

/**
 * NPC 动作结果。
 * <p>
 * Unity 执行完 npc_action_command 后，通过以下三类消息回传：
 * <ul>
 *   <li>npc_action_completed —— result=success，携带 outputs 和 queryResult</li>
 *   <li>npc_action_failed    —— result=failed，携带 reason</li>
 *   <li>npc_action_cancelled —— result=cancelled，携带 reason</li>
 * </ul>
 * 三者的 data 结构一致，只是 result / reason / outputs 的填充不同。
 * </p>
 */
@Data
public class NpcActionResult {

    /** 关联的请求 ID，由服务端下发时生成 */
    private String requestId;

    /** 执行动作的 NPC ID，对应 TeamMember.id */
    private String npcId;

    /** 动作编码，如 check_pulse / OPEN_AIRWAY / administer_shock_aed */
    private String actionCode;

    /** 结果：success / failed / cancelled */
    private String result;

    /** 失败或取消原因，result=success 时为 null */
    private String reason;

    /** 动作开始时间（Unity 侧本地时间，毫秒） */
    private long startedAt;

    /** 动作完成时间（Unity 侧本地时间，毫秒） */
    private long completedAt;

    /** 实际执行时长（秒）。有动画时是动画时长，无动画时是计时时长 */
    private float actualDuration;

    /** 完成时的模拟时间（秒） */
    private float simTime;

    /** 是否走了模拟执行（无动画时的计时模拟） */
    private boolean simulated;

    /** 动作完成后要写回的变量。仅 result=success 时有意义 */
    private Map<String, Object> outputs;

    /** query 类动作的查询结果。仅 skillType=query 时有值 */
    private QueryResultDto queryResult;

    /**
     * query 类动作的查询结果。
     */
    @Data
    public static class QueryResultDto {
        /** 查询的目标变量名，如 phys.hasPulse */
        private String targetVar;
        /** 查询到的值 */
        private Object value;
    }
}

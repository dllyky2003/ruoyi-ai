package com.ruoyi.ems.room.dto;

import com.ruoyi.ems.npc.TeamSize;
import com.ruoyi.ems.room.dto.PresetChangeDto;
import com.ruoyi.ems.room.dto.TeamMemberDto;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 案例配置下发 DTO。
 * <p>
 * 服务端在 {@code start_case_request} 后组装，通过 {@code case_config} 消息下发给 Unity。
 * Unity 据此初始化 Pulse 生理引擎和 NPC agents。
 * </p>
 * <p>
 * 数据来源：
 * <ul>
 *   <li>{@code emsSessionId} / {@code scenarioId} / {@code caseType} —— EmsSession</li>
 *   <li>{@code currentState} —— CaseContext 变量快照</li>
 *   <li>{@code members} —— TeamManager.getTeamMembers()</li>
 *   <li>{@code initialVitals} —— CaseContext.getVariables()（resetDefaults 后）</li>
 *   <li>{@code presetChanges} —— 案例基础配置（后续从 _preset_changes.yaml 加载）</li>
 * </ul>
 * </p>
 */
@Data
public class CaseConfigDto {

    // ==================== 会话标识 ====================

    /** 业务会话 ID（EmsSession.id），后续 Unity 上行消息需携带 */
    private String emsSessionId;

    /** 房间 ID（SessionRoom.sessionId），仅用于服务端路由 */
    private String roomSessionId;

    /** 案例 ID / 场景 ID */
    private String scenarioId;

    /** 案例类型，通常与 scenarioId 相同 */
    private String caseType;

    /** 学员姓名 */
    private String traineeName;

    /** 模式：EXAM / TEACH */
    private String mode;

    // ==================== 状态机 ====================

    /** 初始状态机节点，如 F_101_ASSESS_STABILITY */
    private String currentState;

    // ==================== 团队 ====================

    /** 团队模板 ID：3MAN / 4MAN / 5MAN */
    private TeamSize teamSize;

    /** 团队成员列表 */
    private List<TeamMemberDto> members;

    // ==================== 生理引擎 ====================

    /** 初始体征快照。key 为变量名（phys.* / clinical.* 等），value 为初始值 */
    private Map<String, Object> initialVitals;

    /** 预设变化列表。案例运行过程中按 trigger 触发 */
    private List<PresetChangeDto> presetChanges;

    /** Pulse 初始状态名（可选）。若 Unity 用 PatientStatesData，填入状态名直接切换 */
    private String pulseInitialState;

    // ==================== 附加信息 ====================

    /** 状态描述映射，供 Unity 端展示。key 为状态 ID，value 为描述 */
    private Map<String, String> stateDescriptions;

    /** 扩展字段，预留 */
    private Map<String, Object> extras;
}

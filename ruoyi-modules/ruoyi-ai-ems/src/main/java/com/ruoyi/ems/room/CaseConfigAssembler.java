package com.ruoyi.ems.room;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.entity.EmsSession;


import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.TeamSize;
import com.ruoyi.ems.room.dto.CaseConfigDto;
import com.ruoyi.ems.room.dto.PresetChangeDto;
import com.ruoyi.ems.room.dto.TeamMemberDto;
import com.ruoyi.ems.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class CaseConfigAssembler {

    private final EmsContextManager contextManager;
    private final TeamManager teamManager;
    private final SessionService sessionService;

    /**
     * 组装案例配置。
     *
     * @param emsSessionId  业务会话 ID
     * @param roomSessionId 房间 ID
     * @return 下发到 Unity 的案例配置
     */
    public CaseConfigDto assemble(String emsSessionId, String roomSessionId) {
        // 1. 业务会话
        EmsSession ems = sessionService.getSessionById(emsSessionId);
        if (ems == null) {
            throw new IllegalStateException("业务会话不存在: " + emsSessionId);
        }

        // 2. 会话上下文（含 resetDefaults 后的初始变量）
        CaseContext ctx = contextManager.getSnapshot(emsSessionId);
        if (ctx == null) {
            throw new IllegalStateException("会话上下文不存在: " + emsSessionId);
        }

        String caseType = ctx.getCaseType();
        if (caseType == null) {
            caseType = ems.getScenarioId();
        }

        Map<String, Object> initialVitals = new HashMap<>(ctx.getVariables());
        String currentState = String.valueOf(
            initialVitals.getOrDefault("currentState", "UNKNOWN"));

        // 3. 团队成员
        List<TeamMember> members = teamManager.getTeamMembers(emsSessionId);
        TeamSize teamSize =resolveTemplateId(emsSessionId, members);


        // 4. 组装
        CaseConfigDto dto = new CaseConfigDto();
        dto.setEmsSessionId(emsSessionId);
        dto.setRoomSessionId(roomSessionId);
        dto.setScenarioId(ems.getScenarioId());
        dto.setCaseType(caseType);
        dto.setTraineeName(ems.getTraineeName());
        dto.setMode(ems.getMode());
        dto.setCurrentState(currentState);
        dto.setTeamSize(teamSize);
        dto.setMembers(convertMembers(members));
        dto.setInitialVitals(initialVitals);
        dto.setPresetChanges(loadPresetChanges(caseType));   // 暂空
        dto.setPulseInitialState(null);                       // 暂空
        dto.setStateDescriptions(Map.of());                   // 暂空
        dto.setExtras(null);

        log.info("📦 case_config 组装完成: ems={}, teamSize={}, members={}, vars={}",
            emsSessionId, teamSize,
            dto.getMembers().size(), initialVitals.size());

        if (teamSize == null) {
            log.warn("无法识别团队模板: ems={}, teamSize={}", emsSessionId, teamSize);
        }

        return dto;
    }

    // ==================== 团队 ====================

    /**
     * 解析团队模板 code。优先从 TeamManager 拿，拿不到按人数兜底。
     */
    private TeamSize resolveTemplateId(String emsSessionId, List<TeamMember> members) {
        TeamSize fromManager = teamManager.getTeamTemplateId(emsSessionId);
        if (fromManager != null) {
            return fromManager;
        }

        // 兜底：按 slot 数反推
        int size = members != null ? members.size() : 0;
        return switch (size) {
            case 4 -> TeamSize.TEAM_3MAN;
            case 5 -> TeamSize.TEAM_4MAN;
            case 6 -> TeamSize.TEAM_5MAN;
            default -> TeamSize.defaultSize();
        };
    }

    private List<TeamMemberDto> convertMembers(List<TeamMember> members) {
        if (members == null || members.isEmpty()) return List.of();

        List<TeamMemberDto> result = new ArrayList<>(members.size());
        for (TeamMember m : members) {
            TeamMemberDto dto = new TeamMemberDto();
            dto.setMemberId(m.getId());
            dto.setTitle(m.getTitle());
            dto.setName(m.getName());
            dto.setDuties(m.getDuties() != null
                ? m.getDuties().stream().map(Duty::name).toList()
                : List.of());
            dto.setColor(m.getColor());
            dto.setTtsVoice(m.getTtsVoice());
            dto.setHuman(m.getDuties() != null
                && m.getDuties().contains(Duty.TEAM_LEADER));
            result.add(dto);
        }
        return result;
    }

    // ==================== 预设变化 ====================

    /**
     * 加载案例的预设体征变化。
     * <p>
     * 当前返回空列表，后续在案例配置里补 {@code _preset_changes.yaml} 后填充。
     * </p>
     */
    private List<PresetChangeDto> loadPresetChanges(String caseType) {
        return List.of();
    }
}

package com.ruoyi.ems.room;

import com.ruoyi.ems.config.loader.PhysiologyConfigLoader;
import com.ruoyi.ems.context.AclsInternalVars;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.PhysiologyConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseConfigAssembler {

    private final EmsContextManager contextManager;
    private final TeamManager teamManager;
    private final SessionService sessionService;
    private final PhysiologyConfigLoader physiologyConfigLoader;

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

        // ★ 过滤内部变量，避免 process.clockTickAt / evaluation.errorChain 泄漏到 Unity
        Map<String, Object> initialVitals = filterInitialVitals(ctx.getVariables());

        String currentState = String.valueOf(
            initialVitals.getOrDefault("currentState", "UNKNOWN"));

        // 3. 团队成员
        List<TeamMember> members = teamManager.getTeamMembers(emsSessionId);
        TeamSize teamSize = resolveTemplateId(emsSessionId, members);

        // 4. 组装基础字段
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

        if (teamSize == null) {
            log.warn("无法识别团队模板: ems={}, teamSize={}", emsSessionId, teamSize);
        }

        // ===== 5. 加载生理配置（走 default 版本，避免无谓的回退） =====
        PhysiologyConfig physiologyConfig = physiologyConfigLoader.loadCase(caseType);
        dto.setPhysiologyConfig(physiologyConfig);

        log.info("📦 case_config 组装完成: ems={}, teamSize={}, members={}, vars={}, " +
                "physioConfigHash={}",
            emsSessionId, teamSize, dto.getMembers().size(), initialVitals.size(),
            physiologyConfig != null ? abbreviate(physiologyConfig.getConfigHash()) : "<null>");

        return dto;
    }

    /**
     * ★ 过滤 initialVitals：剔除 {@link AclsInternalVars#NAMES} 中的内部变量。
     *
     * <p>背景：ClockService 每秒写 process.clockTickAt 用于触发派生刷新；
     * AclsCommandEngine 写 evaluation.errorChain 用于评分；这些都不该下发 Unity。</p>
     */
    private Map<String, Object> filterInitialVitals(Map<String, Object> rawVars) {
        if (rawVars == null || rawVars.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> filtered = new LinkedHashMap<>(rawVars.size());
        for (Map.Entry<String, Object> e : rawVars.entrySet()) {
            if (AclsInternalVars.isInternal(e.getKey())) {
                continue;   // ★ 跳过内部变量
            }
            filtered.put(e.getKey(), e.getValue());
        }
        return filtered;
    }

    private static String abbreviate(String hash) {
        if (hash == null) return "<null>";
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }

    // ==================== 团队 ====================

    private TeamSize resolveTemplateId(String emsSessionId, List<TeamMember> members) {
        TeamSize fromManager = teamManager.getTeamTemplateId(emsSessionId);
        if (fromManager != null) {
            return fromManager;
        }
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

    private List<PresetChangeDto> loadPresetChanges(String caseType) {
        return List.of();
    }
}

package com.ruoyi.ems.npc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 从 text / action 反推 npcId 的兜底解析器。
 * 数据源是 TeamManager 里的 TeamMember 花名册。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NpcSpeakerResolver {

    private final TeamManager teamManager;

    /** "张伟报告：..." / "王芳收到，开始执行 xxx" → 对应 memberId */
    public String resolveByText(String emsSessionId, String text) {
        if (text == null || text.isBlank()) return null;

        List<TeamMember> members = teamManager.getTeamMembers(emsSessionId);
        if (members == null || members.isEmpty()) return null;

        // 1) 按姓名匹配（张伟 / 王芳 / 刘洋）
        for (TeamMember m : members) {
            if (m.getName() != null && !m.getName().isBlank()
                && text.contains(m.getName())) {
                return m.getId();
            }
        }
        // 2) 按职位匹配（医生 / 护士 / 司机）
        for (TeamMember m : members) {
            if (m.getTitle() != null && !m.getTitle().isBlank()
                && text.contains(m.getTitle())) {
                return m.getId();
            }
        }
        log.warn("NpcSpeakerResolver.resolveByText 未命中: ems={}, text='{}'",
            emsSessionId, text);
        return null;
    }

    /** 动作码 → 执行人。params 里已带 npcId 直接用；否则按 duty 找。 */
    public String resolveByAction(String emsSessionId, String action,
                                  Map<String, Object> params) {
        if (params != null && params.get("npcId") != null) {
            return params.get("npcId").toString();
        }
        if (action == null) return null;

        // action → duty（按你 TeamTemplate 里实际分配的职责改）
        Duty duty = switch (action) {
            case "TURN_ON_ECG", "OBTAIN_ECG", "ADENOSINE_PUSH",
                 "PUSH_DRUG", "MONITOR" -> Duty.MONITOR_DEFIB;
            case "CPR", "AIRWAY"                -> Duty.CPR;
            case "VAGAL_MANEUVER"               -> Duty.AIRWAY;
            case "ASSESS"                       -> Duty.ASSESS;
            default -> null;
        };
        if (duty == null) return null;

        // 用 TeamManager 现成的方法找"在岗且空闲"的角色
        return teamManager.findIdleMemberForDuty(emsSessionId, duty)
            .map(TeamMember::getId)
            .orElseGet(() -> {
                // 空闲的找不到，就退回找所有成员里职责匹配的第一个
                for (TeamMember m : teamManager.getTeamMembers(emsSessionId)) {
                    if (m.getDuties() != null
                        && m.getDuties().contains(duty)) {
                        return m.getId();
                    }
                }
                return null;
            });
    }
}

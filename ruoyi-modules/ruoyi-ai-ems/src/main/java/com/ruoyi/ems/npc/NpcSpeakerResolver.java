package com.ruoyi.ems.npc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 text / action 反推 npcId 的兜底解析器。
 * 数据源是 TeamManager 里的 TeamMember 花名册。
 *
 * <p>★ 2026-09-23 修改（修复 npcId=null 导致 Unity 端消息全丢）：</p>
 * <ul>
 *   <li>新增"当前执行者"缓存 {@code currentExecutorCache}：由
 *       {@link com.ruoyi.acls.router.NpcSkillExecutor} 在调用 skill.execute() 之前写入。</li>
 *   <li>{@link #resolveByText} 模板未命中时，从缓存兜底返回执行者，避免返回 null。</li>
 *   <li>{@link #resolveByAction} 优先用当前执行者，比按 duty 反查更准确。</li>
 * </ul>
 *
 * <p>背景：技能描述文案（如"除颤电极片已贴好。"）不含姓名/职位，
 * 原 resolveByText 会返回 null，导致 NpcUnityBridgeService 下行的
 * npc_dialogue / npc_action 中 npcId 为空，Unity 端反序列化失败。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NpcSpeakerResolver {

    private final TeamManager teamManager;

    /**
     * ★ 新增：当前正在执行技能的 member（按 session 隔离）。
     *
     * <p>同一 session 的指令是串行处理的（SessionCommandDispatcher 单线程池），
     * 不会出现同 session 多技能并发执行的情况，所以用简单 Map 即可。</p>
     */
    private final Map<String, String> currentExecutorCache = new ConcurrentHashMap<>();

    /**
     * ★ 新增：设置当前执行者。由 NpcSkillExecutor 在调用 skill.execute() 之前写入。
     *
     * @param emsSessionId 业务会话 ID
     * @param memberId     执行者 memberId
     */
    public void setCurrentExecutor(String emsSessionId, String memberId) {
        if (emsSessionId != null && memberId != null) {
            currentExecutorCache.put(emsSessionId, memberId);
        }
    }

    /**
     * ★ 新增：清理当前执行者。技能执行完毕后可选择性调用，非必须。
     */
    public void clearCurrentExecutor(String emsSessionId) {
        if (emsSessionId != null) {
            currentExecutorCache.remove(emsSessionId);
        }
    }

    /** "张伟报告：..." / "王芳收到，开始执行 xxx" → 对应 memberId */
    public String resolveByText(String emsSessionId, String text) {
        if (text == null || text.isBlank()) {
            return fallbackToCurrentExecutor(emsSessionId, text);
        }

        List<TeamMember> members = teamManager.getTeamMembers(emsSessionId);
        if (members == null || members.isEmpty()) {
            return fallbackToCurrentExecutor(emsSessionId, text);
        }

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

        // ★ 3) 新增：模板未命中 → 兜底到当前执行者
        return fallbackToCurrentExecutor(emsSessionId, text);
    }

    /**
     * ★ 新增：模板未命中时的兜底逻辑。
     *
     * <p>优先级：模板命中（姓名/职位） > 当前执行者 > null</p>
     */
    private String fallbackToCurrentExecutor(String emsSessionId, String text) {
        String executorId = currentExecutorCache.get(emsSessionId);
        if (executorId != null) {
            log.debug("NpcSpeakerResolver 模板未命中，兜底到当前执行者: ems={}, executor={}, text='{}'",
                emsSessionId, executorId, text);
            return executorId;
        }
        log.warn("NpcSpeakerResolver.resolveByText 未命中且无兜底执行者: ems={}, text='{}'",
            emsSessionId, text);
        return null;
    }

    /** 动作码 → 执行人。params 里已带 npcId 直接用；否则按 duty 找。 */
    public String resolveByAction(String emsSessionId, String action,
                                  Map<String, Object> params) {
        // 1) params 里显式指定 npcId，最高优先级
        if (params != null && params.get("npcId") != null) {
            return params.get("npcId").toString();
        }

        // ★ 2) 新增：动作码没有显式 npcId 时，优先用当前执行者
        //    （比按 duty 反查更准确，因为同一 duty 可能有多个候选）
        String executorId = currentExecutorCache.get(emsSessionId);
        if (executorId != null) {
            log.debug("NpcSpeakerResolver.resolveByAction 使用当前执行者: ems={}, action={}, executor={}",
                emsSessionId, action, executorId);
            return executorId;
        }

        if (action == null) return null;

        // 3) 兜底：action → duty → 按 duty 找成员
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

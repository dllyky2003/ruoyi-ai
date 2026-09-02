package com.ruoyi.acls.factory;

import com.ruoyi.acls.cognitive.CognizantTeamMember;
import com.ruoyi.acls.config.AclsEngineProperties;
import com.ruoyi.acls.member.VirtualTeamMember;

import com.ruoyi.acls.cognitive.MemberCognitiveService;
import com.ruoyi.acls.cognitive.ReflexRule;
import com.ruoyi.acls.scheduler.CognitiveScheduler;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.Status;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import com.ruoyi.ems.tts.TtsService;
import com.ruoyi.ems.npc.VrClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟组员工厂（位于新模块内部）
 * 不修改 TeamManager，而是通过其公开 API 获取底层成员，再包装为虚拟组员
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualMemberFactory {

    private final TeamManager teamManager;          // 依赖 A 模块（运行时可用）
    private final ApplicationEventPublisher eventPublisher;
    private final TtsService ttsService;
    private final VrClientService vrClientService;
    private final SpELExpressionEvaluator spelEvaluator;
    private final MemberCognitiveService cognitiveService;
    private final CognitiveScheduler cognitiveScheduler;
    private final AclsEngineProperties properties;

    // 缓存：sessionId_memberId -> VirtualTeamMember
    private final Map<String, VirtualTeamMember> cache = new ConcurrentHashMap<>();
    private final Map<String, List<ReflexRule>> reflexRulesCache = new ConcurrentHashMap<>();

    /**
     * 获取或创建虚拟组员（由新引擎的调度器调用）
     */
    public VirtualTeamMember getOrCreate(String sessionId, Duty duty) {
        // 1. 从 TeamManager 获取底层成员（使用其现有方法）
        Optional<TeamMember> memberOpt = teamManager.findIdleMemberForDuty(sessionId, duty);
        if (memberOpt.isEmpty()) {
            // 若没有空闲，尝试获取任意一个具备该职责的成员（通过遍历）
            List<TeamMember> allMembers = teamManager.getTeamMembers(sessionId);
            memberOpt = allMembers.stream()
                .filter(m -> m.isOnDuty() && m.getDuties().contains(duty))
                .findFirst();
        }
        if (memberOpt.isEmpty()) {
            log.warn("未找到具备职责 {} 的成员", duty.getDisplayName());
            return null;
        }

        TeamMember member = memberOpt.get();
        String cacheKey = sessionId + "_" + member.getId();

        return cache.computeIfAbsent(cacheKey, key -> {
            List<ReflexRule> rules = loadReflexRules(sessionId);
            // 创建认知组员（完全位于新模块内部）
            CognizantTeamMember virtual = new CognizantTeamMember(
                member,
                eventPublisher,
                ttsService,
                vrClientService,
                spelEvaluator,
                cognitiveService,
                cognitiveScheduler,
                properties,
                rules
            );
            log.info("🧑‍⚕️ 为成员 {} 创建虚拟组员 (session={})", member.getFullName(), sessionId);
            return virtual;
        });
    }

    /**
     * 获取某会话的所有虚拟组员（用于驱动自主心跳）
     */
    public List<VirtualTeamMember> getAll(String sessionId) {
        return cache.entrySet().stream()
            .filter(e -> e.getKey().startsWith(sessionId + "_"))
            .map(Map.Entry::getValue)
            .toList();
    }

    /**
     * 清理缓存
     */
    public void clear(String sessionId) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(sessionId + "_"));
        reflexRulesCache.remove(sessionId);
    }

    private List<ReflexRule> loadReflexRules(String sessionId) {
        return reflexRulesCache.computeIfAbsent(sessionId, k -> {
            List<ReflexRule> rules = new ArrayList<>();

            // 规则1：VF/VT 检测
            rules.add(ReflexRule.actionRule(
                "VF_DETECTED",
                Duty.MONITOR_DEFIB,
                "#context.getVar('current_rhythm') == 'VF' || #context.getVar('current_rhythm') == 'VT'",
                "⚠️ 室颤！准备除颤仪，能量200J！",
                "defib_prep",
                10
            ));

            // 规则2：按压中断警告
            rules.add(ReflexRule.speechRule(
                "CPR_HANDS_OFF",
                Duty.CPR,
                "#context.getVar('hands_off_time', 0) > 5",
                "⚠️ 按压中断超过5秒，立即恢复！",
                5
            ));

            // 规则3：肾上腺素间隔提醒
            rules.add(ReflexRule.speechRule(
                "EPI_DUE",
                Duty.IV_MED,
                "#utils.getEpiLastGivenTime() > 180 && #context.getCurrentState() == 'PEA'",
                "⏰ 肾上腺素间隔已超3分钟，建议推注第二剂",
                3
            ));

            log.info("✅ 为会话 {} 加载了 {} 条反射规则", sessionId, rules.size());
            return rules;
        });
    }
}

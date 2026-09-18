package com.ruoyi.acls.factory;

import com.ruoyi.acls.cognitive.MemberCognitiveService;
import com.ruoyi.acls.cognitive.ReflexRule;
import com.ruoyi.acls.cognitive.ReflexRuleLoader;
import com.ruoyi.acls.config.AclsEngineProperties;
import com.ruoyi.acls.member.CognizantTeamMember;
import com.ruoyi.acls.member.VirtualTeamMember;
import com.ruoyi.acls.scheduler.CognitiveScheduler;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.VrClientService;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import com.ruoyi.ems.tts.KokoroTtsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualMemberFactory {

    private final TeamManager teamManager;
    private final ApplicationEventPublisher eventPublisher;
    private final KokoroTtsClient ttsService;
    private final VrClientService vrClientService;
    private final SpELExpressionEvaluator spelEvaluator;
    private final MemberCognitiveService cognitiveService;
    private final CognitiveScheduler cognitiveScheduler;
    private final AclsEngineProperties properties;
    private final ReflexRuleLoader ruleLoader;
    // ★ 已移除 private final AtomicActionRegistry actionRegistry;

    private final Map<String, VirtualTeamMember> cache = new ConcurrentHashMap<>();

    public VirtualTeamMember getOrCreate(String sessionId, String caseType, Duty duty) {
        Optional<TeamMember> memberOpt = teamManager.findIdleMemberForDuty(sessionId, duty);
        if (memberOpt.isEmpty()) {
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
            List<ReflexRule> rules = new ArrayList<>();
            String memberMode = properties.getMemberMode();
            if ("human".equalsIgnoreCase(memberMode)) {
                rules = ruleLoader.getRulesForCase(caseType);
                log.info("🧠 模式=[human]，为成员 {} 加载 {} 条反射规则 (案例: {})",
                    member.getFullName(), rules.size(), caseType);
            } else {
                log.info("⚙️ 模式=[executor]，成员 {} 仅执行命令，无自主行为", member.getFullName());
            }

            // ★ 创建成员时不再传递 actionRegistry
            return new CognizantTeamMember(
                sessionId,
                member,
                eventPublisher,
                ttsService,
                vrClientService,
                spelEvaluator,
                cognitiveService,
                cognitiveScheduler,
                properties,
                rules,
                caseType
            );
        });
    }

    public List<VirtualTeamMember> getAll(String sessionId) {
        return cache.entrySet().stream()
            .filter(e -> e.getKey().startsWith(sessionId + "_"))
            .map(Map.Entry::getValue)
            .toList();
    }

    public void clear(String sessionId) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(sessionId + "_"));
        log.info("🗑️ 已清理会话 {} 的虚拟组员缓存", sessionId);
    }

    public void clearAll() {
        cache.clear();
        log.info("🗑️ 已清理所有虚拟组员缓存");
    }
}

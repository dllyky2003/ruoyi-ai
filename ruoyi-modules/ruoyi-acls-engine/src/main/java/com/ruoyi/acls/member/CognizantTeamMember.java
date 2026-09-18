package com.ruoyi.acls.member;

import com.ruoyi.acls.cognitive.MemberCognitiveService;
import com.ruoyi.acls.cognitive.ReflexRule;
import com.ruoyi.acls.config.AclsEngineProperties;
import com.ruoyi.acls.scheduler.CognitiveScheduler;
import com.ruoyi.acls.scheduler.ThinkPriority;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.VrClientService;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import com.ruoyi.ems.tts.KokoroTtsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CognizantTeamMember extends VirtualTeamMember {

    private final SpELExpressionEvaluator spelEvaluator;
    private final MemberCognitiveService cognitiveService;
    private final CognitiveScheduler cognitiveScheduler;
    private final AclsEngineProperties properties;
    private final List<ReflexRule> reflexRules;
    private final String caseType;

    private final AtomicLong lastThinkTime = new AtomicLong(0);

    /**
     * 构造器（移除 actionRegistry 参数）
     */
    public CognizantTeamMember(String sessionId,
                               TeamMember member,
                               ApplicationEventPublisher eventPublisher,
                               KokoroTtsClient ttsService,
                               VrClientService vrClientService,
                               SpELExpressionEvaluator spelEvaluator,
                               MemberCognitiveService cognitiveService,
                               CognitiveScheduler cognitiveScheduler,
                               AclsEngineProperties properties,
                               List<ReflexRule> reflexRules,
                               String caseType) {
        super(sessionId, member, eventPublisher, ttsService, vrClientService);
        this.spelEvaluator = spelEvaluator;
        this.cognitiveService = cognitiveService;
        this.cognitiveScheduler = cognitiveScheduler;
        this.properties = properties;
        this.reflexRules = reflexRules;
        this.caseType = caseType;
    }

    /**
     * 自主思考定时触发
     */
    public void autonomousTick(CaseContext context) {
        if (!"human".equalsIgnoreCase(properties.getMemberMode())) {
            return;
        }

        if (isExecuting() && !isInterruptibleCurrentTask()) {
            return;
        }

        // 1. 反射规则（快速响应）
        if (properties.getCognitive().isReflectionEnabled()) {
            for (ReflexRule rule : reflexRules) {
                if (!hasDuty(rule.getDuty())) continue;
                try {
                    // ★ 修正：使用 evaluateCondition 替代 evaluateGuard
                    boolean triggered = spelEvaluator.evaluateCondition(rule.getCondition(), context);
                    if (triggered) {
                        log.info("⚡ [{}] 触发反射规则: {} (案例: {})", getFullName(), rule.getId(), caseType);
                        rule.getAction().execute(this, context);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("反射规则评估失败: {}", rule.getId(), e);
                }
            }
        }

        // 2. 慢思考（LLM 推理）
        if (properties.getCognitive().isEnabled() && isIdle() && canThinkNow()) {
            submitThinkTask(context);
        }
    }

    private void submitThinkTask(CaseContext context) {
        String systemPrompt = cognitiveService.buildRolePrompt(this);
        String userPrompt = cognitiveService.buildPerceivedData(this, context);

        ThinkPriority priority = detectUrgency(context)
            ? ThinkPriority.CRITICAL
            : ThinkPriority.NORMAL;

        cognitiveScheduler.submitThinkTask(
            getId(),
            systemPrompt,
            userPrompt,
            priority,
            (memberId, response) -> {
                if (response != null && !response.contains("待命") && response.length() > 2) {
                    speak(getFullName() + " 思考：" + response);
                   // log.info("🧠 {} 慢思考输出: {}", getFullName(), response);
                }
            }
        );

        long cooldown = (priority == ThinkPriority.CRITICAL)
            ? properties.getCognitive().getCriticalCooldown()
            : properties.getCognitive().getNormalCooldown();
        lastThinkTime.set(System.currentTimeMillis() + cooldown);
    }

    private boolean canThinkNow() {
        return System.currentTimeMillis() > lastThinkTime.get();
    }

    private boolean detectUrgency(CaseContext context) {
        try {
            String rhythm = context.getVar("current_rhythm", String.class);
            return "VF".equals(rhythm) || "VT".equals(rhythm);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isInterruptibleCurrentTask() {
        String task = getCurrentTask();
        return task != null && !task.contains("DISCHARGE");
    }
}

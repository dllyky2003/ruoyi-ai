package com.ruoyi.acls.cognitive;

import com.ruoyi.acls.config.AclsEngineProperties;
import com.ruoyi.acls.member.VirtualTeamMember;
import com.ruoyi.acls.scheduler.CognitiveScheduler;
import com.ruoyi.acls.scheduler.ThinkPriority;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.VrClientService;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import com.ruoyi.ems.tts.TtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 认知组员 —— 在 VirtualTeamMember 基础上增加“自主思考”能力
 * 包含：硬编码反射（紧急）+ LLM慢思考（非紧急）
 */
@Slf4j
public class CognizantTeamMember extends VirtualTeamMember {

    private final SpELExpressionEvaluator spelEvaluator;
    private final MemberCognitiveService cognitiveService;
    private final CognitiveScheduler cognitiveScheduler;
    private final AclsEngineProperties properties;
    private final List<ReflexRule> reflexRules;

    private final AtomicLong lastThinkTime = new AtomicLong(0);

    public CognizantTeamMember(TeamMember member,
                               ApplicationEventPublisher eventPublisher,
                               TtsService ttsService,
                               VrClientService vrClientService,
                               SpELExpressionEvaluator spelEvaluator,
                               MemberCognitiveService cognitiveService,
                               CognitiveScheduler cognitiveScheduler,
                               AclsEngineProperties properties,
                               List<ReflexRule> reflexRules) {
        super(member, eventPublisher, ttsService, vrClientService);
        this.spelEvaluator = spelEvaluator;
        this.cognitiveService = cognitiveService;
        this.cognitiveScheduler = cognitiveScheduler;
        this.properties = properties;
        this.reflexRules = reflexRules;
    }

    /**
     * 自主心跳（由外部调度器每100ms调用）
     * 这是组员“感知-决策-行动”的入口
     */
    public void autonomousTick(CaseContext context) {
        // 1. 如果正在执行不可中断任务（如放电），跳过感知
        if (isExecuting() && !isInterruptibleCurrentTask()) {
            return;
        }

        // 2. 硬编码反射规则（紧急情况，优先级最高，不走LLM）
        if (properties.getCognitive().isReflectionEnabled()) {
            for (ReflexRule rule : reflexRules) {
                if (!hasDuty(rule.getDuty())) continue;
                try {
                    boolean triggered = spelEvaluator.evaluateGuard(rule.getCondition(), context);
                    if (triggered) {
                        log.info("⚡ [{}] 触发反射规则: {}", getFullName(), rule.getId());
                        rule.getAction().execute(this, context);
                        return;  // 一个tick只执行一条反射
                    }
                } catch (Exception e) {
                    log.warn("反射规则评估失败: {}", rule.getId(), e);
                }
            }
        }

        // 3. 慢思考（LLM），仅在空闲且冷却完成时提交排队
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
                    // 将LLM思考结果转化为语音建议（NORMAL优先级语音）
                    getTtsService().synthesizeAndSend(null,
                        getFullName() + " 思考：" + response);
                    log.info("🧠 {} 慢思考输出: {}", getFullName(), response);
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
        return task != null && !task.contains("DISCHARGE"); // 放电期间不可打断
    }
}

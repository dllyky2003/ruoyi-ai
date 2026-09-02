package com.ruoyi.acls.cognitive;

import com.ruoyi.acls.member.VirtualTeamMember;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.Duty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 硬编码反射规则 —— 组员的“本能反应”
 * 用于处理紧急情况（VF检测、按压中断等），响应时间 < 5ms
 */
@Data
@AllArgsConstructor
public class ReflexRule {
    /** 规则ID */
    private String id;
    /** 适用职责（只有具备该职责的组员才会触发） */
    private Duty duty;
    /** SpEL条件表达式（如 #context.getRhythm() == 'VF' ） */
    private String condition;
    /** 执行动作（语音或动作） */
    private ReflexAction action;
    /** 优先级（同一时刻只执行最高优先级规则） */
    private int priority;

    @FunctionalInterface
    public interface ReflexAction {
        void execute(VirtualTeamMember member, CaseContext context);
    }

    /**
     * 便捷工厂：创建“语音预警”规则
     */
    public static ReflexRule speechRule(String id, Duty duty, String condition,
                                        String speechText, int priority) {
        return new ReflexRule(id, duty, condition,
            (member, ctx) -> {
                member.getTtsService().synthesizeAndSend(null,
                    member.getFullName() + "：" + speechText);
            },
            priority
        );
    }

    /**
     * 便捷工厂：创建“动作+语音”规则
     */
    public static ReflexRule actionRule(String id, Duty duty, String condition,
                                        String speechText, String vrAnimation, int priority) {
        return new ReflexRule(id, duty, condition,
            (member, ctx) -> {
                member.getTtsService().synthesizeAndSend(null,
                    member.getFullName() + "：" + speechText);
                member.getVrClientService().sendAnimation(
                    ctx.getSessionId(), vrAnimation, 2
                );
            },
            priority
        );
    }
}

package com.ruoyi.acls.cognitive;

import com.ruoyi.acls.member.VirtualTeamMember;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.Duty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReflexRule {
    private String id;
    private Duty duty;
    private String condition;
    private ReflexAction action;
    private int priority;

    @FunctionalInterface
    public interface ReflexAction {
        void execute(VirtualTeamMember member, CaseContext context);
    }

    public static ReflexRule speechRule(String id, Duty duty, String condition,
                                        String speechText, int priority) {
        return new ReflexRule(id, duty, condition,
            (member, ctx) -> {
                member.getTtsService().synthesizeAndSend(null,
                    member.getFullName() + "：" + speechText,"zf_005",1.2f);
            },
            priority);
    }

    public static ReflexRule actionRule(String id, Duty duty, String condition,
                                        String speechText, String vrAnimation, int priority) {
        return new ReflexRule(id, duty, condition,
            (member, ctx) -> {
                member.getTtsService().synthesizeAndSend(null,
                    member.getFullName() + "：" + speechText,"zf_005",1.2f);
                member.getVrClientService().sendAnimation(
                    member.getSessionId(),        // ★ 使用 member.getSessionId()
                    vrAnimation, 2);
            },
            priority);
    }
}

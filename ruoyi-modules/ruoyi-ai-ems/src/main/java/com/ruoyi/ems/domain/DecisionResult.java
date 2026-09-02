package com.ruoyi.ems.domain;
import com.ruoyi.ems.action.SystemAction;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DecisionResult {
    private String fromState;
    private String toState;
    private String eventCode;
    private boolean accepted;
    private String rejectionReason;
    private boolean isBlocked;
    private String blockedReason;
    private CaseContext context;
    private List<SystemAction> actions;
    private boolean isGlobalEvent;
    private String globalEventCode;

    // 静态工厂方法（供 IntentOrchestrator 调用）
    public static DecisionResult success(String from, String to, String event,
                                         CaseContext ctx, List<SystemAction> actions) {
        return DecisionResult.builder()
            .fromState(from)
            .toState(to)
            .eventCode(event)
            .accepted(true)
            .context(ctx)
            .actions(actions)
            .build();
    }

    public static DecisionResult rejected(String from, String reason, CaseContext ctx) {
        return DecisionResult.builder()
            .fromState(from)
            .accepted(false)
            .rejectionReason(reason)
            .context(ctx)
            .build();
    }

    public static DecisionResult globalEvent(String from, String eventCode, CaseContext ctx) {
        return DecisionResult.builder()
            .fromState(from)
            .toState(from)
            .eventCode(eventCode)
            .accepted(true)
            .isGlobalEvent(true)
            .globalEventCode(eventCode)
            .context(ctx)
            .build();
    }
}

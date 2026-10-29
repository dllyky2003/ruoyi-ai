package com.ruoyi.ems.event;

import com.ruoyi.ems.npc.TeamMember;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskCompletedEvent extends ApplicationEvent {

    private final String sessionId;
    private final TeamMember member;
    private final boolean success;
    private final String report;

    /** ★ 方案 A：触发本次任务的意图编码（如 S-101-2-2） */
    private final String intentCode;

    /** 兼容旧调用点：不带 intentCode。 */
    public TaskCompletedEvent(String sessionId, TeamMember member,
                              boolean success, String report) {
        this(sessionId, member, success, report, null);
    }

    /** 新签名：带 intentCode。 */
    public TaskCompletedEvent(String sessionId, TeamMember member,
                              boolean success, String report, String intentCode) {
        super(member);
        this.sessionId = sessionId;
        this.member = member;
        this.success = success;
        this.report = report;
        this.intentCode = intentCode;
    }
}

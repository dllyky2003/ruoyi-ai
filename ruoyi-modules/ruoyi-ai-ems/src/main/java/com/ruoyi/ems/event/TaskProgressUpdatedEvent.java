package com.ruoyi.ems.event;


import com.ruoyi.ems.npc.TeamMember;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskProgressUpdatedEvent extends ApplicationEvent {
    private final String sessionId;
    private final TeamMember member;
    private final double progress; // 0.0 ~ 1.0

    public TaskProgressUpdatedEvent(Object source, String sessionId, TeamMember member, double progress) {
        super(source);
        this.sessionId = sessionId;
        this.member = member;
        this.progress = progress;
    }
}

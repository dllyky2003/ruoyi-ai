package com.ruoyi.ems.event;

import com.ruoyi.ems.npc.TeamMember;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskStartedEvent extends ApplicationEvent {
    private final String sessionId;
    private final TeamMember member;
    private final String task;
    private final int durationSeconds;

    public TaskStartedEvent(Object source, String sessionId, TeamMember member, String task, int durationSeconds) {
        super(source);
        this.sessionId = sessionId;
        this.member = member;
        this.task = task;
        this.durationSeconds = durationSeconds;
    }
}

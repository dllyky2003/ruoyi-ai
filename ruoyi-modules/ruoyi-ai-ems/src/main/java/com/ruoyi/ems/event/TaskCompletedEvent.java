package com.ruoyi.ems.event;

import com.ruoyi.ems.npc.TeamMember;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 团队成员任务完成事件
 * 当虚拟团队成员完成任务或超时时发布
 */
@Getter
public class TaskCompletedEvent extends ApplicationEvent {

    /** 会话ID */
    private final String sessionId;

    /** 完成任务/超时的成员 */
    private final TeamMember member;

    /** 是否成功完成 */
    private final boolean success;

    /** 汇报文本 */
    private final String report;

    public TaskCompletedEvent(String sessionId, TeamMember member, boolean success, String report) {
        super(member);
        this.sessionId = sessionId;
        this.member = member;
        this.success = success;
        this.report = report;
    }
}

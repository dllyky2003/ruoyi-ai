package com.ruoyi.ems.domain.dto;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 团队状态推送消息（通过 WebSocket 发送给前端）
 */
@Data
@Builder
public class TeamStatePushMessage {

    /** 消息类型 */
    private String type;  // "team_state_full" | "team_member_update" | "team_report"

    /** 会话ID */
    private String sessionId;

    /** 完整团队状态（type=team_state_full 时使用） */
    private TeamState teamState;

    /** 成员更新（type=team_member_update 时使用） */
    private MemberUpdate memberUpdate;

    /** NPC汇报（type=team_report 时使用） */
    private TeamReport report;

    /** 时间戳 */
    private long timestamp;

    @Data
    @Builder
    public static class TeamState {
        private String templateId;
        private List<MemberState> members;
        private String summary;
    }

    @Data
    @Builder
    public static class MemberState {
        private String id;
        private String name;
        private String title;
        private List<String> duties;
        private String status;          // IDLE, EXECUTING, COMPLETED, FAILED
        private String statusDisplay;    // 空闲, 执行中, 已完成, 失败
        private String currentTask;
        private double progress;         // 0.0 ~ 1.0
        private boolean onDuty;
    }

    @Data
    @Builder
    public static class MemberUpdate {
        private String memberId;
        private String name;
        private String statusDisplay;
        private String currentTask;
        private Double progress;
        private String updateType;      // "started" | "progress" | "completed" | "failed"
    }

    @Data
    @Builder
    public static class TeamReport {
        private String memberName;
        private String memberTitle;
        private String content;
        private boolean success;
        private String reportType;      // "task_completed" | "task_failed" | "status_change"
    }
}

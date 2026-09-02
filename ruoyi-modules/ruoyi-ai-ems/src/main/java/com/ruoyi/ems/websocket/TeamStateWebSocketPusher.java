package com.ruoyi.ems.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.domain.dto.TeamStatePushMessage;
import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.TeamMember;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 团队状态 WebSocket 推送器
 * Phase 5: 实时推送团队状态变化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamStateWebSocketPusher {

    private final TeamManager teamManager;
    private final ObjectMapper objectMapper;

    /** 存储每个会话的 WebSocket Session */
    private final Map<String, Session> webSocketSessions = new ConcurrentHashMap<>();

    /**
     * 注册 WebSocket 会话
     */
    public void registerSession(String sessionId, Session webSocketSession) {
        webSocketSessions.put(sessionId, webSocketSession);
        // 注册后立即推送完整团队状态
        pushFullTeamState(sessionId);
    }

    /**
     * 注销 WebSocket 会话
     */
    public void unregisterSession(String sessionId) {
        webSocketSessions.remove(sessionId);
    }

    /**
     * 推送完整团队状态（首次连接或刷新时使用）
     */
    private void pushFullTeamState(String sessionId) {
        Session wsSession = webSocketSessions.get(sessionId);
        if (wsSession == null || !wsSession.isOpen()) return;

        try {
            Map<String, Object> teamState = teamManager.getTeamState(sessionId);
            String summary = teamManager.getTeamStatusSummary(sessionId);

            TeamStatePushMessage.TeamState state = TeamStatePushMessage.TeamState.builder()
                .templateId("3MAN") // 可从Redis获取
                .summary(summary)
                .build();

            // 构建成员列表
            List<TeamMember> members = teamManager.getTeamMembers(sessionId);
            state.setMembers(members.stream().map(m -> {
                return TeamStatePushMessage.MemberState.builder()
                    .id(m.getId())
                    .name(m.getName())
                    .title(m.getTitle())
                    .duties(m.getDuties().stream()
                        .map(d -> d.getDisplayName())
                        .collect(java.util.stream.Collectors.toList()))
                    .status(m.getStatus().name())
                    .statusDisplay(m.getStatus().getDisplayName())
                    .currentTask(m.getCurrentTask())
                    .progress(m.getTaskProgress())
                    .onDuty(m.isOnDuty())
                    .build();
            }).collect(java.util.stream.Collectors.toList()));

            TeamStatePushMessage message = TeamStatePushMessage.builder()
                .type("team_state_full")
                .sessionId(sessionId)
                .teamState(state)
                .timestamp(System.currentTimeMillis())
                .build();

            sendMessage(wsSession, message);

        } catch (Exception e) {
            log.warn("推送团队状态失败: {}", e.getMessage());
        }
    }

    /**
     * 监听任务完成事件，推送成员更新
     */
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        String sessionId = event.getSessionId();
        Session wsSession = webSocketSessions.get(sessionId);
        if (wsSession == null || !wsSession.isOpen()) return;

        TeamMember member = event.getMember();

        // 构建成员更新消息
        TeamStatePushMessage.MemberUpdate update = TeamStatePushMessage.MemberUpdate.builder()
            .memberId(member.getId())
            .name(member.getName())
            .statusDisplay(member.getStatus().getDisplayName())
            .currentTask(member.getCurrentTask())
            .progress(event.isSuccess() ? 1.0 : member.getTaskProgress())
            .updateType(event.isSuccess() ? "completed" : "failed")
            .build();

        TeamStatePushMessage message = TeamStatePushMessage.builder()
            .type("team_member_update")
            .sessionId(sessionId)
            .memberUpdate(update)
            .timestamp(System.currentTimeMillis())
            .build();

        sendMessage(wsSession, message);

        // 如果有汇报内容，单独推送汇报消息
        if (event.getReport() != null) {
            TeamStatePushMessage.TeamReport report = TeamStatePushMessage.TeamReport.builder()
                .memberName(member.getName())
                .memberTitle(member.getTitle())
                .content(event.getReport())
                .success(event.isSuccess())
                .reportType(event.isSuccess() ? "task_completed" : "task_failed")
                .build();

            TeamStatePushMessage reportMessage = TeamStatePushMessage.builder()
                .type("team_report")
                .sessionId(sessionId)
                .report(report)
                .timestamp(System.currentTimeMillis())
                .build();

            sendMessage(wsSession, reportMessage);
        }
    }

    /**
     * 定时推送团队成员进度更新（每秒）
     */
    @Scheduled(fixedRate = 1000)
    public void pushProgressUpdates() {
        for (Map.Entry<String, Session> entry : webSocketSessions.entrySet()) {
            String sessionId = entry.getKey();
            Session wsSession = entry.getValue();
            if (wsSession == null || !wsSession.isOpen()) continue;

            try {
                List<TeamMember> members = teamManager.getTeamMembers(sessionId);
                for (TeamMember member : members) {
                    if (member.isExecuting()) {
                        // 推送进度更新
                        TeamStatePushMessage.MemberUpdate update = TeamStatePushMessage.MemberUpdate.builder()
                            .memberId(member.getId())
                            .name(member.getName())
                            .statusDisplay(member.getStatus().getDisplayName())
                            .currentTask(member.getCurrentTask())
                            .progress(member.getTaskProgress())
                            .updateType("progress")
                            .build();

                        TeamStatePushMessage message = TeamStatePushMessage.builder()
                            .type("team_member_update")
                            .sessionId(sessionId)
                            .memberUpdate(update)
                            .timestamp(System.currentTimeMillis())
                            .build();

                        sendMessage(wsSession, message);
                    }
                }
            } catch (Exception e) {
                log.debug("推送进度更新失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 发送 WebSocket 消息
     */
    private void sendMessage(Session session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.getBasicRemote().sendText(json);
        } catch (IOException e) {
            log.warn("发送 WebSocket 消息失败: {}", e.getMessage());
        }
    }
}

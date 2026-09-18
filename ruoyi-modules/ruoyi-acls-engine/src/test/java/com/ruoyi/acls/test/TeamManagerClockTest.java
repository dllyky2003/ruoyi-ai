package com.ruoyi.acls.test;

import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.TeamSize;
import com.ruoyi.ems.room.event.ClockPausedEvent;
import com.ruoyi.ems.room.event.ClockResumedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TeamManagerClockTest {

    private TeamManager teamManager;
    private static final String SID = "s-001";

    @BeforeEach
    void setup() {
        teamManager = new TeamManager();
        ReflectionTestUtils.setField(teamManager, "eventPublisher", mock(ApplicationEventPublisher.class));
        teamManager.initTemplates();
        teamManager.initializeTeam(SID, TeamSize.TEAM_3MAN);
    }

    private TeamMember firstNpc() {
        return teamManager.getTeamMembers(SID).stream()
            .filter(m -> !m.getDuties().contains(Duty.TEAM_LEADER))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void pauseFreezesExecutingMembers() {
        TeamMember m = firstNpc();
        m.startTask("cpr_compressions", 60);

        teamManager.onClockPaused(new ClockPausedEvent(SID, System.currentTimeMillis()));

        assertNotNull(m.getPausedAt(), "执行中的成员应被冻结");
    }

    @Test
    void pauseSkipsIdleMembers() {
        // 所有成员 IDLE，不执行任务
        teamManager.onClockPaused(new ClockPausedEvent(SID, System.currentTimeMillis()));

        for (TeamMember m : teamManager.getTeamMembers(SID)) {
            assertNull(m.getPausedAt(), "空闲成员不应被冻结");
        }
    }

    @Test
    void pauseIsIdempotent() {
        TeamMember m = firstNpc();
        m.startTask("task", 30);

        teamManager.onClockPaused(new ClockPausedEvent(SID, System.currentTimeMillis()));
        LocalDateTime firstPaused = m.getPausedAt();

        teamManager.onClockPaused(new ClockPausedEvent(SID, System.currentTimeMillis()));
        assertEquals(firstPaused, m.getPausedAt(), "重复暂停不应重设 pausedAt");
    }

    @Test
    void resumeCompensatesTaskStartTime() {
        TeamMember m = firstNpc();
        m.startTask("task", 60);
        LocalDateTime originalStart = m.getTaskStartTime();

        teamManager.onClockPaused(new ClockPausedEvent(SID, System.currentTimeMillis()));
        teamManager.onClockResumed(new ClockResumedEvent(SID,
            System.currentTimeMillis(), 5000L));   // 暂停 5 秒

        assertNull(m.getPausedAt(), "恢复后 pausedAt 应清空");
        assertNotNull(m.getTaskStartTime());
        assertEquals(originalStart.plusSeconds(5), m.getTaskStartTime(),
            "taskStartTime 应后推 5 秒");
    }

    @Test
    void resumeWithoutPauseIsNoop() {
        TeamMember m = firstNpc();
        m.startTask("task", 60);
        LocalDateTime originalStart = m.getTaskStartTime();

        // 没暂停，直接恢复
        teamManager.onClockResumed(new ClockResumedEvent(SID, System.currentTimeMillis(), 5000L));

        assertEquals(originalStart, m.getTaskStartTime(), "未暂停时不应补偿");
    }

    @Test
    void resumeSkipsZeroPauseDuration() {
        TeamMember m = firstNpc();
        m.startTask("task", 60);
        LocalDateTime originalStart = m.getTaskStartTime();

        teamManager.onClockPaused(new ClockPausedEvent(SID, System.currentTimeMillis()));
        teamManager.onClockResumed(new ClockResumedEvent(SID,
            System.currentTimeMillis(), 500L));    // 不足 1 秒

        assertEquals(originalStart, m.getTaskStartTime(), "秒级补偿，<1s 不调整");
    }

    @Test
    void unknownSessionIsNoop() {
        // 不抛异常即可
        teamManager.onClockPaused(new ClockPausedEvent("no-such", System.currentTimeMillis()));
        teamManager.onClockResumed(new ClockResumedEvent("no-such", System.currentTimeMillis(), 1000L));
    }

    @Test
    void leaderNotMarkedOnDuty() {
        List<TeamMember> members = teamManager.getTeamMembers(SID);
        TeamMember leader = members.stream()
            .filter(m -> m.getDuties().contains(Duty.TEAM_LEADER))
            .findFirst()
            .orElseThrow();

        assertFalse(leader.isOnDuty(), "组长不应参与 NPC 路由");
    }
}

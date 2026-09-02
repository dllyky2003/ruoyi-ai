package com.ruoyi.ems.npc;

import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.event.TaskProgressUpdatedEvent;
import com.ruoyi.ems.event.TaskStartedEvent;
import com.ruoyi.ems.orchestrator.model.ActionChainRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TeamManager {

    private final Map<String, TeamConfigInstance> sessionTeamMap = new ConcurrentHashMap<>();
    private final Map<String, TeamTemplate> templateCache = new ConcurrentHashMap<>();
    private final Map<String, ActionChainRuntime> chainRuntimes = new ConcurrentHashMap<>();
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final List<Consumer<TaskCompletedEvent>> taskCompletionListeners = new ArrayList<>();

    @PostConstruct
    public void initTemplates() {
        templateCache.put("3MAN", TeamTemplate.china3Man());
        templateCache.put("4MAN", TeamTemplate.china4Man());
        templateCache.put("5MAN", TeamTemplate.china5Man());
        log.info("📋 已加载团队模板: {}", templateCache.keySet());
    }

    public void addTaskCompletionListener(Consumer<TaskCompletedEvent> listener) {
        taskCompletionListeners.add(listener);
    }

    public synchronized void initializeTeam(String sessionId, String templateId) {
        TeamTemplate template = templateCache.get(templateId);
        if (template == null) {
            log.warn("未知模板: {}，使用默认三人组", templateId);
            template = TeamTemplate.china3Man();
        }
        List<TeamMember> members = new ArrayList<>();
        for (MemberSlot slot : template.getSlots()) {
            TeamMember member = TeamMember.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .name(slot.getName())
                .title(slot.getTitle())
                .duties(slot.getDuties())
                .status(Status.IDLE)
                .onDuty(true)
                .skillLevel(0.85 + Math.random() * 0.1)
                .build();
            members.add(member);
        }
        TeamConfigInstance instance = new TeamConfigInstance(templateId, members);
        sessionTeamMap.put(sessionId, instance);
        log.info("🏥 虚拟团队已组建（{}人{}）：{}", members.size(), templateId,
            members.stream().map(TeamMember::getTitleName).collect(Collectors.joining("、")));
    }

    private TeamConfigInstance getOrCreate(String sessionId) {
        TeamConfigInstance instance = sessionTeamMap.get(sessionId);
        if (instance == null) {
            initializeTeam(sessionId, "3MAN");
            instance = sessionTeamMap.get(sessionId);
        }
        return instance;
    }

    public List<TeamMember> getTeamMembers(String sessionId) {
        TeamConfigInstance instance = getOrCreate(sessionId);
        return new ArrayList<>(instance.members);
    }

    public Optional<TeamMember> findMemberByName(String sessionId, String name) {
        TeamConfigInstance instance = getOrCreate(sessionId);
        return instance.members.stream()
            .filter(m -> m.getName().equals(name))
            .findFirst();
    }

    public Optional<TeamMember> findIdleMemberForDuty(String sessionId, Duty duty) {
        TeamConfigInstance instance = getOrCreate(sessionId);
        return instance.members.stream()
            .filter(m -> m.isOnDuty() && (m.isIdle() || m.getStatus() == Status.COMPLETED))
            .filter(m -> m.getDuties().contains(duty))
            .findFirst();
    }

    public boolean assignTaskForDuty(String sessionId, Duty duty, String task, int durationSeconds) {
        Optional<TeamMember> opt = findIdleMemberForDuty(sessionId, duty);
        if (opt.isEmpty()) {
            log.warn("未找到能执行 {} 的空闲成员", duty.getDisplayName());
            return false;
        }
        TeamMember member = opt.get();
        member.startTask(task, durationSeconds);
        eventPublisher.publishEvent(new TaskStartedEvent(this, sessionId, member, task, durationSeconds));
        log.info("📋 {}（{}）分配任务: {}（预计{}秒）",
            member.getTitle(), member.getName(), task, durationSeconds);
        return true;
    }

    public boolean assignTaskToMember(String sessionId, String memberId, String task, int durationSeconds) {
        TeamConfigInstance instance = getOrCreate(sessionId);
        TeamMember member = instance.members.stream()
            .filter(m -> m.getId().equals(memberId))
            .findFirst()
            .orElse(null);
        if (member == null || !member.isIdle()) {
            return false;
        }
        member.startTask(task, durationSeconds);
        log.info("📋 {}（{}）分配任务: {}（预计{}秒）",
            member.getTitle(), member.getName(), task, durationSeconds);
        return true;
    }

    public synchronized void updateStatus(String sessionId) {
        TeamConfigInstance instance = sessionTeamMap.get(sessionId);
        if (instance == null) return;

        List<TaskCompletedEvent> completedEvents = new ArrayList<>();

        for (TeamMember member : instance.members) {
            if (!member.isExecuting()) continue;

            double progress = member.getTaskProgress();
            eventPublisher.publishEvent(new TaskProgressUpdatedEvent(this, sessionId, member, progress));

            if (member.isTaskTimeout()) {
                member.failTask();
                TaskCompletedEvent event = new TaskCompletedEvent(sessionId, member, false,
                    "⏰ " + member.getFullName() + " " + member.getCurrentTask() + " 超时未完成，请重新分配");
                completedEvents.add(event);
                continue;
            }

            if (progress >= 1.0 && member.isExecuting()) {
                member.completeTask();
                log.info("✅ {} 完成任务: {}", member.getName(), member.getCurrentTask());
                TaskCompletedEvent event = new TaskCompletedEvent(sessionId, member, true, generateReport(member));
                completedEvents.add(event);
            }
        }

        for (TaskCompletedEvent event : completedEvents) {
            onTaskCompleted(event);
        }
    }

    private void onTaskCompleted(TaskCompletedEvent event) {
        eventPublisher.publishEvent(event);
        for (Consumer<TaskCompletedEvent> listener : taskCompletionListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("任务完成监听器执行失败", e);
            }
        }
        log.info("📢 {}汇报: {}", event.getMember().getName(), event.getReport());
    }

    public String generateReport(TeamMember member) {
        if (member.getStatus() == Status.COMPLETED) {
            return String.format("✅ %s（%s）报告：%s 已完成",
                member.getTitle(), member.getName(),
                member.getCurrentTask() != null ? member.getCurrentTask() : "任务");
        } else if (member.getStatus() == Status.FAILED) {
            return String.format("❌ %s（%s）报告：%s 执行失败，请重新分配",
                member.getTitle(), member.getName(),
                member.getCurrentTask() != null ? member.getCurrentTask() : "任务");
        }
        return String.format("💬 %s（%s）：%s",
            member.getTitle(), member.getName(),
            member.getStatus().getDisplayName());
    }

    public String getTeamStatusSummary(String sessionId) {
        TeamConfigInstance instance = sessionTeamMap.get(sessionId);
        if (instance == null) return "虚拟团队尚未组建";

        return instance.members.stream()
            .map(m -> {
                if (m.isIdle()) return String.format("%s（%s）：空闲", m.getTitle(), m.getName());
                if (m.isExecuting()) return String.format("%s（%s）：⏳ %s %d%%",
                    m.getTitle(), m.getName(), m.getCurrentTask(), (int)(m.getTaskProgress() * 100));
                if (m.getStatus() == Status.COMPLETED) {
                    return String.format("%s（%s）：✅ 已完成 %s",
                        m.getTitle(), m.getName(), m.getCurrentTask());
                }
                if (m.getStatus() == Status.FAILED) {
                    return String.format("%s（%s）：❌ 任务失败，需重试",
                        m.getTitle(), m.getName());
                }
                return String.format("%s（%s）：%s", m.getTitle(), m.getName(), m.getStatus().getDisplayName());
            })
            .collect(Collectors.joining("；"));
    }

    public Map<String, Object> getTeamState(String sessionId) {
        TeamConfigInstance instance = sessionTeamMap.get(sessionId);
        if (instance == null) return Map.of();

        Map<String, Object> result = new LinkedHashMap<>();
        for (TeamMember member : instance.members) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("id", member.getId());
            state.put("name", member.getName());
            state.put("title", member.getTitle());
            state.put("duties", member.getDuties().stream()
                .map(Duty::getDisplayName)
                .collect(Collectors.toList()));
            state.put("status", member.getStatus().name());
            state.put("statusDisplay", member.getStatus().getDisplayName());
            state.put("currentTask", member.getCurrentTask());
            state.put("progress", member.getTaskProgress());
            state.put("isOnDuty", member.isOnDuty());
            result.put(member.getId(), state);
        }
        return result;
    }

    public void resetTeam(String sessionId) {
        TeamConfigInstance instance = sessionTeamMap.get(sessionId);
        if (instance != null) {
            instance.members.forEach(TeamMember::resetToIdle);
            log.info("🔄 团队状态已重置: {}", sessionId);
        }
    }

    public void clearTeam(String sessionId) {
        sessionTeamMap.remove(sessionId);
        chainRuntimes.remove(sessionId);
        log.info("🗑️ 团队已清理: {}", sessionId);
    }

    public ActionChainRuntime getOrCreateChain(String sessionId, String intentCode, List<String> stepIds) {
        return chainRuntimes.computeIfAbsent(sessionId,
            k -> new ActionChainRuntime(intentCode, stepIds));
    }

    public ActionChainRuntime getChain(String sessionId) {
        return chainRuntimes.get(sessionId);
    }

    public void removeChain(String sessionId) {
        chainRuntimes.remove(sessionId);
    }

    private record TeamConfigInstance(String templateId, List<TeamMember> members) {
    }
}

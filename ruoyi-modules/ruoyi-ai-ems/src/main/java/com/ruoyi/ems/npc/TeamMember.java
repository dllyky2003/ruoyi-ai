package com.ruoyi.ems.npc;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟团队成员模型
 * 可承担一个或多个 ACLS 职责，支持动态任务状态管理
 */
@Data
@Builder
public class TeamMember {

    // ==================== 核心标识 ====================
    private String id;
    private String name;
    private String title;
    private Set<Duty> duties;

    // ==================== 工作状态 ====================
    private Status status;
    private String currentTask;
    private LocalDateTime taskStartTime;
    private Integer taskDuration;
    private double skillLevel;
    private boolean onDuty;
    @Builder.Default
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    // ==================== ★ 新增：进度字段（由 VirtualTeamMember 维护） ====================
    /** 任务进度 0.0~1.0，由虚拟组员独立更新，不持久化 */
    @Builder.Default
    private transient double taskProgress = 0.0;

    // ==================== 原有状态管理方法 ====================

    public boolean isIdle() {
        return status == Status.IDLE;
    }

    public boolean isExecuting() {
        return status == Status.EXECUTING;
    }

    public void startTask(String task, int durationSeconds) {
        this.currentTask = task;
        this.taskStartTime = LocalDateTime.now();
        this.taskDuration = durationSeconds;
        this.status = Status.EXECUTING;
        this.taskProgress = 0.0; // 重置进度
    }

    public void completeTask() {
        this.status = Status.COMPLETED;
        this.taskProgress = 1.0;
    }

    public void failTask() {
        this.status = Status.FAILED;
    }

    public void resetToIdle() {
        this.status = Status.IDLE;
        this.currentTask = null;
        this.taskStartTime = null;
        this.taskDuration = null;
        this.taskProgress = 0.0;
    }

    public boolean isTaskTimeout() {
        if (status != Status.EXECUTING || taskDuration == null || taskStartTime == null) {
            return false;
        }
        long elapsed = Duration.between(taskStartTime, LocalDateTime.now()).getSeconds();
        return elapsed > taskDuration + 10;
    }

    /**
     * ★ 修改：返回 taskProgress 字段，而非实时计算
     */
    public double getTaskProgress() {
        return taskProgress;
    }

    /**
     * ★ 新增：设置进度（由虚拟组员调用）
     */
    public void setTaskProgress(double progress) {
        this.taskProgress = Math.min(1.0, Math.max(0.0, progress));
    }

    public String getStatusDescription() {
        String base = title + name;
        if (status == Status.IDLE) {
            return base + "：空闲待命";
        }
        if (status == Status.EXECUTING) {
            int progress = (int) (getTaskProgress() * 100);
            return base + "：正在" + currentTask + "（" + progress + "%）";
        }
        if (status == Status.COMPLETED) {
            return base + "：已完成" + currentTask;
        }
        if (status == Status.FAILED) {
            return base + "：任务失败，需重试";
        }
        return base + "：" + status.getDisplayName();
    }

    public String getFullName() {
        return title + name;
    }

    public String getTitleName() {
        return title + name;
    }

    public boolean hasDuty(Duty duty) {
        return duties != null && duties.contains(duty);
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> defaultAttributes() {
        return new ConcurrentHashMap<>();
    }
}

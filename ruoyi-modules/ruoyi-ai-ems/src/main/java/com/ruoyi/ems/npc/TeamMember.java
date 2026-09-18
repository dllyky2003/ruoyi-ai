package com.ruoyi.ems.npc;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
@Builder
public class TeamMember {

    // ==================== 核心标识 ====================
    private String id;
    private String name;
    private String title;
    private List<Duty> duties;

    // ==================== 颜色与音色 ====================
    @Builder.Default
    private String color = "#9E9E9E";
    @Builder.Default
    private String ttsVoice = "zm_098";

    // ==================== 工作状态 ====================
    private Status status;
    private String currentTask;
    private LocalDateTime taskStartTime;
    private Integer taskDuration;
    private double skillLevel;
    private boolean onDuty;
    @Builder.Default
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    // ==================== 进度与附加信息 ====================
    @Builder.Default
    private transient double taskProgress = 0.0;

    /**
     * 任务被中断的原因（取消/抢占）
     */
    private transient String interruptedReason;

    /**
     * 任务失败的详细原因
     */
    private transient String failReason;

    /**
     * 任务暂停时的墙上时间。
     * <p>
     * 暂停期间冻结，恢复时用于计算补偿量，把 taskStartTime 往后推。
     * 仅 transient，不参与 Redis 序列化。
     * </p>
     */
    private transient LocalDateTime pausedAt;
    /**
     * NPC 进度下行节流用：上次已推送的进度档位（5% 一档，0~20）。
     * -1 表示尚未推送过。
     * transient，不参与 Redis 序列化。
     */
    @Builder.Default
    private transient int lastPushedBucket = -1;
    // ==================== 状态管理方法 ====================

    public boolean isIdle() {
        return status == Status.IDLE;
    }

    public boolean isExecuting() {
        return status == Status.EXECUTING;
    }

    public boolean isAvailable() {
        return status == Status.IDLE || status == Status.COMPLETED;
    }

    /**
     * 启动任务（仅当空闲或已完成时）
     *
     * @return true 表示成功启动，false 表示当前正在执行任务（需使用 forceStartTask）
     */
    public boolean startTask(String task, int durationSeconds) {
        if (status == Status.EXECUTING) {
            log.warn("{} 正在执行任务 {}，无法启动新任务 {}", name, currentTask, task);
            return false;
        }
        doStartTask(task, durationSeconds, null);
        return true;
    }

    /**
     * 强制启动任务（抢占模式）
     * <p>
     * 如果当前正在执行任务，旧任务会被标记为 INTERRUPTED（实质为 CANCELLED），
     * 然后立即启动新任务。
     *
     * @param interruptReason 中断原因（如 "OVERRIDE" / "NEW_COMMAND"）
     */
    public void forceStartTask(String task, int durationSeconds, String interruptReason) {
        if (status == Status.EXECUTING) {
            log.info("⚡ {} 被抢占: {} (原因: {})", name, currentTask, interruptReason);
            this.status = Status.CANCELLED;
            this.interruptedReason = interruptReason;
            this.taskProgress = 0.0;
        }
        doStartTask(task, durationSeconds, null);
    }

    /**
     * 内部方法：实际启动任务
     */
    private void doStartTask(String task, int durationSeconds, String failReason) {
        this.currentTask = task;
        this.taskStartTime = LocalDateTime.now();
        this.taskDuration = durationSeconds;
        this.status = Status.EXECUTING;
        this.taskProgress = 0.0;
        this.failReason = null;
        this.interruptedReason = null;
        this.pausedAt = null;
        log.debug("▶️ {} 开始任务: {} (预计 {} 秒)", name, task, durationSeconds);
    }

    /**
     * 完成当前任务
     */
    public void completeTask() {
        if (status == Status.EXECUTING) {
            this.status = Status.COMPLETED;
            this.taskProgress = 1.0;
            log.info("✅ {} 完成任务: {}", name, currentTask);
        } else {
            log.warn("{} 不在执行状态，无法完成 (当前状态: {})", name, status);
        }
    }

    /**
     * 标记任务失败
     *
     * @param reason 失败原因
     */
    public void failTask(String reason) {
        if (status == Status.EXECUTING) {
            this.status = Status.FAILED;
            this.failReason = reason;
            this.taskProgress = 0.0;
            log.error("❌ {} 任务失败: {} ({})", name, currentTask, reason);
        } else {
            log.warn("{} 不在执行状态，无法标记失败 (当前状态: {})", name, status);
        }
    }

    /**
     * 无参失败（兼容旧接口）
     */
    public void failTask() {
        failTask("执行异常");
    }

    /**
     * ★ 取消当前任务（组长主动取消）
     *
     * @param reason 取消原因
     */
    public void cancelTask(String reason) {
        if (status == Status.EXECUTING) {
            this.status = Status.CANCELLED;
            this.interruptedReason = reason;
            this.taskProgress = 0.0;
            log.info("⏹️ {} 任务被取消: {} ({})", name, currentTask, reason);
        } else {
            log.warn("{} 没有正在执行的任务可取消 (当前状态: {})", name, status);
        }
    }

    /**
     * 无参取消（兼容简化调用）
     */
    public void cancelTask() {
        cancelTask("组长手动取消");
    }

    /**
     * 恢复任务（从失败/取消状态回到空闲，准备重试）
     * <p>
     * 注意：恢复不等于自动重新执行，只是将状态复位到 IDLE，
     * 由上层调用方决定是否重新 startTask。
     */
    public void recover() {
        if (status == Status.FAILED || status == Status.CANCELLED) {
            log.info("♻️ {} 从 {} 状态恢复", name, status);
            this.status = Status.IDLE;
            this.currentTask = null;
            this.taskStartTime = null;
            this.taskDuration = null;
            this.taskProgress = 0.0;
            this.failReason = null;
            this.interruptedReason = null;
            this.pausedAt = null;
        } else {
            log.warn("{} 当前状态 {} 不支持恢复（仅支持 FAILED/CANCELLED）", name, status);
        }
    }

    /**
     * 重置到空闲（清除所有状态）
     */
    public void resetToIdle() {
        this.status = Status.IDLE;
        this.currentTask = null;
        this.taskStartTime = null;
        this.taskDuration = null;
        this.taskProgress = 0.0;
        this.failReason = null;
        this.interruptedReason = null;
        this.pausedAt = null;
        log.debug("🔄 {} 已重置为空闲", name);
    }

    /**
     * 判断任务是否超时
     */
    public boolean isTaskTimeout() {
        if (status != Status.EXECUTING || taskDuration == null || taskStartTime == null) {
            return false;
        }
        long elapsed = Duration.between(taskStartTime, LocalDateTime.now()).getSeconds();
        // 允许 10 秒缓冲
        return elapsed > taskDuration + 10;
    }

    /**
     * 获取任务已执行秒数
     */
    public long getTaskElapsedSeconds() {
        if (taskStartTime == null) return 0;
        return Duration.between(taskStartTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * ★ 时钟心跳推进任务进度。
     * <p>
     * 基于 taskStartTime 计算 elapsed，按 taskDuration 归一化到 [0, 1]。
     * 与 isTaskTimeout() 互补：tickProgress 负责推进进度，isTaskTimeout 负责兜底超时。
     * <p>
     * 由 ClockService 每秒调用 TeamManager.updateStatus() 间接触发。
     */
    public void tickProgress() {
        if (status != Status.EXECUTING || taskStartTime == null || taskDuration == null) {
            return;
        }
        if (taskDuration <= 0) {
            // duration 为 0 或负数，视作瞬时完成
            this.taskProgress = 1.0;
            return;
        }
        long elapsed = getTaskElapsedSeconds();
        double progress = (double) elapsed / taskDuration;
        this.taskProgress = Math.min(1.0, Math.max(0.0, progress));
    }

    public double getTaskProgress() {
        return taskProgress;
    }

    public void setTaskProgress(double progress) {
        this.taskProgress = Math.min(1.0, Math.max(0.0, progress));
    }

    // ==================== 状态展示 ====================

    public String getStatusDescription() {
        switch (status) {
            case IDLE:
                return name + "：空闲待命";
            case EXECUTING:
                int progress = (int) (getTaskProgress() * 100);
                return name + "：正在" + currentTask + "（" + progress + "%）";
            case COMPLETED:
                return name + "：已完成" + currentTask;
            case FAILED:
                return name + "：任务失败" + (failReason != null ? "（" + failReason + "）" : "");
            case CANCELLED:
                return name + "：任务已取消" + (interruptedReason != null ? "（" + interruptedReason + "）" : "");
            default:
                return name + "：" + status.getDisplayName();
        }
    }

    public String getFullName() {
        return name;
    }

    public String getTitleName() {
        return name;
    }

    public boolean hasDuty(Duty duty) {
        return duties != null && duties.contains(duty);
    }
}

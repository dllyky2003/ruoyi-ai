package com.ruoyi.ems.event;


import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 状态机状态转移事件
 *
 * 当状态机完成一次状态转移后发布，用于：
 * 1. 记录状态变更历史（审计日志）
 * 2. 触发系统动作（如通知模拟器、推送前端）
 * 3. 更新监控指标
 * 4. 驱动计时器管理
 *
 * 该事件是不可变对象，包含状态转移的完整上下文快照。
 * 监听器应通过 ContextManager 获取最新的上下文，而非依赖事件中的上下文引用。
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@Getter
@ToString
public class StateTransitionEvent {

    /**
     * 案例实例ID
     */
    private final String caseInstanceId;

    /**
     * 源状态
     */
    private final String fromState;

    /**
     * 目标状态
     */
    private final String toState;

    /**
     * 触发转移的事件编码
     */
    private final String eventCode;

    /**
     * 关联的意图编码（如有）
     */
    private final String intentCode;

    /**
     * 转移是否成功
     */
    private final boolean success;

    /**
     * 拒绝原因（success=false 时有效）
     */
    private final String rejectionReason;

    /**
     * 时间戳
     */
    private final Instant timestamp;

    /**
     * 上下文关键变量快照（用于日志和审计，只读）
     */
    private final Map<String, Object> contextSnapshot;

    // ==================== 构造器 ====================

    private StateTransitionEvent(Builder builder) {
        this.caseInstanceId = builder.caseInstanceId;
        this.fromState = builder.fromState;
        this.toState = builder.toState;
        this.eventCode = builder.eventCode;
        this.intentCode = builder.intentCode;
        this.success = builder.success;
        this.rejectionReason = builder.rejectionReason;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.contextSnapshot = builder.contextSnapshot != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.contextSnapshot))
            : Collections.emptyMap();
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建成功转移事件
     */
    public static StateTransitionEvent success(String caseInstanceId,
                                               String fromState,
                                               String toState,
                                               String eventCode,
                                               String intentCode,
                                               Map<String, Object> snapshot) {
        return builder()
            .caseInstanceId(caseInstanceId)
            .fromState(fromState)
            .toState(toState)
            .eventCode(eventCode)
            .intentCode(intentCode)
            .success(true)
            .contextSnapshot(snapshot)
            .build();
    }

    /**
     * 创建成功转移事件（简化版）
     */
    public static StateTransitionEvent success(String caseInstanceId,
                                               String fromState,
                                               String toState,
                                               String eventCode) {
        return success(caseInstanceId, fromState, toState, eventCode, null, null);
    }

    /**
     * 创建拒绝事件（转移未发生）
     */
    public static StateTransitionEvent rejected(String caseInstanceId,
                                                String fromState,
                                                String eventCode,
                                                String intentCode,
                                                String reason) {
        return builder()
            .caseInstanceId(caseInstanceId)
            .fromState(fromState)
            .toState(fromState)  // 拒绝时状态不变
            .eventCode(eventCode)
            .intentCode(intentCode)
            .success(false)
            .rejectionReason(reason)
            .build();
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String caseInstanceId;
        private String fromState;
        private String toState;
        private String eventCode;
        private String intentCode;
        private boolean success = true;
        private String rejectionReason;
        private Instant timestamp;
        private Map<String, Object> contextSnapshot;

        public Builder caseInstanceId(String caseInstanceId) {
            this.caseInstanceId = caseInstanceId;
            return this;
        }

        public Builder fromState(String fromState) {
            this.fromState = fromState;
            return this;
        }

        public Builder toState(String toState) {
            this.toState = toState;
            return this;
        }

        public Builder eventCode(String eventCode) {
            this.eventCode = eventCode;
            return this;
        }

        public Builder intentCode(String intentCode) {
            this.intentCode = intentCode;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder rejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder contextSnapshot(Map<String, Object> contextSnapshot) {
            this.contextSnapshot = contextSnapshot;
            return this;
        }

        public StateTransitionEvent build() {
            if (caseInstanceId == null) {
                throw new IllegalArgumentException("caseInstanceId 不能为空");
            }
            if (fromState == null) {
                throw new IllegalArgumentException("fromState 不能为空");
            }
            if (success && toState == null) {
                throw new IllegalArgumentException("成功转移时 toState 不能为空");
            }
            return new StateTransitionEvent(this);
        }
    }

    // ==================== 便捷判断方法 ====================

    /**
     * 是否为状态变更（转移成功且状态发生变化）
     */
    public boolean isStateChanged() {
        return success && fromState != null && toState != null && !fromState.equals(toState);
    }

    /**
     * 是否进入终止状态
     */
    public boolean isEndState(String endState) {
        return success && endState != null && endState.equals(toState);
    }

    /**
     * 是否从指定状态转移
     */
    public boolean isFromState(String state) {
        return state != null && state.equals(fromState);
    }

    /**
     * 是否转移到指定状态
     */
    public boolean isToState(String state) {
        return state != null && state.equals(toState);
    }
}

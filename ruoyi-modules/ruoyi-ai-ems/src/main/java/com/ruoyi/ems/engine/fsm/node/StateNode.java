package com.ruoyi.ems.engine.fsm.node;


import com.ruoyi.ems.domain.IntentCommand;
import org.springframework.statemachine.ExtendedState;

/**
 * 状态节点处理器接口：
 * 所有案例（窄QRS、宽QRS、心脏骤停等）共用此接口，无需为每个案例新建实现类。
 */
public interface StateNode {

    /**
     * 根据当前状态、意图和参数，计算应触发的事件。
     *
     * @param caseId         当前运行的案例ID（如 "case_acls_narrow_qrs_stable"）
     * @param currentState   当前状态机状态ID（如 "ASSESS_STABILITY"）
     * @param intent         识别到的意图（包含 intentCode 和 params）
     * @param extendedState  状态机扩展状态（可读写，用于参数传递和上下文共享）
     * @return 触发状态迁移的事件名，若返回 null 则表示不触发迁移（仅记录参数）
     */
    String computeEvent(String caseId,
                        String currentState,
                        IntentCommand intent,
                        ExtendedState extendedState);
}

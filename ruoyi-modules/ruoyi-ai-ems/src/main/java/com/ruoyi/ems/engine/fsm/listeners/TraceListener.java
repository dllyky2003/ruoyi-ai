package com.ruoyi.ems.engine.fsm.listeners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Component;

/**
 * 状态机监听器：记录状态变更日志
 * 泛型改为 String，支持多案例配置
 */
@Slf4j
@Component
public class TraceListener extends StateMachineListenerAdapter<String, String> {

    @Override
    public void stateChanged(State<String, String> from, State<String, String> to) {
        String fromId = from != null ? from.getId() : "INIT";
        String toId = to != null ? to.getId() : "END";
        log.info("状态变更: {} --> {}", fromId, toId);
    }
}

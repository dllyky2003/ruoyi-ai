package com.ruoyi.ems.engine.fsm.interceptors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TraceInterceptor extends StateMachineInterceptorAdapter<String, String> {

    @SuppressWarnings("unchecked")
    @Override
    public void postStateChange(State<String, String> state,
                                Message<String> message,
                                Transition<String, String> transition,
                                StateMachine<String, String> stateMachine,
                                StateMachine<String, String> rootStateMachine) {
        String source = transition != null && transition.getSource() != null
            ? transition.getSource().getId() : null;
        String target = state != null ? state.getId() : null;
        String event = message != null ? message.getPayload() : null;

        Map<String, Object> trace = new HashMap<>();
        trace.put("from", source);
        trace.put("to", target);
        trace.put("event", event);
        trace.put("timestamp", LocalDateTime.now().toString());

        Map<Object, Object> variables = rootStateMachine.getExtendedState().getVariables();
        List<Map<String, Object>> traces = (List<Map<String, Object>>) variables.get("traces");
        if (traces == null) {
            traces = new ArrayList<>();
        }
        traces.add(trace);
        variables.put("traces", traces);

        log.info("状态变更: {} --[{}]--> {}", source, event, target);
    }
}

package com.ruoyi.ems.engine.fsm.actions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 药物给药动作：记录药物使用记录
 * 泛型改为 String，支持多案例配置
 */
@Slf4j
@Component
public class DrugAdministrationAction implements Action<String, String> {

    @Override
    public void execute(StateContext<String, String> context) {
        String event = context.getEvent() != null ? context.getEvent() : "UNKNOWN";
        log.info("药物给药动作触发，事件: {}", event);

        // 记录最近药物事件
        context.getExtendedState().getVariables().put("lastDrug", event);

        // 获取药物日志列表
        List<String> drugLog = context.getExtendedState()
            .get("drugLog", List.class);
        if (drugLog == null) {
            drugLog = new ArrayList<>();
        }

        // 根据事件记录药物名称
        if (event.contains("ADENOSINE_6MG")) {
            drugLog.add("腺苷6mg IV推注");
        } else if (event.contains("ADENOSINE_12MG")) {
            drugLog.add("腺苷12mg IV推注");
        } else if (event.contains("TREATMENT_DONE")) {
            drugLog.add("β阻滞剂/钙通道阻滞剂给药");
        } else if (event.contains("VAGAL_DONE")) {
            drugLog.add("迷走神经刺激操作");
        } else {
            drugLog.add("未知药物事件: " + event);
        }

        context.getExtendedState().getVariables().put("drugLog", drugLog);
    }
}

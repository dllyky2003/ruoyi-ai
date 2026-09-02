package com.ruoyi.ems.engine.fsm.actions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 电复律动作：记录安全校验结果
 * 泛型改为 String，支持多案例配置
 */
@Slf4j
@Component
public class CardioversionAction implements Action<String, String> {

    @Override
    public void execute(StateContext<String, String> context) {
        log.info("执行电复律动作，记录安全校验...");

        context.getExtendedState().getVariables().put("cardioversionPerformed", true);
        context.getExtendedState().getVariables().put("lastAction", "CARDIOVERSION");

        Map<String, Object> safetyCheck = new HashMap<>();

        // 从扩展状态中获取学员是否提及了安全要点
        Boolean syncModeMentioned = context.getExtendedState()
            .get("syncModeMentioned", Boolean.class);
        Boolean sedationMentioned = context.getExtendedState()
            .get("sedationMentioned", Boolean.class);
        Integer energySetting = context.getExtendedState()
            .get("energySetting", Integer.class);

        safetyCheck.put("syncModeMentioned", syncModeMentioned != null && syncModeMentioned);
        safetyCheck.put("sedationMentioned", sedationMentioned != null && sedationMentioned);
        safetyCheck.put("energySetting", energySetting);
        safetyCheck.put("energyCompliant", energySetting != null && energySetting >= 200);

        context.getExtendedState().getVariables().put("safetyCheck", safetyCheck);

        // 使用 Boolean.TRUE.equals 避免 NPE
        if (!Boolean.TRUE.equals(safetyCheck.get("syncModeMentioned"))) {
            log.warn("安全警告：未提及同步模式");
        }
        if (!Boolean.TRUE.equals(safetyCheck.get("energyCompliant"))) {
            log.warn("安全警告：能量设置未达2025指南推荐（≥200J）");
        }
    }
}

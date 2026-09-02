package com.ruoyi.ems.engine.fsm.guards;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

/**
 * 稳定性评估Guard：判断患者是否稳定
 * 泛型改为 String，支持多案例配置
 */
@Slf4j
@Component
public class StabilityGuard implements Guard<String, String> {

    @Override
    public boolean evaluate(StateContext<String, String> context) {
        // 从扩展状态中获取心率、血压等指标
        Object heartRateObj = context.getExtendedState().getVariables().get("heartRate");
        Object systolicBPObj = context.getExtendedState().getVariables().get("systolicBP");
        // 也可以从 performance 中获取，但为了灵活性，我们直接从扩展状态读取

        // 默认判断逻辑：心率 > 150 且 收缩压 > 90 视为稳定
        // 实际项目中可以从配置中读取阈值
        boolean stable = false;
        if (heartRateObj != null && systolicBPObj != null) {
            try {
                int heartRate = Integer.parseInt(heartRateObj.toString());
                int systolicBP = Integer.parseInt(systolicBPObj.toString());
                stable = heartRate > 150 && systolicBP > 90;
            } catch (NumberFormatException e) {
                log.warn("心率或血压值格式异常: heartRate={}, systolicBP={}", heartRateObj, systolicBPObj);
                stable = false;
            }
        } else {
            // 如果没有心率/血压数据，默认不稳定（保守）
            log.debug("未获取到心率或血压数据，默认不稳定");
            stable = false;
        }

        log.debug("稳定性评估结果: stable={}, heartRate={}, systolicBP={}",
            stable, heartRateObj, systolicBPObj);
        return stable;
    }
}

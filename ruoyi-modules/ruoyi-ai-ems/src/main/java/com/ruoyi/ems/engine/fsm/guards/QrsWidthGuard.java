package com.ruoyi.ems.engine.fsm.guards;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

/**
 * QRS宽度评估Guard：判断QRS波宽度（窄/宽）
 * 泛型改为 String，支持多案例配置
 */
@Slf4j
@Component
public class QrsWidthGuard implements Guard<String, String> {

    @Override
    public boolean evaluate(StateContext<String, String> context) {
        // 从扩展状态中获取 QRS 宽度（预期值为 "NARROW" 或 "WIDE"）
        Object qrsWidthObj = context.getExtendedState().getVariables().get("qrsWidth");
        // 也可以从 performance 中获取，但为了统一，从扩展状态读取

        boolean narrow = false;
        if (qrsWidthObj != null) {
            String qrsWidth = qrsWidthObj.toString();
            // 判断是否为窄QRS（即返回 true 表示窄，false 表示宽）
            narrow = "NARROW".equalsIgnoreCase(qrsWidth);
        } else {
            // 默认窄QRS（实际场景应根据数据定）
            log.debug("未获取到QRS宽度数据，默认窄QRS");
            narrow = true;
        }

        log.debug("QRS宽度评估结果: narrow={}, qrsWidth={}", narrow, qrsWidthObj);
        return narrow;
    }
}

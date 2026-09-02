package com.ruoyi.ems.orchestrator.service;

import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.orchestrator.model.PreconditionConfig;
import com.ruoyi.ems.spel.SpELExpressionEvaluator; // 改为导入具体类
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreconditionValidator {

    private final SpELExpressionEvaluator spelEvaluator; // 直接使用 SpEL 实现

    public boolean validate(PreconditionConfig config, CaseContext context, Set<String> completedIntents) {
        if (config == null) return true;

        // 1. 检查强制意图（快速通道）
        if (config.getRequiredIntents() != null) {
            for (String required : config.getRequiredIntents()) {
                if (!completedIntents.contains(required)) {
                    log.warn("⛔ 前置条件不满足：缺少强制意图 {}", required);
                    return false;
                }
            }
        }

        // 2. 检查状态键值对（简单场景）
        if (config.getRequiredState() != null) {
            for (var entry : config.getRequiredState().entrySet()) {
                Object actual = context.getVar(entry.getKey(), Object.class);
                if (!entry.getValue().equals(actual)) {
                    log.warn("⛔ 前置条件不满足：{} 需要 {}，实际 {}", entry.getKey(), entry.getValue(), actual);
                    return false;
                }
            }
        }

        // 3. SpEL 高级条件（若存在则作为最终裁决）
        if (config.getSpelGuard() != null && !config.getSpelGuard().isEmpty()) {
            boolean result = spelEvaluator.evaluateGuard(config.getSpelGuard(), context);
            if (!result) {
                log.warn("⛔ 前置条件不满足：SpEL 条件 {} 返回 false", config.getSpelGuard());
            }
            return result;
        }

        return true;
    }
}

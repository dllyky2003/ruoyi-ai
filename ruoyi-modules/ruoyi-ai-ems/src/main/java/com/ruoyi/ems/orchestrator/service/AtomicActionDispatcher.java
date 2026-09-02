package com.ruoyi.ems.orchestrator.service;

import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.ExecutionResult;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.rule.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AtomicActionDispatcher {

    private final TeamManager teamManager;
    private final AtomicActionRegistry actionRegistry;
    private final RuleEvaluator ruleEvaluator;

    /**
     * 派发原子动作
     * @param sessionId 会话ID
     * @param refAtomic 原子动作ID
     * @param actorName 执行者职责（对应 Duty 枚举名）
     * @param staticParams 静态参数
     * @param spelParams SpEL 动态参数定义
     * @param context 患者状态上下文
     */
    public void dispatch(String sessionId, String refAtomic, String actorName,
                         Map<String, Object> staticParams,
                         Map<String, String> spelParams,
                         CaseContext context) {

        // 1. 获取动作元数据
        var meta = actionRegistry.getMeta(refAtomic);
        if (meta == null) {
            throw new IllegalArgumentException("未知原子动作: " + refAtomic);
        }

        // 2. 合并参数：静态参数 + SpEL 动态计算
        Map<String, Object> finalParams = new HashMap<>();
        if (staticParams != null) finalParams.putAll(staticParams);

        if (spelParams != null) {
            for (var entry : spelParams.entrySet()) {
                try {
                    Object value = ruleEvaluator.evaluateValue(entry.getValue(), context, Object.class);
                    finalParams.put(entry.getKey(), value);
                } catch (Exception e) {
                    log.error("SpEL 参数计算失败: {} -> {}", entry.getKey(), entry.getValue(), e);
                    throw new RuntimeException("动态参数计算失败: " + entry.getKey());
                }
            }
        }

        // 3. 【安全硬校验】根据原子动作类型校验参数
        validateSafety(refAtomic, finalParams);

        // 4. 转换 Duty
        Duty duty;
        try {
            duty = Duty.valueOf(actorName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知职责: " + actorName + "，请检查 Duty 枚举");
        }

        // 5. 计算实际耗时（考虑成员技能水平，由 TeamManager 内部处理）
        int baseDuration = actionRegistry.getDuration(refAtomic);

        // 6. 构建任务描述（用于日志和回令）
        String taskDesc = buildTaskDescription(refAtomic, finalParams);

        // 7. 调用 TeamManager 分配任务（内部会查找空闲成员并更新状态）
        boolean assigned = teamManager.assignTaskForDuty(sessionId, duty, taskDesc, baseDuration);
        if (!assigned) {
            log.warn("⚠️ 无法分配任务 {} 给 {}（可能无空闲成员）", refAtomic, actorName);
            throw new RuntimeException("无可用成员执行任务: " + refAtomic);
        }

        log.info("📤 已派发原子动作: {} -> {} ({}s)，参数: {}", refAtomic, actorName, baseDuration, finalParams);
    }

    /**
     * 🚨 安全硬校验（杜绝 LLM 幻觉导致的危险参数）
     */
    private void validateSafety(String refAtomic, Map<String, Object> params) {
        // 电复律相关：能量必须在 50~200J
        if ("DISCHARGE_SHOCK".equals(refAtomic) || "CHARGE_DEFIB".equals(refAtomic)) {
            Object energyObj = params != null ? params.get("energy") : null;
            if (energyObj != null) {
                try {
                    String str = energyObj.toString().replace("J", "").trim();
                    int value = Integer.parseInt(str);
                    if (value < 50 || value > 200) {
                        throw new SafetyGuardException(
                            "⛔ 安全拦截：电复律能量 " + value + "J 超出合理范围（50~200J）"
                        );
                    }
                } catch (NumberFormatException e) {
                    throw new SafetyGuardException("⛔ 安全拦截：能量参数格式错误: " + energyObj);
                }
            }
        }

        // 腺苷推注：剂量只能是 6mg 或 12mg
        if ("IV_PUSH".equals(refAtomic) || "PREPARE_SYRINGE".equals(refAtomic)) {
            Object doseObj = params != null ? params.get("dose") : null;
            if (doseObj != null) {
                String doseStr = doseObj.toString();
                if (doseStr.contains("mg")) {
                    try {
                        int value = Integer.parseInt(doseStr.replace("mg", "").trim());
                        if (value != 6 && value != 12) {
                            throw new SafetyGuardException(
                                "⛔ 安全拦截：腺苷剂量 " + value + "mg 不符合指南（仅 6mg 或 12mg）"
                            );
                        }
                    } catch (NumberFormatException e) {
                        // 如果解析失败，可能是 "6mg/kg" 格式，跳过数值校验
                        log.debug("剂量格式包含单位组合，跳过数值硬校验: {}", doseStr);
                    }
                }
            }
        }
    }

    private String buildTaskDescription(String refAtomic, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder(refAtomic);
        if (params != null && !params.isEmpty()) {
            sb.append(" ");
            sb.append(params);
        }
        return sb.toString();
    }

    public static class SafetyGuardException extends RuntimeException {
        public SafetyGuardException(String message) {
            super(message);
        }
    }

    public CompletableFuture<ExecutionResult> dispatchAsync(
        String sessionId,
        String refAtomic,
        Map<String, Object> params,
        CaseContext context
    )
    {
        // 1. 执行参数校验和成员分配
        // 2. 返回CompletableFuture，由TeamManager的TaskCompletedEvent触发完成
        // 3. 同时发送VR动画指令（通过VrClientService）
        return null;
    }
}

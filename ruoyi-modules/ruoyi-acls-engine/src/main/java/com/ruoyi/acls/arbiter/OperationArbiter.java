package com.ruoyi.acls.arbiter;


import com.ruoyi.ems.config.loader.RuleConfigLoader;
import com.ruoyi.ems.domain.CaseContext;

import com.ruoyi.ems.domain.RuleDefinition;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 操作仲裁器：在执行动作链之前，验证当前意图在当前状态下是否被允许。
 * 基于规则配置文件（全局 + 案例专属）进行裁决。
 * <p>
 * 直接使用 RuleConfigLoader 和 SpELExpressionEvaluator，无需中间 RuleEngine 层。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationArbiter {

    private final RuleConfigLoader configLoader;
    private final SpELExpressionEvaluator evaluator;


    /**
     * 查找匹配的规则（按优先级返回第一个条件全部通过的规则）
     */
    private RuleDefinition findMatchingRule(String caseId, String intent,
                                            String currentState, Map<String, Object> slots,
                                            CaseContext context) {
        List<RuleDefinition> rules = configLoader.getMergedRulesForCase(caseId);
        for (RuleDefinition rule : rules) {
            // 1. 意图匹配
            if (!rule.getIntent().equals(intent)) {
                continue;
            }

            // 2. 状态匹配（"ANY" 表示任意状态）
            List<String> allowed = rule.getAllowedStates();
            if (allowed != null && !allowed.isEmpty() && !allowed.contains("ANY")) {
                if (!allowed.contains(currentState)) {
                    continue;
                }
            }

            // 3. 条件评估（所有条件必须为 true）
            List<String> conditions = rule.getConditions();
            boolean allMet = true;
            if (conditions != null && !conditions.isEmpty()) {
                for (String condExpr : conditions) {
                    // 使用 SpEL 评估，传入 slots 作为额外变量
                    boolean ok = evaluator.evaluateGuardWithVars(condExpr, context, slots);
                    if (!ok) {
                        allMet = false;
                        break;
                    }
                }
            }
            if (!allMet) continue;

            // 匹配成功
            return rule;
        }
        return null; // 无匹配规则，表示没有约束
    }

    /**
     * 仲裁给定意图在当前状态下是否允许执行
     *
     * @param currentState 当前状态机状态（如 "V-FIB"）
     * @param intentId     意图编码（如 "S2-06"）
     * @param slots        提取的参数（如 {"energy": 200}）
     * @param context      CaseContext（包含案例ID、变量等）
     * @return 仲裁结果（允许/拒绝 + 用户消息）
     */
    public ArbiterResult arbitrate(String currentState,
                                   String intentId,
                                   Map<String, Object> slots,
                                   CaseContext context) {
        // 1. 空上下文检查
        if (context == null) {
            log.warn("仲裁失败: CaseContext 为空");
            return ArbiterResult.deny("系统上下文不存在，请重新开始");
        }





        String caseId = context.getCaseType();
        if (caseId == null || caseId.isEmpty()) {
            log.warn("仲裁失败: caseId 为空");
            return ArbiterResult.deny("案例类型未识别");
        }

        //#============================将来需要判断角色是否有权执行某操作时再用，需要涉及_fast_path_routing.json
//        String currentRole = (String) context.getVar("role"); // 从上下文获取角色
//        if (currentRole != null && !roleBasedRouter.isRoleAllowed(caseId, intentId, currentRole)) {
//            return ArbiterResult.deny("当前角色 [" + currentRole + "] 无权执行此操作，请联系组长。");
//        }
        //#=========================================================

        // 2. 查找匹配规则
        RuleDefinition matched = findMatchingRule(caseId, intentId, currentState, slots, context);

        // 3. 无匹配规则 → 允许（没有约束）
        if (matched == null) {
            log.debug("仲裁通过（无匹配规则）: caseId={}, intent={}, state={}", caseId, intentId, currentState);
            return ArbiterResult.allow();
        }

        // 4. 有匹配规则但通过了所有条件 → 允许（规则条件全部满足）
        // 注意：findMatchingRule 返回的都是条件全部通过的规则，所以此处默认允许。
        // 如果需要额外逻辑，可在此扩展。
        log.debug("仲裁通过（匹配规则条件满足）: caseId={}, intent={}, ruleId={}", caseId, intentId, matched.getId());
        return ArbiterResult.allow();
    }

    /**
     * 获取拒绝消息（用于前端展示）
     */
    public String getDenyMessage(String currentState,
                                 String intentId,
                                 Map<String, Object> slots,
                                 CaseContext context) {
        if (context == null) return "上下文不存在";
        String caseId = context.getCaseType();
        if (caseId == null) return "案例类型未识别";

        RuleDefinition matched = findMatchingRule(caseId, intentId, currentState, slots, context);
        if (matched == null) {
            return null; // 无匹配规则，无需拒绝
        }
        String msg = matched.getDenyMessage();
        if (msg != null && slots != null) {
            for (Map.Entry<String, Object> entry : slots.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value != null) {
                    msg = msg.replace("{" + key + "}", String.valueOf(value));
                }
            }
        }
        return msg != null ? msg : "当前状态不允许执行此操作";
    }

    /**
     * 便捷方法：仲裁 + 自动处理拒绝消息
     */
    public ArbiterResult arbitrateWithMessage(String currentState,
                                              String intentId,
                                              Map<String, Object> slots,
                                              CaseContext context) {
        ArbiterResult result = arbitrate(currentState, intentId, slots, context);
        if (!result.isAllowed()) {
            // 如果仲裁返回拒绝，但未设置消息，补全消息
            if (result.getUserFriendlyMessage() == null) {
                String msg = getDenyMessage(currentState, intentId, slots, context);
                return ArbiterResult.deny(msg != null ? msg : "操作被拒绝");
            }
        }
        return result;
    }
}

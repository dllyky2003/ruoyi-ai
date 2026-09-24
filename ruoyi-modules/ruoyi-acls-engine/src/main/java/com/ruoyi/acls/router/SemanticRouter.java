package com.ruoyi.acls.router;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticRouter {

    private final SemanticRoutingLoader routingLoader;
    private final SkillRegistry skillRegistry;
    private final SpELExpressionEvaluator spelEvaluator;

    /**
     * 路由主入口
     */
    public RouteDecision route(IntentCommand command, CaseContext context) {
        String intentCode = command.getIntentCode();
        String caseType = context.getCaseType();
        String currentState = context.getCurrentState();
        Map<String, Object> slots = command.getSlots();

        log.debug("🧭 SemanticRouter: intent={}, caseType={}, state={}", intentCode, caseType, currentState);

        // 1. 查找匹配的路由规则
        List<RouteRule> matchedRules = routingLoader.findMatchingRules(intentCode, caseType, currentState);

        if (matchedRules.isEmpty()) {
            log.warn("⚠️ 未找到匹配的路由规则: intent={}, caseType={}, state={}",
                intentCode, caseType, currentState);
            return RouteDecision.clarify("当前场景下不支持此操作，请重新下达指令。");
        }

        // ★ 2. 排序：具体规则优先于通配符规则，避免通配规则抢在前面被尝试
        matchedRules.sort((a, b) -> {
            boolean aWild = "*".equals(a.getIntent());
            boolean bWild = "*".equals(b.getIntent());
            return Boolean.compare(aWild, bWild);
        });

        // 3. 遍历匹配的规则
        for (RouteRule rule : matchedRules) {
            // 3.1 检查 SpEL 条件
            if (!evaluateCondition(rule, context, slots)) {
                log.debug("⏭️ 跳过规则 {}: 条件不满足", rule.getId());
                continue;
            }

            // 3.2 ★ targetId 判空（可能是通配兜底规则未配置 target）
            String targetId = rule.getTargetId();
            if (targetId == null || targetId.isBlank()) {
                log.warn("⚠️ 规则 [{}] targetId 为空，已跳过: intent={}, caseType={}, state={}, 请检查 YAML 配置",
                    rule.getId(), rule.getIntent(), rule.getCaseType(), rule.getState());
                continue;
            }

            // 3.3 查询技能
            EmsSkill skill = skillRegistry.getSkill(targetId);
            if (skill == null) {
                log.warn("⚠️ 规则 {} 指向的技能不存在: {}", rule.getId(), targetId);
                continue;
            }

            // 3.4 解析参数并执行
            Map<String, Object> resolvedParams = resolveParams(rule, slots, context);

            log.info("✅ 路由命中: rule={}, target={}, params={}",
                rule.getId(), targetId, resolvedParams);

            return RouteDecision.executeSkill(targetId, resolvedParams);
        }

        log.warn("⚠️ 所有匹配规则的条件下都不满足: intent={}, caseType={}, state={}",
            intentCode, caseType, currentState);
        return RouteDecision.clarify("当前情况不符合操作条件，请检查患者状态。");
    }

    /**
     * ★ 检查是否有匹配该意图的路由规则
     */
    public boolean hasRouteForIntent(String intentId) {
        if (intentId == null) return false;
        List<RouteRule> rules = routingLoader.getRouteRules();
        for (RouteRule rule : rules) {
            if (rule.getIntent() == null) continue;
            if (rule.getIntent().equals(intentId) || "*".equals(rule.getIntent())) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ 获取路由加载器（用于外部查询）
     */
    public SemanticRoutingLoader getRoutingLoader() {
        return routingLoader;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 求值规则条件。
     *
     * <p>★ 2026-09-23（P0 修复）：改用 CaseContext 版本 API。
     * Map 版本的 {@code evaluateValue(String, Map, Class)} 已被团队禁用。</p>
     */
    private boolean evaluateCondition(RouteRule rule, CaseContext context, Map<String, Object> slots) {
        String condition = rule.getCondition();
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        try {
            Boolean result = spelEvaluator.evaluateValue(condition, context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("⚠️ SpEL 条件评估失败: rule={}, condition={}, error={}",
                rule.getId(), condition, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> resolveParams(RouteRule rule, Map<String, Object> slots, CaseContext context) {
        Map<String, Object> result = new HashMap<>();

        if (rule.getParams() != null) {
            result.putAll(rule.getParams());
        }

        if (rule.getParamMapping() != null && !rule.getParamMapping().isEmpty()) {
            for (Map.Entry<String, String> mapping : rule.getParamMapping().entrySet()) {
                String targetKey = mapping.getKey();
                String expression = mapping.getValue();

                try {
                    Object value = evaluateParamExpression(expression, slots, context);
                    if (value != null) {
                        result.put(targetKey, value);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ 参数映射评估失败: key={}, expression={}, error={}",
                        targetKey, expression, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 求值 paramMapping 里的单个表达式。
     *
     * <p>★ 2026-09-23（P0 修复）：改用 CaseContext 版本 API。
     * 表达式内用 {@code #<domain>.<field>} 形式，例如 {@code #intervention.lastDrug}；
     * 由 {@code SpELExpressionEvaluator.buildContext(CaseContext)} 组装可用变量域
     * （{@code #phys} / {@code #process} / {@code #intervention} / {@code #device} /
     * {@code #clinical} / {@code #derived}）。</p>
     */
    private Object evaluateParamExpression(String expression, Map<String, Object> slots, CaseContext context) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        // 常量（不含表达式符号）直接返回
        if (!expression.contains("#") && !expression.contains("$") && !expression.contains("{")) {
            return expression;
        }

        try {
            // 归一化写法：$vars.x / ${vars.x} / $slots.x → # 形式
            String normalized = expression
                .replace("$slots.", "#slots.")
                .replace("${slots.", "#slots.")
                .replace("$vars.", "#vars.")
                .replace("${vars.", "#vars.");

            // ★ 走 CaseContext 版本（Map 版本已被团队禁用）
            return spelEvaluator.evaluateValue(normalized, context, Object.class);
        } catch (Exception e) {
            log.warn("⚠️ 参数表达式评估失败: expression={}, error={}", expression, e.getMessage());
            return null;
        }
    }
}

package com.ruoyi.ems.engine;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.rule.IRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RuleMatcher {

    private final YamlStateMachineLoader configLoader;
    private final IRuleEvaluator ruleEvaluator;  // 新增，用于评估条件

    public RuleMatcher(YamlStateMachineLoader configLoader, IRuleEvaluator ruleEvaluator) {
        this.configLoader = configLoader;
        this.ruleEvaluator = ruleEvaluator;
    }

    // ============================================================
    // 1. 原有方法：匹配单个规则（由用户意图触发）
    // ============================================================

    /**
     * 查找匹配的转移规则（支持继承链）
     */
    public TransitionRule findMatchingRule(ResolvedCaseTypeConfig config,
                                           String currentState,
                                           IntentCommand command) {

        List<String> chain = configLoader.getInheritanceChain(config.getCode());

        for (String caseType : chain) {
            ResolvedCaseTypeConfig c = configLoader.getConfig(caseType);
            if (c == null) continue;

            Optional<TransitionRule> match = c.getTransitions().stream()
                .filter(r -> r.getSource().equals(currentState))
                .filter(r -> r.getIntentCode() == null ||
                    r.getIntentCode().equals(command.getIntentCode()))
                .filter(r -> r.getEvent() != null && !r.getEvent().isEmpty())
                .sorted((a, b) -> {
                    // 优先匹配明确 intentCode 的规则
                    boolean aHasIntent = a.getIntentCode() != null;
                    boolean bHasIntent = b.getIntentCode() != null;
                    if (aHasIntent && !bHasIntent) return -1;
                    if (!aHasIntent && bHasIntent) return 1;
                    return 0;
                })
                .findFirst();

            if (match.isPresent()) {
                log.debug("规则匹配成功: {} → {} (来源: {})",
                    currentState, match.get().getTarget(), caseType);
                return match.get();
            }
        }

        return null;
    }

    // ============================================================
    // 2. 新增方法：查找所有可触发的系统定时规则
    // ============================================================

    /**
     * 查找当前状态下所有可触发的系统定时规则（systemTrigger = true）
     * 由 ClockService 每秒调用，用于自动超时转移
     *
     * @param config 案例配置
     * @param currentState 当前状态
     * @param context 运行时上下文（用于评估条件）
     * @return 满足条件的所有系统规则列表
     */
    public List<TransitionRule> findSystemTriggerableRules(ResolvedCaseTypeConfig config,
                                                           String currentState,
                                                           CaseContext context) {
        List<TransitionRule> matched = new ArrayList<>();

        if (config == null || currentState == null || context == null) {
            return matched;
        }

        // 从当前状态的所有转移中筛选 systemTrigger = true 的规则
        List<TransitionRule> candidates = config.getTransitions().stream()
            .filter(r -> r.getSource().equals(currentState))
            .filter(r -> r.isSystemTrigger())  // 需要 TransitionRule 有 systemTrigger 字段
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return matched;
        }

        // 逐条评估条件
        for (TransitionRule rule : candidates) {
            // 如果没有条件，直接视为可触发
            if (rule.getCondition() == null || rule.getCondition().isEmpty()) {
                matched.add(rule);
                log.debug("系统规则无条件，可直接触发: {}", rule.getDescription());
                continue;
            }

            // 评估条件
            try {
                boolean passed = ruleEvaluator.evaluateCondition(rule.getCondition(), context);
                if (passed) {
                    matched.add(rule);
                    log.debug("系统规则条件满足: {} -> {}", rule.getDescription(), rule.getTarget());
                } else {
                    log.trace("系统规则条件不满足: {}", rule.getDescription());
                }
            } catch (Exception e) {
                log.warn("系统规则条件评估异常: {}, 错误: {}", rule.getDescription(), e.getMessage());
            }
        }

        return matched;
    }

    /**
     * 检查当前状态下是否有可触发的系统规则（快速判断）
     */
    public boolean hasSystemTriggerableRules(ResolvedCaseTypeConfig config,
                                             String currentState,
                                             CaseContext context) {
        return !findSystemTriggerableRules(config, currentState, context).isEmpty();
    }

    // ============================================================
    // 3. 原有方法：获取可用意图列表
    // ============================================================

    /**
     * 获取某状态下所有可用的意图列表
     */
    public Set<String> getAvailableIntents(ResolvedCaseTypeConfig config, String currentState) {
        return config.getTransitions().stream()
            .filter(r -> r.getSource().equals(currentState))
            .map(TransitionRule::getIntentCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}

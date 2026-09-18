package com.ruoyi.acls.guard;

import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 安全护栏
 *
 * <p>职责：在指令执行前，检查当前场景下该操作是否被禁止。
 *
 * <p>拦截级别：
 * <ul>
 *   <li><b>BLOCK（硬拦截）</b>：绝对禁止执行，直接拒绝</li>
 *   <li><b>WARN（软拦截）</b>：警告但允许执行（需二次确认）</li>
 *   <li><b>LOG（仅记录）</b>：记录日志但不拦截</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardRail {

    private final GuardRailLoader guardRailLoader;

    /**
     * 检查指令是否触发安全拦截
     *
     * @param command 意图命令
     * @param context 案例上下文
     * @return null 表示安全（继续执行）；非 null 表示被拦截
     */
    public CommandResult check(IntentCommand command, CaseContext context) {
        if (command == null || context == null) {
            return null;
        }

        String intentCode = command.getIntentCode();
        String caseType = context.getCaseType();
        String currentState = context.getCurrentState();

        // 查找匹配的护栏规则
        List<GuardRule> matchedRules = guardRailLoader.findMatchingRules(
            caseType, currentState, intentCode
        );

        if (matchedRules.isEmpty()) {
            return null;
        }

        // 按严重级别排序后，取第一条
        GuardRule firstRule = matchedRules.get(0);

        switch (firstRule.getSeverity()) {
            case BLOCK:
                log.warn("🛑 安全护栏硬拦截: intent={}, caseType={}, state={}, rule={}",
                    intentCode, caseType, currentState, firstRule.getId());
                return buildBlockedResult(firstRule);

            case WARN:
                log.info("⚠️ 安全护栏软警告: intent={}, caseType={}, state={}, rule={}",
                    intentCode, caseType, currentState, firstRule.getId());
                // 软拦截：返回警告信息但不阻断（业务层决定是否继续）
                return buildWarningResult(firstRule);

            case LOG:
                log.info("📋 安全护栏记录: intent={}, caseType={}, state={}, rule={}, message={}",
                    intentCode, caseType, currentState, firstRule.getId(), firstRule.getMessage());
                return null;  // 仅记录，不拦截

            default:
                return null;
        }
    }

    /**
     * 检查指令是否触发安全拦截（带阻断级别返回）
     *
     * @param command 意图命令
     * @param context 案例上下文
     * @return 拦截结果，包含是否阻断以及对应的 CommandResult
     */
    public GuardCheckResult checkWithLevel(IntentCommand command, CaseContext context) {
        if (command == null || context == null) {
            return GuardCheckResult.pass();
        }

        String intentCode = command.getIntentCode();
        String caseType = context.getCaseType();
        String currentState = context.getCurrentState();

        List<GuardRule> matchedRules = guardRailLoader.findMatchingRules(
            caseType, currentState, intentCode
        );

        if (matchedRules.isEmpty()) {
            return GuardCheckResult.pass();
        }

        GuardRule firstRule = matchedRules.get(0);

        switch (firstRule.getSeverity()) {
            case BLOCK:
                return GuardCheckResult.block(buildBlockedResult(firstRule));
            case WARN:
                return GuardCheckResult.warn(buildWarningResult(firstRule), firstRule);
            case LOG:
                log.info("📋 [GUARD_LOG] {}", firstRule.getMessage());
                return GuardCheckResult.pass();
            default:
                return GuardCheckResult.pass();
        }
    }

    private CommandResult buildBlockedResult(GuardRule rule) {
        String message = rule.getMessage();
        String suggestion = rule.getSuggestion();

        if (suggestion != null && !suggestion.isEmpty()) {
            return CommandResult.failure(message + " " + suggestion);
        }
        return CommandResult.failure(message);
    }

    private CommandResult buildWarningResult(GuardRule rule) {
        String message = rule.getMessage();
        String suggestion = rule.getSuggestion();

        if (suggestion != null && !suggestion.isEmpty()) {
            return CommandResult.warning(message + " " + suggestion);
        }
        return CommandResult.warning(message);
    }

    /**
     * 护栏检查结果
     */
    public static class GuardCheckResult {
        private final boolean blocked;
        private final boolean warned;
        private final CommandResult result;
        private final GuardRule rule;

        private GuardCheckResult(boolean blocked, boolean warned, CommandResult result, GuardRule rule) {
            this.blocked = blocked;
            this.warned = warned;
            this.result = result;
            this.rule = rule;
        }

        public static GuardCheckResult pass() {
            return new GuardCheckResult(false, false, null, null);
        }

        public static GuardCheckResult block(CommandResult result) {
            return new GuardCheckResult(true, false, result, null);
        }

        public static GuardCheckResult warn(CommandResult result, GuardRule rule) {
            return new GuardCheckResult(false, true, result, rule);
        }

        public boolean isBlocked() {
            return blocked;
        }

        public boolean isWarned() {
            return warned;
        }

        public boolean isPassed() {
            return !blocked && !warned;
        }

        public CommandResult getResult() {
            return result;
        }

        public GuardRule getRule() {
            return rule;
        }
    }
}

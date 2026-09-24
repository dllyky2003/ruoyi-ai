package com.ruoyi.ems.spi;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.engine.StateTransitionResult;
import com.ruoyi.ems.spi.dto.OutcomeBranchRequest;
import com.ruoyi.ems.spi.dto.OutcomeBranchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OutcomeBranchExecutor 的默认实现（ems 侧）。
 *
 * <p>写变量 + 触发状态机重评估 + 幂等防重。</p>
 *
 * <p>幂等键：sessionId|branchId；同一会话内同一分支只执行一次。</p>
 *
 * <p>★ 2026-09-23 修改：{@link StateMachineFacade#reEvaluateOnContextChange}
 * 返回类型从 {@code String} 改为 {@link StateTransitionResult}，
 * 本类同步适配：用 {@code transition.getToState()} 取目标状态，
 * 并把 {@code success} / {@code reason} 记入日志。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultOutcomeBranchExecutor implements OutcomeBranchExecutor {

    private final EmsContextManager contextManager;
    private final StateMachineFacade stateMachineFacade;

    private final Set<String> executedKeys = ConcurrentHashMap.newKeySet();

    @Override
    public OutcomeBranchResult execute(OutcomeBranchRequest req) {
        OutcomeBranchResult result = new OutcomeBranchResult();

        if (req == null || req.getSessionId() == null || req.getBranchId() == null) {
            result.setSuccess(false);
            result.setErrorMessage("request 或 sessionId/branchId 为空");
            return result;
        }

        String key = req.getSessionId() + "|" + req.getBranchId();
        if (!executedKeys.add(key)) {
            log.info("[OutcomeBranchExecutor] 幂等跳过: session={}, branchId={}",
                req.getSessionId(), req.getBranchId());
            result.setSuccess(true);
            result.setSkipped(true);
            return result;
        }

        try {
            CaseContext ctx = contextManager.getSnapshot(req.getSessionId());
            String stateBefore = ctx != null ? ctx.getCurrentState() : null;
            result.setStateBefore(stateBefore);

            Map<String, Object> updates = req.getClinicalStateValues();
            if (updates != null && !updates.isEmpty()) {
                long version = ctx != null ? ctx.getVersion() : 0L;
                boolean ok = contextManager.batchSetVarsWithVersion(
                    req.getSessionId(), updates, "OUTCOME_BRANCH", version);
                if (!ok) {
                    CaseContext latest = contextManager.getSnapshot(req.getSessionId());
                    if (latest != null) {
                        ok = contextManager.batchSetVarsWithVersion(
                            req.getSessionId(), updates, "OUTCOME_BRANCH", latest.getVersion());
                    }
                }
                if (!ok) {
                    executedKeys.remove(key);
                    result.setSuccess(false);
                    result.setErrorMessage("变量写入失败（版本冲突重试后仍失败）");
                    return result;
                }
                result.setWrittenVarCount(updates.size());
            } else {
                log.warn("[OutcomeBranchExecutor] clinicalStateValues 为空: session={}, branchId={}",
                    req.getSessionId(), req.getBranchId());
            }

            // ★ 2026-09-23 修改：接收 StateTransitionResult
            StateTransitionResult transition = stateMachineFacade.reEvaluateOnContextChange(
                req.getSessionId(), "OUTCOME_BRANCH");

            // ★ 从 transition 中取目标状态；null 时回退到 stateBefore
            String newState = (transition != null && transition.getToState() != null)
                ? transition.getToState()
                : stateBefore;
            result.setStateAfter(newState);
            result.setSuccess(true);

            // ★ 日志补充 success / reason，便于诊断
            log.info("[OutcomeBranchExecutor] branch 已应用: session={}, branchId={}, vars={}, {} -> {} (success={}, reason={})",
                req.getSessionId(), req.getBranchId(),
                updates != null ? updates.keySet() : Collections.emptySet(),
                stateBefore, newState,
                transition != null && transition.isSuccess(),
                transition != null ? transition.getReason() : "null");
            return result;
        } catch (Exception e) {
            executedKeys.remove(key);
            result.setSuccess(false);
            result.setErrorMessage("execute 异常: " + e.getMessage());
            log.warn("[OutcomeBranchExecutor] 执行失败: session={}, branchId={}, err={}",
                req.getSessionId(), req.getBranchId(), e.getMessage(), e);
            return result;
        }
    }
}

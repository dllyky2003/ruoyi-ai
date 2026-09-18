package com.ruoyi.acls.listener;

import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.event.TaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能完成 → 置位变量 → 驱动状态机。
 * 监听 TeamManager 发布的 TaskCompletedEvent，把"NPC 任务完成"
 * 翻译成"状态机可感知的变量变更 + 条件出边重评估"。
 *
 * <p>所有变量写入统一走 EmsContextManager.batchSetVarsWithVersion，
 * 复用版本锁、权限校验、WS 推送。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCompletionStateDriver {

    private final SkillRegistry skillRegistry;
    private final EmsContextManager contextManager;
    private final StateMachineFacade stateMachineFacade;

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (!event.isSuccess()) {
            log.debug("任务失败，跳过状态驱动: task={}", event.getMember().getCurrentTask());
            return;
        }

        String sessionId = event.getSessionId();
        String taskId = event.getMember().getCurrentTask();

        EmsSkill skill = skillRegistry.getSkill(taskId);
        if (skill == null) {
            log.warn("技能未找到: {}", taskId);
            return;
        }

        // 1. 从接口统一拿输出（可能是多个变量，也可能为空）
        Map<String, Object> outputs = skill.getSuccessOutputs();
        if (outputs == null || outputs.isEmpty()) {
            // 兼容旧实现：只实现了 getSuccessVarName 的技能
            String varName = skill.getSuccessVarName();
            if (varName == null) {
                log.debug("技能 {} 无输出变量，跳过", taskId);
                return;
            }
            outputs = Map.of(varName, true);
        }

        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            log.warn("上下文不存在: {}", sessionId);
            return;
        }

        // 2. 解析 #xxx 引用（只能引用 CaseContext 里的变量）
        Map<String, Object> evalMap = new HashMap<>(ctx.getVariablesSnapshot());
        evalMap.put("caseContext", ctx);
        evalMap.put("elapsedSeconds", ctx.getElapsedSeconds());
        evalMap.put("currentState", ctx.getCurrentState());

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : outputs.entrySet()) {
            resolved.put(e.getKey(), resolveValue(e.getValue(), evalMap));
        }

        // 3. ★ 走统一入口：版本锁 + 权限校验 + WS 推送
        long version = ctx.getVersion();
        boolean ok = contextManager.batchSetVarsWithVersion(
            sessionId, resolved, "SYSTEM", version);

        if (!ok) {
            log.warn("变量批量写入失败（版本冲突？）: session={}, task={}, vars={}",
                sessionId, taskId, resolved.keySet());
            return;
        }
        log.info("📌 变量批量置位: {} (task={})", resolved, taskId);

        // 4. 驱动状态机（2 参重载，内部从 configCache 拿 config + 加锁）
        stateMachineFacade.reEvaluateOnContextChange(sessionId, taskId);
    }

    private Object resolveValue(Object value, Map<String, Object> evalMap) {
        if (value instanceof String s && s.startsWith("#")) {
            String varKey = s.substring(1);
            if (evalMap.containsKey(varKey)) {
                return evalMap.get(varKey);
            }
            log.warn("变量 {} 不存在，保留原值", varKey);
        }
        return value;
    }
}

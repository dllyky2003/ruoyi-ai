package com.ruoyi.ems.executor.factory;

import com.ruoyi.ems.domain.ExecutionResult;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.executor.IntentExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图执行器工厂
 * 根据意图类型自动选择对应的执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentExecutorFactory {

    /** Spring 会自动注入所有实现了 IntentExecutor 接口的 Bean */
    private final List<IntentExecutor> executors;

    /** 缓存：意图码 → 执行器 */
    private final Map<String, IntentExecutor> executorCache = new ConcurrentHashMap<>();

    /**
     * 获取匹配的执行器
     */
    public IntentExecutor getExecutor(IntentCommand command) {
        String intentCode = command.getIntentCode();

        // 从缓存获取
        if (executorCache.containsKey(intentCode)) {
            return executorCache.get(intentCode);
        }

        // 遍历所有执行器，查找支持该意图的
        for (IntentExecutor executor : executors) {
            if (executor.supports(command)) {
                executorCache.put(intentCode, executor);
                return executor;
            }
        }

        // 如果没有找到，返回默认执行器（Fallback）
        return getFallbackExecutor();
    }

    /**
     * 执行意图
     */
    public ExecutionResult execute(IntentCommand command, String sessionId) {
        IntentExecutor executor = getExecutor(command);
        if (executor == null) {
            return ExecutionResult.rejected("没有找到支持该意图的执行器");
        }

        try {
            return executor.execute(command, sessionId);
        } catch (Exception e) {
            log.error("执行意图失败: {}", command.getIntentCode(), e);
            return ExecutionResult.rejected("执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取兜底执行器
     */
    private IntentExecutor getFallbackExecutor() {
        return new IntentExecutor() {
            @Override
            public ExecutionResult execute(IntentCommand command, String sessionId) {
                return ExecutionResult.success("✅ 已收到指令，正在处理...");
            }

            @Override
            public boolean supports(IntentCommand command) {
                return true; // 任何意图都支持
            }

            @Override
            public ExecutorType getType() {
                return ExecutorType.FALLBACK;
            }
        };
    }
}

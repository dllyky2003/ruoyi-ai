package com.ruoyi.ems.executor;

import com.ruoyi.ems.domain.ExecutionResult;
import com.ruoyi.ems.domain.IntentCommand;

/**
 * 意图执行器接口
 * 所有意图执行器必须实现此接口
 */
public interface IntentExecutor {

    /**
     * 执行意图
     * @param command 意图命令
     * @param sessionId 会话ID
     * @return 执行结果
     */
    ExecutionResult execute(IntentCommand command, String sessionId);

    /**
     * 判断是否支持该意图
     */
    boolean supports(IntentCommand command);

    /**
     * 获取执行器类型
     */
    ExecutorType getType();

    /**
     * 执行器类型枚举
     */
    enum ExecutorType {
        /** 查询/询问 */
        INQUIRY,
        /** 执行指令 */
        COMMAND,
        /** 宣告/报告 */
        REPORT,
        /** 团队协调 */
        TEAM,
        /** 错误/兜底 */
        FALLBACK
    }
}

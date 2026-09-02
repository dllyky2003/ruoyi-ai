package com.ruoyi.acls.command;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;

/**
 * 命令接口 —— 所有可执行动作的统一抽象
 */
public interface ICommand {

    /** 命令唯一标识（用于日志和追踪） */
    String getCommandId();

    /** 执行核心逻辑 */
    CommandResult execute(CommandContext context);

    /** 超时阈值（毫秒），0 表示无超时限制 */
    long getTimeoutMillis();

    /** 前置条件检查（返回 false 则跳过此命令） */
    default boolean evaluateCondition(CommandContext context) {
        return true;
    }

    /** 外部中断回调（响应组长抢占或系统关闭） */
    default void onInterrupted(CommandContext context) {
        // 默认空实现
    }
}

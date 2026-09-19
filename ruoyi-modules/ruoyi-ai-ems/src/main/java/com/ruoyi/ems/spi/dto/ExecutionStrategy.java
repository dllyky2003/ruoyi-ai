package com.ruoyi.ems.spi.dto;

/**
 * 四分支执行策略。
 */
public enum ExecutionStrategy {
    ALLOW,
    ALLOW_WITH_WARNING,
    BLOCK,
    EXECUTE_HARMFUL_ACTION
}

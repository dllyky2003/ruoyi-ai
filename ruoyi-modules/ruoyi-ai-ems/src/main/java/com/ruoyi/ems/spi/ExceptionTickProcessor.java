package com.ruoyi.ems.spi;

/**
 * 时钟 tick 时的异常评估 SPI。
 * <p>由 acls-engine 模块的 AclsCommandEngine 实现；
 * ems 侧的 ClockService 通过本接口回调，避免 ems 反向依赖 acls-engine。</p>
 */
public interface ExceptionTickProcessor {

    /**
     * 每秒 tick 时调用，评估延时规则 / 异常规则。
     * @param sessionId ems 会话 ID
     */
    void tickExceptionsForSession(String sessionId);
}

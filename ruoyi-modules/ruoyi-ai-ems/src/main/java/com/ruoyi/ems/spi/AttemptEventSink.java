package com.ruoyi.ems.spi;


import com.ruoyi.ems.spi.dto.AttemptEventContext;

/**
 * 尝试事件 Sink（SPI）。
 *
 * <p>在每次权威状态迁移提交后被 ems 调用一次，用于把该次事件交给 tutor 侧处理：
 * eventSequence 推进、ScenarioBeat 选择、评分证据链写入等。</p>
 *
 * <p>tutor 未部署时，由 NoOpAttemptEventSink 兜底，行为不变。</p>
 */
public interface AttemptEventSink {

    /**
     * 状态迁移已提交（事务成功后）回调。
     *
     * <p>实现方不得抛异常中断主流程；ems 侧已 try/catch 兜底。</p>
     *
     * @param context 迁移上下文，含 sessionId、from/to state、intent、variables 等
     */
    void onTransitionCommitted(AttemptEventContext context);
}

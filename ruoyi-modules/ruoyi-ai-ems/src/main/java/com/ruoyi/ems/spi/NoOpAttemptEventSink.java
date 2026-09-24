package com.ruoyi.ems.spi;

import com.ruoyi.ems.spi.dto.AttemptEventContext;
import lombok.extern.slf4j.Slf4j;

/**
 * AttemptEventSink 的 No-Op 兜底实现。
 *
 * <p>tutor 未部署时使用，行为等价于“不处理”。</p>
 */
@Slf4j
public class NoOpAttemptEventSink implements AttemptEventSink {

    @Override
    public void onTransitionCommitted(AttemptEventContext context) {
        if (log.isDebugEnabled()) {
            log.debug("[ACLS SPI] AttemptEventSink No-Op: session={}, {} -> {}",
                context != null ? context.getSessionId() : null,
                context != null ? context.getFromState() : null,
                context != null ? context.getToState() : null);
        }
    }
}

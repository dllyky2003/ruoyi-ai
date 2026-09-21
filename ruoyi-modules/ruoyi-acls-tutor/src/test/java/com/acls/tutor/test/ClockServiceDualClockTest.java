package com.acls.tutor.test;

import com.ruoyi.acls.engine.ClockService;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClockServiceDualClockTest {

    @Mock EmsContextManager contextManager;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ClockService clockService;

    /**
     * 验证双时钟分离：
     * - simulated elapsed 只在 RUNNING 时递增（排除暂停）
     * - assessment real elapsed 按墙上时间累计（包含暂停）
     *
     * <p>注意：contextManager 是 Mockito mock，setClockRunning(...) 是 void 方法，
     * 默认 no-op，不会真正改 CaseContext.clockStatus。因此这里在调用
     * ClockService.pause/resume 之后，手动同步 ctx.pauseClock()/resumeClock()，
     * 让 CaseContext 自身的 RUNNING/PAUSED 语义与 ClockService 一致。</p>
     */
    @Test
    void real应包含暂停_simulated不包含() throws Exception {
        CaseContext ctx = new CaseContext("S-1");
        when(contextManager.getSnapshot("S-1")).thenReturn(ctx);

        clockService.start("S-1");
        ctx.resumeClock();          // 同步 mock 未生效的副作用
        Thread.sleep(60);

        clockService.pause("S-1");
        ctx.pauseClock();           // 同步 mock 未生效的副作用
        // 暂停期间尝试推进 simulated：应被 CaseContext.isClockRunning() 挡住
        ctx.tick();
        ctx.tick();
        Thread.sleep(60);

        clockService.resume("S-1");
        ctx.resumeClock();          // 同步 mock 未生效的副作用
        Thread.sleep(60);

        // simulated：暂停期间的两次 tick 应被挡住，总数保持 0
        assertEquals(0.0, clockService.elapsedSeconds("S-1"),
            "暂停期间的 tick 不应计入 simulated elapsed");

        // real：墙上时间累计，应 >= 0.15s（三段 sleep 合计 ~180ms，留余量）
        double real = clockService.assessmentRealElapsedSeconds("S-1");
        assertTrue(real >= 0.15, "real 应 >= 0.15s, 实际=" + real);
    }

    @Test
    void stop应冻结real不再增长() throws Exception {
        // 本测试只走 ClockService 自己的 Map，不需要 stub getSnapshot
        clockService.start("S-1");
        Thread.sleep(40);
        clockService.stop("S-1");

        double r1 = clockService.assessmentRealElapsedSeconds("S-1");
        Thread.sleep(60);
        double r2 = clockService.assessmentRealElapsedSeconds("S-1");
        assertEquals(r1, r2, 1e-6, "stop 后 real 应冻结");
    }

    @Test
    void 未start时real为0() {
        assertEquals(0.0, clockService.assessmentRealElapsedSeconds("S-NONE"));
    }
}

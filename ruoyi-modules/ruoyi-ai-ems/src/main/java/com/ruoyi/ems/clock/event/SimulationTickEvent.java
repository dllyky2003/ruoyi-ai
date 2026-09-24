package com.ruoyi.ems.clock.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 仿真时钟滴答事件。
 *
 * <p>由 {@code ClockService.processSessionTick} 每秒发布一次（仅对活跃且
 * clockRunning=true 的会话）。</p>
 *
 * <p><b>同步发布</b>：Spring 默认同步派发，订阅方若耗时过长会阻塞 ClockService.tick()。
 * 螺旋 2 阶段订阅方（EvolutionRuleEvaluator）只做内存读写，量级为微秒，可接受。
 * 若未来订阅方增多，改 @Async 需同步评估线程安全。</p>
 */
@Data
@AllArgsConstructor
public class SimulationTickEvent {

    /** 业务会话 ID */
    private final String sessionId;

    /** 本次 tick 的墙上时间（毫秒） */
    private final long tickAt;

    /** 模拟已运行秒数（排除暂停） */
    private final int simulatedElapsed;

    /** 发布时刻的 CaseContext 版本号，供订阅方做幂等/一致性判断 */
    private final long stateRevision;
}

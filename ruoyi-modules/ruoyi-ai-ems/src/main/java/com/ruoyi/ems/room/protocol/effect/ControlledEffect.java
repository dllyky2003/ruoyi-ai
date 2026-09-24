package com.ruoyi.ems.room.protocol.effect;

/**
 * 契约 V1.0.0 §7.2 —— 已批准的受控效果接口。
 *
 * <p>导师覆盖不得直接写 ClinicalState，必须映射到这里注册的某个
 * {@link ControlledEffect} 实现。未注册的效果会被
 * {@code OverrideHandler} 拒绝（UNAUTHORIZED_OVERRIDE）。</p>
 *
 * <p><b>实现约束：</b></p>
 * <ul>
 *   <li>不得直接调用 {@code EmsContextManager} 以外的写路径</li>
 *   <li>不得直接改 ClinicalState 结构</li>
 *   <li>必须通过 {@code ClockService} 操作时钟</li>
 *   <li>执行结果必须可审计（OVR-3 落地）</li>
 * </ul>
 */
public interface ControlledEffect {

    /** 效果 ID（契约 §7.1 proposedEffect.effectId） */
    String getEffectId();

    /**
     * 执行效果。
     *
     * @param ctx 效果上下文（含 emsSessionId / attemptId / overrideId / reason）
     * @return 执行结果（accepted / message / stateRevision）
     */
    EffectResult apply(EffectContext ctx);
}

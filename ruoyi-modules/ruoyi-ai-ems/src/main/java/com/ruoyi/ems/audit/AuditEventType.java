package com.ruoyi.ems.audit;

/**
 * 契约 §12 —— 审计事件类型。写入 JSONL 时用 {@link #name()}。
 */
public enum AuditEventType {

    /** §7.2 导师覆盖 */
    TUTOR_OVERRIDE,

    /** §8 评分中断 */
    ASSESSMENT_INTERRUPTION,

    /** §10.3 时钟暂停 */
    CLOCK_PAUSE,

    /** §10.3 时钟恢复 */
    CLOCK_RESUME,

    /** §4 会话开始 */
    SESSION_START,

    /** §4 会话结束 */
    SESSION_END,

    /** §4 状态机跃迁 */
    STATE_TRANSITION;
}

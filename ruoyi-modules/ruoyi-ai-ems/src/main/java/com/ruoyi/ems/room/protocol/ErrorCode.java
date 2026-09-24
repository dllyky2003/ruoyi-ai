package com.ruoyi.ems.room.protocol;

/**
 * 契约 V1.0.0 §6 错误码。
 *
 * <p>客户端按此枚举做分支处理，字符串值一旦冻结不得改名。</p>
 */
public enum ErrorCode {

    /** 期望修订过期：丢弃本地推断，应用全量快照 */
    STALE_REVISION,

    /** 幂等重放：返回原结果，不重复执行 */
    IDEMPOTENT_REPLAY,

    /** 临床规则阻断：展示反馈，不推进状态 */
    BLOCKED_BY_RULE,

    /** 参数错误：要求澄清 / 纠错 */
    INVALID_PARAMETER,

    /** 导师覆盖未授权：拒绝并审计 */
    UNAUTHORIZED_OVERRIDE,

    /** 尝试未运行：拒绝改状态 */
    ATTEMPT_NOT_RUNNING,

    /** 序号断档：请求全量快照 */
    SEQUENCE_GAP,

    /** Schema 校验失败：拒绝并审计 */
    SCHEMA_REJECTED,

    /** 需要全量快照：请求并覆盖本地状态 */
    FULL_SNAPSHOT_REQUIRED
}

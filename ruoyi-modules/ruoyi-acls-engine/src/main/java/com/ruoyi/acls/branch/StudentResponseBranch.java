package com.ruoyi.acls.branch;

/**
 * 学员操作质量分支（螺旋 3 三态）。
 *
 * <p>定义学员在一次操作窗口内的行为质量，作为 EffectGate 的第三输入，
 * 与 {@code StateTransitionResult} 共同决定触发何种 Effect。</p>
 *
 * <ul>
 *   <li>{@link #NORMAL}  —— 窗口内执行正确意图，状态成功转移</li>
 *   <li>{@link #ERROR}   —— 执行错误意图，或被系统明确拒绝</li>
 *   <li>{@link #DELAYED} —— 窗口内未执行正确意图（含沉默与超时）</li>
 * </ul>
 *
 * <p>会话级语义：一个会话在同一时刻只有一个当前 branch，
 * 由 {@code StudentResponseResolver} 依据最近一次操作信号解析。</p>
 *
 * @see StudentResponseSignal
 * @see StudentResponseResolver
 */
public enum StudentResponseBranch {

    /** 正常操作：窗口内执行正确意图，状态成功转移。 */
    NORMAL,

    /** 错误操作：执行错误意图，或被系统明确拒绝。 */
    ERROR,

    /** 延时操作：窗口内未执行正确意图（含沉默与超时）。 */
    DELAYED;

    /**
     * 从字符串解析分支（大小写不敏感）。
     *
     * @param s 字符串（normal / error / delayed，大小写不限）
     * @return 对应枚举；无法识别或为 null 时返回 {@code null}
     */
    public static StudentResponseBranch from(String s) {
        if (s == null) {
            return null;
        }
        for (StudentResponseBranch b : values()) {
            if (b.name().equalsIgnoreCase(s)) {
                return b;
            }
        }
        return null;
    }
}

package com.ruoyi.acls.branch;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 学员操作分支信号（螺旋 3 三态）。
 *
 * <p>由 {@code AclsCommandEngine} 在三种判定点写入，供
 * {@link StudentResponseResolver} 解析会话当前分支：</p>
 *
 * <ul>
 *   <li>ALLOW + 状态转移成功 → {@link StudentResponseBranch#NORMAL}</li>
 *   <li>REJECT（处理器明确拒绝） → {@link StudentResponseBranch#ERROR}</li>
 *   <li>TIMEOUT（异常规则命中 TIMING） → {@link StudentResponseBranch#DELAYED}</li>
 * </ul>
 *
 * <p>本对象不可变；每次记录产生一个新实例，由 Resolver 按 session 覆盖持有。</p>
 *
 * @see StudentResponseBranch
 * @see StudentResponseResolver
 */
@Data
@AllArgsConstructor
public class StudentResponseSignal {

    /** 会话 ID。 */
    private final String sessionId;

    /** 本次操作判定的分支。 */
    private final StudentResponseBranch branch;

    /**
     * 触发本次判定的意图编码。
     * <p>TIMEOUT 场景可能为 {@code null}（无学员意图）。</p>
     */
    private final String intent;

    /** 判定时刻（{@code System.currentTimeMillis()}）。 */
    private final long at;

    /** 审计用原因说明（例如 "状态转移成功" / "处理器拒绝: xxx"）。 */
    private final String reason;
}

package com.ruoyi.acls.branch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学员操作分支解析器（螺旋 3 三态）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li><b>写入</b>：{@link #record} 由 {@code AclsCommandEngine} 在
 *       ALLOW / REJECT / TIMEOUT 三处调用，覆盖会话最近一次分支信号。</li>
 *   <li><b>解析</b>：{@link #resolve} 由 {@code SkillCompletionStateDriver}
 *       与 {@code EvolutionRuleEvaluator} 调用，返回当前有效分支。</li>
 * </ol>
 *
 * <p>降级规则：若最近一次信号为 {@link StudentResponseBranch#NORMAL}，
 * 但距该信号已超过操作窗口（默认 30 秒），则降级为
 * {@link StudentResponseBranch#DELAYED}。表示学员停止正确操作后，
 * 患者进入延时恶化通道。</p>
 *
 * <p>无信号时返回 {@link StudentResponseBranch#NORMAL}（患者默认稳定，
 * 未被学员操作影响）。</p>
 *
 * <p>★ 第一版说明：操作窗口暂用常量 {@link #DEFAULT_WINDOW_SEC}，
 * 未接入病例 YAML 的 {@code process.expectedInterventionWindow}。
 * 若后续需要在病例级配置窗口，在 {@link #resolve} 内注入
 * {@code EmsContextManager} 读取该变量即可，当前改动面最小。</p>
 *
 * @see StudentResponseBranch
 * @see StudentResponseSignal
 */
@Slf4j
@Component
public class StudentResponseResolver {

    /** 默认操作窗口（秒）：最近一次正常操作超过此窗口后降级为 DELAYED。 */
    private static final long DEFAULT_WINDOW_SEC = 30L;

    /** sessionId -> 最近一次分支信号。 */
    private final Map<String, StudentResponseSignal> latest = new ConcurrentHashMap<>();

    // ==================== 写入 ====================

    /**
     * 记录一次学员操作分支信号，覆盖该会话的旧信号。
     *
     * @param sessionId 会话 ID
     * @param branch    分支枚举
     * @param intent    触发本次判定的意图编码（可为 null，如 TIMEOUT）
     * @param reason    审计原因说明
     */
    public void record(String sessionId, StudentResponseBranch branch,
                       String intent, String reason) {
        if (sessionId == null || branch == null) {
            log.warn("[EffectGate][Branch] record 参数为空: session={}, branch={}",
                sessionId, branch);
            return;
        }
        StudentResponseSignal sig = new StudentResponseSignal(
            sessionId, branch, intent, System.currentTimeMillis(), reason);
        latest.put(sessionId, sig);
        log.info("[EffectGate][Branch] session={} branch={} intent={} reason={}",
            sessionId, branch, intent, reason);
    }

    // ==================== 解析 ====================

    /**
     * 解析会话当前有效分支。
     *
     * <p>返回值语义：</p>
     * <ul>
     *   <li>无信号 → {@link StudentResponseBranch#NORMAL}</li>
     *   <li>最近信号为 NORMAL 且在窗口内 → {@code NORMAL}</li>
     *   <li>最近信号为 NORMAL 但超窗 → {@link StudentResponseBranch#DELAYED}</li>
     *   <li>最近信号为 ERROR → {@code ERROR}</li>
     *   <li>最近信号为 DELAYED → {@code DELAYED}</li>
     * </ul>
     *
     * @param sessionId 会话 ID（为 null 时返回 {@code NORMAL}）
     * @return 当前有效分支，永不为 null
     */
    public StudentResponseBranch resolve(String sessionId) {
        if (sessionId == null) {
            return StudentResponseBranch.NORMAL;
        }
        StudentResponseSignal sig = latest.get(sessionId);
        if (sig == null) {
            // 无信号：患者默认稳定，尚未被学员影响
            return StudentResponseBranch.NORMAL;
        }
        StudentResponseBranch branch = sig.getBranch();
        if (branch == StudentResponseBranch.NORMAL) {
            long ageMs = System.currentTimeMillis() - sig.getAt();
            if (ageMs > DEFAULT_WINDOW_SEC * 1000L) {
                // 学员最后一次正确操作已超窗 → 降级为延时
                return StudentResponseBranch.DELAYED;
            }
        }
        return branch;
    }

    // ==================== 清理 ====================

    /**
     * 清理会话信号（会话结束时调用，避免内存泄漏）。
     *
     * @param sessionId 会话 ID
     */
    public void clear(String sessionId) {
        if (sessionId != null) {
            latest.remove(sessionId);
        }
    }
}

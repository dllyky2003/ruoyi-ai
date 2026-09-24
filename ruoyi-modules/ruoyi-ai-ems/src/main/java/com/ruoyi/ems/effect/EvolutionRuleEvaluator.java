package com.ruoyi.ems.effect;

import com.ruoyi.ems.clock.event.SimulationTickEvent;
import com.ruoyi.ems.config.loader.PhysiologyConfigLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.EvolutionRule;
import com.ruoyi.ems.domain.PhysiologyConfig;

import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import com.ruoyi.ems.spi.StudentResponseBranchProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生理演化规则评估器。
 *
 * <p>监听 {@link SimulationTickEvent}，按 {@code PhysiologyConfig.evolutionRules}
 * 周期性调整 {@code phys.*} 体征。</p>
 *
 * <p><b>与现有 YAML 的兼容</b>：</p>
 * <ul>
 *   <li>{@code rhythm} 大小写不敏感（YAML 里 "svt" / "SVT" 都匹配 phys.rhythm）</li>
 *   <li>{@code perTick} 缺省 = false（阶跃应用，与现有 YAML 语义一致）</li>
 *   <li>{@code delta} 在 perTick=false 时表示"一次性变化量"，不折算每秒</li>
 * </ul>
 *
 * <p><b>与 EffectManager 的关系</b>：</p>
 * <ul>
 *   <li>{@code EffectManager} 处理"学员操作 → 即时修正量"（activeEffects 列表）</li>
 *   <li>{@code EvolutionRuleEvaluator} 处理"时间流逝 → 体征演化"（直接写 phys.*）</li>
 *   <li>两者独立，互不依赖</li>
 * </ul>
 *
 * <p><b>★ 2026-09-23 螺旋 3 修改：branch 过滤</b>：</p>
 * <ul>
 *   <li>onTick 入口读取当前会话的学员操作分支（NORMAL / ERROR / DELAYED），
 *       见 {@link StudentResponseBranchProvider}。</li>
 *   <li>对每条规则：若 {@code rule.branch} 非空且与当前分支不匹配 → 跳过本规则。</li>
 *   <li>{@code rule.branch} 为 null / 空 → 对所有分支生效（向后兼容）。</li>
 *   <li>无法获取分支（provider 缺失或返回 null）→ 视为“无分支信号”，
 *       声明了 branch 的规则一律跳过，避免误伤。</li>
 * </ul>
 *
 * <p><b>★ 2026-09-23 螺旋 3 修复 P2-14：同 tick 累积写入</b>：</p>
 * <ul>
 *   <li>规则按 {@code afterSeconds} 升序执行，保证短延时先于长延时。</li>
 *   <li>本 tick 内的写入先累积到局部 {@code writes}，后续规则计算新值时
 *       优先从 {@code writes} 读取当前值，而不是从旧 {@code ctx} 读取。</li>
 *   <li>同一 session 的 tick 处理加锁串行化，避免并发覆盖。</li>
 * </ul>
 */
@Slf4j
@Component
public class EvolutionRuleEvaluator {

    private static final String WRITER = "PHYSIO_TICK";

    @Autowired private PhysiologyConfigLoader configLoader;
    @Autowired private EmsContextManager contextManager;
    @Autowired private SpELExpressionEvaluator spel;

    /**
     * ★ 螺旋 3：学员操作分支提供者。
     * acls-engine 模块未加载时为 null，按“无分支信号”处理。
     */
    @Autowired(required = false)
    private StudentResponseBranchProvider branchProvider;

    /** sessionId → 已永久停用的 ruleId 集合（stop_when 命中） */
    private final Map<String, Set<String>> stoppedRules = new ConcurrentHashMap<>();

    /** sessionId → 已一次性应用过的 ruleId 集合（perTick=false） */
    private final Map<String, Set<String>> appliedOnceRules = new ConcurrentHashMap<>();

    /** sessionId → 串行化锁，避免同一 session 的 tick 并发执行 */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    // ============================================================
    // 主入口：监听 tick
    // ============================================================

    @EventListener
    public void onTick(SimulationTickEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) return;

        // ★ P2-14：同一 session 串行化，防止并发读旧值
        Object lock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            doOnTick(event);
        }
    }

    private void doOnTick(SimulationTickEvent event) {
        String sessionId = event.getSessionId();
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null || !ctx.isClockRunning()) return;

        String caseId = ctx.getCaseType();
        if (caseId == null || caseId.isBlank()) return;

        PhysiologyConfig cfg = configLoader.loadCase(caseId);
        if (cfg == null || cfg.getEvolutionRules() == null || cfg.getEvolutionRules().isEmpty()) {
            return;
        }

        String currentRhythm = readString(ctx, "phys.rhythm");

        // ★ 螺旋 3：读取当前学员操作分支
        String currentBranch = (branchProvider != null)
            ? branchProvider.resolveBranch(sessionId)
            : null;
        log.debug("[Physio][Branch] session={} branch={}", sessionId, currentBranch);

        Set<String> stopped = stoppedRules.computeIfAbsent(
            sessionId, k -> ConcurrentHashMap.newKeySet());
        Set<String> appliedOnce = appliedOnceRules.computeIfAbsent(
            sessionId, k -> ConcurrentHashMap.newKeySet());

        // ★ P2-14：本 tick 累积写入，后续规则优先从此读取
        Map<String, Object> writes = new LinkedHashMap<>();

        // ★ P2-14：规则按 afterSeconds 升序，保证短延时先执行
        List<EvolutionRule> rules = new ArrayList<>(cfg.getEvolutionRules());
        rules.sort(Comparator.comparingLong(EvolutionRule::getAfterSeconds));

        for (EvolutionRule rule : rules) {
            String ruleId = resolveRuleId(rule);

            // 0. 已永久停用
            if (stopped.contains(ruleId)) continue;

            // ★ 0.5 螺旋 3：branch 过滤
            if (!branchMatches(rule, currentBranch, sessionId)) {
                continue;
            }

            // 1. rhythm 匹配（大小写不敏感）
            if (rule.getRhythm() != null && !rule.getRhythm().isBlank()
                && !rule.getRhythm().equalsIgnoreCase(currentRhythm)) {
                continue;
            }

            // 2. afterSeconds 阈值（用会话级 simulatedElapsed）
            if (event.getSimulatedElapsed() < rule.getAfterSeconds()) continue;

            // 3. perTick=false 且已应用过 → 跳过
            if (!Boolean.TRUE.equals(rule.getPerTick()) && appliedOnce.contains(ruleId)) {
                continue;
            }

            // 4. applies_when 额外条件（SpEL）
            if (rule.getAppliesWhen() != null && !rule.getAppliesWhen().isBlank()
                && !evaluate(rule.getAppliesWhen(), ctx)) {
                continue;
            }

            // 5. stop_when 检查（命中即永久停用）
            if (rule.getStopWhen() != null && !rule.getStopWhen().isBlank()
                && evaluate(rule.getStopWhen(), ctx)) {
                stopped.add(ruleId);
                log.info("[Physio] rule={} stop_when 命中，永久停用: session={}",
                    ruleId, sessionId);
                continue;
            }

            // 6. 计算新值（★ P2-14：传入 writes，优先读累积值）
            String varName = rule.getVariable();
            if (varName == null || varName.isBlank()) continue;

            Object newValue = computeNewValue(rule, ctx, varName, writes);
            if (newValue == null) continue;

            // ★ P2-14：取当前值用于日志与比较，优先从 writes 取
            Object oldValue = writes.getOrDefault(varName, ctx.getVariables().get(varName));
            writes.put(varName, newValue);   // 始终累积，供后续规则读取

            if (!Objects.equals(oldValue, newValue)) {
                log.info("[Physio] rule={} {} {} → {}: session={}",
                    ruleId, varName, oldValue, newValue, sessionId);
            }

            // 7. perTick=false：首次命中即标记已应用（无论是否真的写入）
            if (!Boolean.TRUE.equals(rule.getPerTick())) {
                appliedOnce.add(ruleId);
            }
        }

        if (!writes.isEmpty()) {
            boolean ok = contextManager.batchSetVarsWithVersion(
                sessionId, writes, WRITER, ctx.getVersion());
            if (!ok) {
                log.warn("[Physio] 写回失败（版本冲突）: session={}, vars={}",
                    sessionId, writes.keySet());
            }
        }
    }

    // ============================================================
    // ★ 螺旋 3：branch 过滤
    // ============================================================

    /**
     * 判断规则的 branch 声明是否与当前分支匹配。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>{@code rule.branch} 为 null / 空 → 对所有分支生效，返回 true。</li>
     *   <li>{@code rule.branch} 非空且 {@code currentBranch == null} → 无分支信号，
     *       返回 false（保守跳过，避免误伤）。</li>
     *   <li>二者非空 → 大小写不敏感比较，相等返回 true。</li>
     * </ul>
     */
    private boolean branchMatches(EvolutionRule rule, String currentBranch, String sessionId) {
        String ruleBranch = rule.getBranch();
        if (ruleBranch == null || ruleBranch.isBlank()) {
            return true;   // 规则未声明 branch → 对所有分支生效
        }

        // ★ 语义对齐：对演化规则而言，无信号 = 学员尚未行动 = DELAYED
        //   与 SkillCompletionStateDriver（无信号 = NORMAL，给予技能完成 credit）区分。
        String effective = (currentBranch == null || currentBranch.isBlank())
            ? "DELAYED"
            : currentBranch;

        boolean match = ruleBranch.equalsIgnoreCase(effective);
        if (match) {
            log.debug("[Physio][Branch] rule={} branch={} 匹配: session={}",
                resolveRuleId(rule), effective, sessionId);
        }
        return match;
    }

    // ============================================================
    // 计算新值
    // ============================================================

    /**
     * 按 rule.operation / rule.delta 计算新值。
     *
     * <p>★ P2-14：读取当前值时优先从本 tick 的 {@code writes} 取，
     * 保证同 tick 内后续规则能看到前序规则的写入。</p>
     *
     * <p>返回 null 表示"无法计算，跳过"。返回类型统一为 Double（数值型）
     * 或 rule.value（set 操作），避免 Integer/Double 混用导致 getVar 读取异常。</p>
     */
    private Object computeNewValue(EvolutionRule rule, CaseContext ctx,
                                   String varName, Map<String, Object> writes) {
        String op = rule.getOperation();

        // set 操作：直接返回配置值
        if ("set".equalsIgnoreCase(op)) {
            return rule.getValue();
        }

        // 判断加减方向
        boolean isSubtract;
        if ("subtract".equalsIgnoreCase(op)) {
            isSubtract = true;
        } else if ("add".equalsIgnoreCase(op)) {
            isSubtract = false;
        } else {
            Float d = rule.getDelta();
            if (d == null) return null;
            isSubtract = d < 0;
        }

        // ★ P2-14：优先从 writes 读当前值，无则回退到 ctx
        Number n = null;
        Object accumulated = writes.get(varName);
        if (accumulated instanceof Number) {
            n = (Number) accumulated;
        } else {
            n = ctx.getVar(varName, Number.class);
        }
        if (n == null) {
            log.debug("[Physio] 变量 {} 无当前值，跳过: rule={}", varName, rule.getId());
            return null;
        }
        double current = n.doubleValue();

        Float delta = rule.getDelta();
        double step = (delta != null) ? Math.abs(delta.doubleValue()) : 0.0;
        if (step == 0.0) return null;

        double next = isSubtract ? current - step : current + step;

        // bounds 截断
        if (rule.getBounds() != null) {
            Double min = rule.getBounds().getMin();
            Double max = rule.getBounds().getMax();
            if (min != null && next < min) next = min;
            if (max != null && next > max) next = max;
        }

        return next;
    }

    // ============================================================
    // 工具
    // ============================================================

    /**
     * 解析 ruleId；未提供时按 rhythm + variable + afterSeconds 生成稳定 key。
     */
    private String resolveRuleId(EvolutionRule rule) {
        if (rule.getId() != null && !rule.getId().isBlank()) {
            return rule.getId();
        }
        return String.format("%s:%s:%d",
            rule.getRhythm() != null ? rule.getRhythm() : "*",
            rule.getVariable() != null ? rule.getVariable() : "?",
            rule.getAfterSeconds());
    }

    /**
     * SpEL 求值，异常时返回 false（保守）。
     *
     * <p>注意：applies_when / stop_when 中除 phys/process/intervention/
     * device/clinical/derived 之外的变量，一律走 {@code #variables['xxx']} 键访问，
     * 否则会抛 SpelEvaluationException。</p>
     */
    private boolean evaluate(String expression, CaseContext ctx) {
        try {
            return spel.evaluateCondition(expression, ctx);
        } catch (Exception e) {
            log.warn("[Physio] SpEL 求值异常: expr={}, err={}", expression, e.getMessage());
            return false;
        }
    }

    private String readString(CaseContext ctx, String key) {
        Object v = ctx.getVariables().get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * 会话结束时清理，避免内存泄漏。
     * <p>螺旋 2 阶段暂由 CaseEndListener 手动调用；螺旋 3 可挂事件自动触发。</p>
     */
    public void clearSession(String sessionId) {
        stoppedRules.remove(sessionId);
        appliedOnceRules.remove(sessionId);
        sessionLocks.remove(sessionId);
    }
}

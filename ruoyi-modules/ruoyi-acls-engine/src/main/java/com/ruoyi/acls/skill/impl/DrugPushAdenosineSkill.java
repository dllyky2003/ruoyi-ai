package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.ems.clock.DrugClockService;
import com.ruoyi.ems.constant.InterventionFields;
import com.ruoyi.ems.constant.InterventionKeys;
import com.ruoyi.ems.constant.ProcessKeys;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.effect.EffectManager;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 腺苷推注专用技能（继承通用 {@link DrugPushSkill}）。
 *
 * <p>★ 2026-09-24 螺旋 4 G4-06 第三步：腺苷二次推药 WARN。</p>
 *
 * <p>职责分工：</p>
 * <ul>
 *   <li>推注本身逻辑完全复用父类 {@code DrugPushSkill}（VR 动画、
 *       intervention.adenosine read-modify-write、事件日志、
 *       intervention.lastDrug* 等扁平量）；</li>
 *   <li>本类只新增一件事：<b>若本次推注落在上一次腺苷推注的 60s 观察窗内，
 *       下发 WARN 反馈文案 {@code feedback_adenosine_recheck_warn}</b>，
 *       但不阻断 effect（branch 不变，adenosine_12mg 照常触发）。</li>
 * </ul>
 *
 * <p><b>为什么在 {@link #applyMapChanges} 里判定？</b></p>
 * <p>父类 {@code execute()} 是 final，流程是：写 Map → 写扁平量 → 写日志 →
 * {@code afterSuccessfulExecution}。当 {@code afterSuccessfulExecution} 被调用时，
 * {@code intervention.adenosine.lastTime} 已经被本次推注覆盖，无法再判断"是否在窗口内"。
 * 因此必须在 Map 写入之前先读旧 {@code lastTime}、判定窗口，并把结果暂存到
 * {@code slots}（会话局部数据通道）里，供后续步骤读取。</p>
 */
@Slf4j
public class DrugPushAdenosineSkill extends DrugPushSkill {

    /** WARN 反馈文案 key，前端/推送层据此渲染实际文案。 */
    public static final String FEEDBACK_ADENOSINE_RECHECK_WARN =
        "feedback_adenosine_recheck_warn";

    /** slots 内部字段名（下划线前缀 + 业务语义，避免与真实槽位冲突）。 */
    private static final String SLOT_WITHIN_ADENOSINE_WINDOW = "__withinAdenosineWindow";

    private final DrugClockService drugClockService;
    private final EmsContextManager contextManager;

    public DrugPushAdenosineSkill(EffectManager effectManager,
                                  MapWriteHelper helper,
                                  DrugClockService drugClockService,
                                  EmsContextManager contextManager) {
        super(effectManager, helper, "adenosine");
        this.drugClockService = drugClockService;
        this.contextManager = contextManager;
    }

    // ====================================================================
    // ★ 在写 Map 之前判定窗口，把结果暂存到 slots
    // ====================================================================
    @Override
    protected void applyMapChanges(Map<String, Object> m, Map<String, Object> slots) {
        // 1. 读旧 lastTime（在 super.applyMapChanges 覆盖它之前）
        Object oldLastTimeObj = m.get(InterventionFields.LAST_TIME);
        Long oldLastTime = (oldLastTimeObj instanceof Number n) ? n.longValue() : null;

        // 2. 判定是否落在 60s 窗口内
        boolean withinWindow = false;
        if (oldLastTime != null) {
            long elapsed = System.currentTimeMillis() - oldLastTime;
            withinWindow = elapsed > 0
                && elapsed < DrugClockService.ADENOSINE_RECHECK_WINDOW_MS;
            if (withinWindow) {
                log.warn("⚠️ [EffectGate][AdenosineRecheck] 60s 内二次推注腺苷，不阻断 effect: "
                        + "oldLastTime={}, elapsed={}ms",
                    oldLastTime, elapsed);
            }
        }
        slots.put(SLOT_WITHIN_ADENOSINE_WINDOW, withinWindow);

        // 3. 交给父类完成常规 Map 修改（覆盖 lastTime、count++、totalDose += 等）
        super.applyMapChanges(m, slots);
    }

    // ====================================================================
    // ★ 推注成功后，若在窗口内则写 WARN 变量
    // ====================================================================
    @Override
    protected void afterSuccessfulExecution(CommandContext context,
                                            Map<String, Object> slots) {
        // 保留父类的 debug 日志
        super.afterSuccessfulExecution(context, slots);

        if (!Boolean.TRUE.equals(slots.get(SLOT_WITHIN_ADENOSINE_WINDOW))) {
            return;   // 不在窗口内，无 WARN
        }

        String sessionId = context.getSessionId();
        publishAdenosineRecheckWarn(sessionId);
    }
    @Override
    public Map<String, Object> getSuccessOutputs() {
        Map<String, Object> out = new LinkedHashMap<>();

        out.put(InterventionKeys.INTERVENTION_ADENOSINE_GIVEN, true);

        String adeno = InterventionKeys.INTERVENTION_ADENOSINE;
        out.put(adeno + InterventionFields.SEP + InterventionFields.STATUS, "given");
        out.put(adeno + InterventionFields.SEP + InterventionFields.FLUSH_DONE, false);

        out.put(InterventionKeys.INTERVENTION_LAST_DRUG, "adenosine");
        out.put(InterventionKeys.INTERVENTION_LAST_DRUG_DOSE,
            "#" + InterventionKeys.INTERVENTION_LAST_DRUG_DOSE);
        out.put(InterventionKeys.INTERVENTION_LAST_DRUG_ROUTE,
            "#" + InterventionKeys.INTERVENTION_LAST_DRUG_ROUTE);

        out.put(ProcessKeys.PROCESS_LAST_INTERVENTION_TIME, "NOW");

        return out;
    }
    /**
     * 下发「60s 内二次推注腺苷」WARN 反馈。
     *
     * <p>走变量通道：把 message key 写入 {@code process.tutorWarning}，
     * 由状态推送（ProtocolBridgeService / Unity）顺带带出，供前端展示。</p>
     *
     * <p>不阻塞、不抛异常：即使写入失败，也只 WARN 日志，不影响 effect 触发。</p>
     */
    private void publishAdenosineRecheckWarn(String sessionId) {
        try {
            long now = System.currentTimeMillis();
            Map<String, Object> warnVars = new LinkedHashMap<>();
            warnVars.put("process.tutorWarning", FEEDBACK_ADENOSINE_RECHECK_WARN);
            warnVars.put("process.tutorWarningAt", now);

            // 重新取最新快照的 version，避免必然的版本冲突
            CaseContext latest = contextManager.getSnapshot(sessionId);
            long expectedVersion = (latest != null) ? latest.getVersion() : 0L;

            boolean ok = contextManager.batchSetVarsWithVersion(
                sessionId, warnVars, "TUTOR_WARN", expectedVersion);

            if (ok) {
                log.info("⚠️ [AdenosineRecheckWarn] feedback={}, session={}, at={}",
                    FEEDBACK_ADENOSINE_RECHECK_WARN, sessionId, now);
            } else {
                log.warn("⚠️ [AdenosineRecheckWarn] 变量写入版本冲突，WARN 未持久化: "
                    + "session={}, expectedVersion={}", sessionId, expectedVersion);
            }
        } catch (Exception e) {
            log.warn("下发腺苷 WARN 反馈失败: session={}, err={}",
                sessionId, e.getMessage());
        }
    }
}

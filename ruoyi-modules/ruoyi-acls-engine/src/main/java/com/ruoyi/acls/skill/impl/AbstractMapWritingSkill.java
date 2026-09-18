package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.constant.InterventionFields;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamMember;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * "写 Map 型变量 + 追加事件日志" 类技能的通用基类。
 *
 * 与 StandardActionSkill 的区别：
 *   - StandardActionSkill 只写 1 个扁平 successVar
 *   - 本基类支持 Map 型变量（read-modify-write）+ 多个扁平量 + case.eventLog 追加
 *
 * 子类只需实现 7 个抽象方法；统一流程（VR 等待、语音、Map 写入、
 * 事件追加、超时保护、错误返回）由基类提供。
 */
@Slf4j
public abstract class AbstractMapWritingSkill extends AbstractEmsSkill {

    protected final MapWriteHelper mapWriteHelper;

    protected AbstractMapWritingSkill(String keystepId, Duty duty,
                                      MapWriteHelper mapWriteHelper) {
        super(keystepId, duty);
        this.mapWriteHelper = mapWriteHelper;
    }

    // ====================================================================
    // 子类必须实现（7 个抽象方法）
    // ====================================================================

    /** VR 动作码，如 "SYNC_CARDIOVERSION" */
    protected abstract String getVrActionCode();

    /** 目标 Map 变量名（如 intervention.cardioversion），可随 slots 变化 */
    protected abstract String getTargetMapKey(Map<String, Object> slots);

    /** 修改 Map 副本 —— 子类在这里 m.put(...)，基类负责 read-modify-write */
    protected abstract void applyMapChanges(Map<String, Object> map,
                                            Map<String, Object> slots);

    /** 事件日志 code，如 "CARDIOVERSION" */
    protected abstract String getEventCode(Map<String, Object> slots);

    /** 事件日志 params（可为 null） */
    protected abstract Map<String, Object> buildEventParams(Map<String, Object> slots);

    /** VR 动作时长（秒） */
    protected abstract int getDurationSeconds();

    /** VR 动作成功时的语音 */
    protected abstract String getSuccessSpeech(Map<String, Object> slots);

    // ====================================================================
    // 子类可选覆盖
    // ====================================================================

    /** VR 动作参数；默认把 slots 原样传 */
    protected Map<String, Object> getVrActionParams(Map<String, Object> slots) {
        return slots;
    }

    /** VR 动作超时秒数，默认 duration + 5 */
    protected int getTimeoutSeconds() {
        return getDurationSeconds() + 5;
    }

    /** 确认语音；默认 "开始执行 {keystepId}" */
    protected String getConfirmSpeech(Map<String, Object> slots) {
        return "开始执行 " + keystepId;
    }

    /** 汇报语音；默认 "{keystepId} 已完成" */
    protected String getReportSpeech(Map<String, Object> slots) {
        return keystepId + " 已完成";
    }

    /** 额外参数校验；默认通过 */
    protected CommandResult validateExtra(CommandContext context,
                                          Map<String, Object> slots) {
        return CommandResult.success("参数校验通过");
    }

    /** 额外扁平量写入（如 device.lastShockEnergy）；默认无 */
    protected Map<String, Object> buildExtraFlatWrites(Map<String, Object> slots) {
        return Map.of();
    }

    /** 成功后的额外副作用（如 EffectManager 触发药物效果）；默认无 */
    protected void afterSuccessfulExecution(CommandContext context,
                                            Map<String, Object> slots) {
    }

    // ====================================================================
    // 统一 execute 流程 —— 子类不应覆盖
    // ====================================================================

    @Override
    public final CommandResult validateParams(CommandContext context,
                                              Map<String, Object> slots) {
        return validateExtra(context, slots);
    }

    @Override
    public final CommandResult execute(CommandContext context,
                                       Map<String, Object> slots,
                                       TeamMember executor) {
        String sessionId = context.getSessionId();

        // 1. 确认语音
        sendConfirmSpeech(context, executor, getConfirmSpeech(slots));

        // 2. VR 动作（阻塞等待）
        CompletableFuture<Void> future = context.getVrClientService().sendAction(
            sessionId,
            getVrActionCode(),
            getVrActionParams(slots),
            getSuccessSpeech(slots),
            getDurationSeconds());

        try {
            future.get(getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return CommandResult.failure(keystepId + " 动作执行超时");
        } catch (Exception e) {
            log.error("{} VR 任务异常: {}", keystepId, e.getMessage());
            return CommandResult.failure(keystepId + " 动作执行失败: " + e.getMessage());
        }

        // 3. Map read-modify-write
        String mapKey = getTargetMapKey(slots);
        if (mapKey != null && !mapKey.isBlank()) {
            boolean ok = mapWriteHelper.mutateMap(sessionId, mapKey, "EXECUTOR",
                m -> applyMapChanges(m, slots));
            if (!ok) {
                log.warn("⚠️ {} 写入 Map 失败（不影响 VR 动画）: {}", keystepId, mapKey);
            }
        }

        // 4. 额外扁平量
        Map<String, Object> flat = buildExtraFlatWrites(slots);
        if (flat != null && !flat.isEmpty()) {
            mapWriteHelper.setFlatVars(sessionId, flat, "EXECUTOR");
        }

        // 5. 事件日志
        mapWriteHelper.appendEvent(
            sessionId, getEventCode(slots), "EXECUTOR", buildEventParams(slots));

        // 6. 副作用钩子
        afterSuccessfulExecution(context, slots);

        // 7. 汇报语音
        sendReportSpeech(context, executor, getReportSpeech(slots));

        log.info("✅ {} 执行完成 (executor={})", keystepId, executor.getName());
        return CommandResult.success(keystepId + " 完成");
    }

    // ====================================================================
    // 默认 getQueryResponse：读目标 Map 的 count/lastTime
    // 子类如要读扁平量可覆盖
    // ====================================================================

    @Override
    public String getQueryResponse(CommandContext context) {
        String mapKey = getTargetMapKey(Map.of());
        if (mapKey == null || mapKey.isBlank()) {
            return "尚未执行 " + keystepId;
        }
        Object map = context.getCaseContext().getVar(mapKey, Map.class);
        if (!(map instanceof Map<?, ?> m) || m.isEmpty()) {
            return "尚未执行 " + keystepId;
        }
        int count = toInt(m.get(InterventionFields.COUNT));
        if (count == 0) {
            return "尚未执行 " + keystepId;
        }
        return "已执行 " + count + " 次 " + keystepId;
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    protected static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    protected static double toDouble(Object v) {
        if (v == null) return 0d;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0d; }
    }

    protected static long nowMs() {
        return System.currentTimeMillis();
    }
}

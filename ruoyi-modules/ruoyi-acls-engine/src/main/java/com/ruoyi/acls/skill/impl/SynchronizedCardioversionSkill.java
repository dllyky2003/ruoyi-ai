package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.constant.InterventionFields;
import com.ruoyi.ems.constant.InterventionKeys;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.npc.Duty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步电复律（stable_narrow 及后续案例通用）。
 *
 * slots:
 *   - energy:    Integer，焦耳（50-200）
 *   - emergency: Boolean
 */
public class SynchronizedCardioversionSkill extends AbstractMapWritingSkill {

    public SynchronizedCardioversionSkill(MapWriteHelper helper) {
        super("synchronized_cardioversion", Duty.MONITOR_DEFIB, helper);
    }

    // ============ 基类抽象方法 ============

    @Override protected String getVrActionCode() { return "SYNC_CARDIOVERSION"; }

    @Override protected String getTargetMapKey(Map<String, Object> slots) {
        return InterventionKeys.INTERVENTION_CARDIOVERSION;
    }

    @Override protected void applyMapChanges(Map<String, Object> m, Map<String, Object> slots) {
        int energy = toInt(slots.get("energy"));
        boolean emergency = Boolean.parseBoolean(
            String.valueOf(slots.getOrDefault("emergency", "false")));

        m.put(InterventionFields.STATUS,    "performed");
        m.put(InterventionFields.LAST_TIME, nowMs());
        m.put(InterventionFields.COUNT,     toInt(m.get(InterventionFields.COUNT)) + 1);
        m.put("lastEnergy", energy);
        m.put("emergency",  emergency);
    }

    @Override protected String getEventCode(Map<String, Object> slots) { return "CARDIOVERSION"; }

    @Override protected Map<String, Object> buildEventParams(Map<String, Object> slots) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("energy",    slots.get("energy"));
        p.put("sync",      true);
        p.put("emergency", slots.getOrDefault("emergency", false));
        return p;
    }

    @Override protected int getDurationSeconds() { return 3; }

    @Override protected String getSuccessSpeech(Map<String, Object> slots) {
        return "同步电复律 " + toInt(slots.get("energy")) + "J";
    }

    // ============ 可选覆盖 ============

    @Override protected String getConfirmSpeech(Map<String, Object> slots) {
        int energy = toInt(slots.get("energy"));
        boolean emergency = Boolean.parseBoolean(
            String.valueOf(slots.getOrDefault("emergency", "false")));
        return "同步电复律 " + energy + "J" + (emergency ? "（紧急）" : "");
    }

    @Override protected String getReportSpeech(Map<String, Object> slots) {
        return "同步电复律 " + toInt(slots.get("energy")) + "J 已完成";
    }

    @Override protected CommandResult validateExtra(CommandContext ctx, Map<String, Object> slots) {
        Object energyObj = slots.get("energy");
        if (energyObj == null) return CommandResult.failure("缺少能量参数（energy）");
        int energy;
        try { energy = Integer.parseInt(energyObj.toString()); }
        catch (NumberFormatException e) { return CommandResult.failure("能量值非法: " + energyObj); }
        if (energy < 50 || energy > 200)
            return CommandResult.failure("同步电复律能量应在 50-200J，当前 " + energy + "J");
        return CommandResult.success("参数校验通过");
    }

    @Override protected Map<String, Object> buildExtraFlatWrites(Map<String, Object> slots) {
        int energy = toInt(slots.get("energy"));
        return Map.of(
            "device.lastShockEnergy", energy,
            "device.lastShockSync",   true
        );
    }
}

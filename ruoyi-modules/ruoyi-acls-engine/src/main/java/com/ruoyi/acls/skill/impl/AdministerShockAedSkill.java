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
 * AED / 除颤（心搏骤停及后续案例通用）。
 *
 * slots:
 *   - energy: Integer，焦耳（100-360）
 *   - sync:   Boolean（AED 通常 false）
 */
public class AdministerShockAedSkill extends AbstractMapWritingSkill {

    public AdministerShockAedSkill(MapWriteHelper helper) {
        super("administer_shock_aed", Duty.MONITOR_DEFIB, helper);
    }

    // ============ 基类抽象方法 ============

    @Override protected String getVrActionCode() { return "DEFIB_SHOCK"; }

    @Override protected String getTargetMapKey(Map<String, Object> slots) {
        return InterventionKeys.INTERVENTION_DEFIB;
    }

    @Override protected void applyMapChanges(Map<String, Object> m, Map<String, Object> slots) {
        int energy = toInt(slots.get("energy"));
        boolean sync = Boolean.parseBoolean(
            String.valueOf(slots.getOrDefault("sync", "false")));

        m.put(InterventionFields.STATUS,    "performed");
        m.put(InterventionFields.LAST_TIME, nowMs());
        m.put(InterventionFields.COUNT,     toInt(m.get(InterventionFields.COUNT)) + 1);
        m.put("lastEnergy", energy);
        m.put("lastSync",   sync);
    }

    @Override protected String getEventCode(Map<String, Object> slots) { return "DEFIB_SHOCK"; }

    @Override protected Map<String, Object> buildEventParams(Map<String, Object> slots) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("energy", slots.get("energy"));
        p.put("sync",   slots.getOrDefault("sync", false));
        return p;
    }

    @Override protected int getDurationSeconds() { return 3; }

    @Override protected String getSuccessSpeech(Map<String, Object> slots) {
        return "电击除颤 " + toInt(slots.get("energy")) + "J";
    }

    // ============ 可选覆盖 ============

    @Override protected String getConfirmSpeech(Map<String, Object> slots) {
        int energy = toInt(slots.get("energy"));
        boolean sync = Boolean.parseBoolean(
            String.valueOf(slots.getOrDefault("sync", "false")));
        return (sync ? "同步" : "非同步") + "电击 " + energy
            + "J，请所有人离开患者";
    }

    @Override protected String getReportSpeech(Map<String, Object> slots) {
        return "电击 " + toInt(slots.get("energy"))
            + "J 已释放，请立即恢复胸外按压";
    }

    @Override protected CommandResult validateExtra(CommandContext ctx, Map<String, Object> slots) {
        Object energyObj = slots.get("energy");
        if (energyObj == null) return CommandResult.failure("缺少能量参数（energy）");
        int energy;
        try { energy = Integer.parseInt(energyObj.toString()); }
        catch (NumberFormatException e) { return CommandResult.failure("能量值非法: " + energyObj); }
        if (energy < 100 || energy > 360)
            return CommandResult.failure("除颤能量应在 100-360J，当前 " + energy + "J");
        return CommandResult.success("参数校验通过");
    }

    @Override protected Map<String, Object> buildExtraFlatWrites(Map<String, Object> slots) {
        int energy = toInt(slots.get("energy"));
        boolean sync = Boolean.parseBoolean(
            String.valueOf(slots.getOrDefault("sync", "false")));
        return Map.of(
            "device.lastShockEnergy", energy,
            "device.lastShockSync",   sync
        );
    }
}

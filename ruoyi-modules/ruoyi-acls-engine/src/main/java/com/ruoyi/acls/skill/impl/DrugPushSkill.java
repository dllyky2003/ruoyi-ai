package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.constant.InterventionFields;
import com.ruoyi.ems.constant.InterventionKeys;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.domain.Drugs;
import com.ruoyi.ems.effect.EffectManager;
import com.ruoyi.ems.npc.Duty;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.ruoyi.ems.domain.DrugEnum.ADENOSINE;

/**
 * 通用药物推注技能（16 种药物共用）。
 *
 * 参数化：keystepId = "drug_push_" + boundDrugCode
 *   new DrugPushSkill(em, helper, "epinephrine")  → drug_push_epinephrine
 *   new DrugPushSkill(em, helper, "adenosine")    → drug_push_adenosine
 */
public class DrugPushSkill extends AbstractMapWritingSkill {

    private final EffectManager effectManager;
    private final String boundDrugCode;

    public DrugPushSkill(EffectManager effectManager,
                         MapWriteHelper helper,
                         String boundDrugCode) {
        super(boundDrugCode != null ? "drug_push_" + boundDrugCode : "drug_push",
            Duty.IV_MED, helper);
        this.effectManager = effectManager;
        this.boundDrugCode = boundDrugCode;
    }

    // ============ 基类抽象方法 ============

    @Override protected String getVrActionCode() { return "IV_PUSH"; }

    @Override protected Map<String, Object> getVrActionParams(Map<String, Object> slots) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("drug", resolveDrug(slots));
        p.put("dose", slots.getOrDefault("dose", ""));
        return p;
    }

    @Override protected String getTargetMapKey(Map<String, Object> slots) {
        return Drugs.getInterventionVarKey(resolveDrug(slots));
    }

    @Override protected void applyMapChanges(Map<String, Object> m, Map<String, Object> slots) {
        String dose  = String.valueOf(slots.getOrDefault("dose", ""));
        String route = String.valueOf(slots.getOrDefault("route", "IV"));
        double doseValue = Drugs.parseDoseValue(dose);

        m.put(InterventionFields.STATUS,     "given");
        m.put(InterventionFields.LAST_TIME,  nowMs());
        m.put(InterventionFields.LAST_DOSE,  dose);
        m.put(InterventionFields.LAST_ROUTE, route);
        m.put(InterventionFields.COUNT,      toInt(m.get(InterventionFields.COUNT)) + 1);
        m.put(InterventionFields.TOTAL_DOSE,
            toDouble(m.get(InterventionFields.TOTAL_DOSE)) + doseValue);
    }

    @Override protected String getEventCode(Map<String, Object> slots) {
        return resolveDrug(slots).toUpperCase() + "_PUSH";
    }

    @Override protected Map<String, Object> buildEventParams(Map<String, Object> slots) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("drug",  resolveDrug(slots));
        p.put("dose",  slots.getOrDefault("dose", ""));
        p.put("route", slots.getOrDefault("route", "IV"));
        return p;
    }

    @Override protected int getDurationSeconds() { return 2; }

    @Override protected String getSuccessSpeech(Map<String, Object> slots) {
        return "快速推注 " + resolveDrug(slots) + " " + slots.getOrDefault("dose", "");
    }

    // ============ 可选覆盖 ============

    /** 药物技能读扁平量（intervention.lastDrug），覆盖基类默认的 Map 读 */
    @Override public String getQueryResponse(CommandContext context) {
        String drug = context.getCaseContext()
            .getVar(InterventionKeys.INTERVENTION_LAST_DRUG, String.class);
        String dose = context.getCaseContext()
            .getVar(InterventionKeys.INTERVENTION_LAST_DRUG_DOSE, String.class);
        if (drug == null || drug.isEmpty()) return "尚未执行药物推注，请组长指示。";
        Long lastTime = context.getCaseContext()
            .getVar(InterventionKeys.INTERVENTION_LAST_DRUG_TIME, Long.class);
        String timeDesc = (lastTime != null)
            ? "（" + (System.currentTimeMillis() - lastTime) / 1000 + "秒前）" : "";
        return drug + " " + dose + " 已完成推注" + timeDesc;
    }

    @Override protected String getConfirmSpeech(Map<String, Object> slots) {
        return "准备推注 " + resolveDrug(slots) + " " + slots.getOrDefault("dose", "");
    }

    @Override protected String getReportSpeech(Map<String, Object> slots) {
        return resolveDrug(slots) + " " + slots.getOrDefault("dose", "") + " 推注已完成";
    }

    @Override protected CommandResult validateExtra(CommandContext context, Map<String, Object> slots) {
        String drug = resolveDrug(slots);
        String dose = String.valueOf(slots.getOrDefault("dose", ""));
        String caseType = context.getCaseContext().getCaseType();

        String err = Drugs.validateDose(drug, dose, caseType);
        if (err != null) return CommandResult.failure(err);

        if (ADENOSINE.equals(drug)) {
            double doseValue = Drugs.parseDoseValue(dose);
            if (doseValue != 6 && doseValue != 12)
                return CommandResult.failure("腺苷剂量应为 6mg 或 12mg，当前为 " + dose);
        }
        return CommandResult.success("剂量校验通过");
    }

    @Override protected Map<String, Object> buildExtraFlatWrites(Map<String, Object> slots) {
        return Map.of(
            InterventionKeys.INTERVENTION_LAST_DRUG,       resolveDrug(slots),
            InterventionKeys.INTERVENTION_LAST_DRUG_DOSE,  String.valueOf(slots.getOrDefault("dose", "")),
            InterventionKeys.INTERVENTION_LAST_DRUG_ROUTE, String.valueOf(slots.getOrDefault("route", "IV")),
            InterventionKeys.INTERVENTION_LAST_DRUG_TIME,  nowMs()
        );
    }

    @Override protected void afterSuccessfulExecution(CommandContext context, Map<String, Object> slots) {
        String drug = resolveDrug(slots);
        String dose = String.valueOf(slots.getOrDefault("dose", ""));
        String effectId = Drugs.getEffectId(drug, dose);
        effectManager.triggerEffect(effectId, context.getCaseContext());
    }

    // ============ 辅助 ============

    private String resolveDrug(Map<String, Object> slots) {
        if (boundDrugCode != null) return boundDrugCode;
        return String.valueOf(slots.getOrDefault("drug", "unknown"));
    }

}

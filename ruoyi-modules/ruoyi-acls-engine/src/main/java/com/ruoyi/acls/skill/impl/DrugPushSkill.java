package com.ruoyi.acls.skill.impl;
import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.constant.InterventionFields;
import com.ruoyi.ems.constant.InterventionKeys;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.domain.DrugEnum;
import com.ruoyi.ems.domain.Drugs;
import com.ruoyi.ems.effect.EffectManager;
import com.ruoyi.ems.npc.Duty;
import lombok.extern.slf4j.Slf4j;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用药物推注技能（16 种药物共用）。
 *
 * <p>参数化：keystepId = "drug_push_" + boundDrugCode</p>
 * <pre>
 *   new DrugPushSkill(em, helper, "epinephrine")  → drug_push_epinephrine
 *   new DrugPushSkill(em, helper, "adenosine")    → drug_push_adenosine
 * </pre>
 *
 * <p>★ 2026-09-23（路径 A）：effect 触发已统一挪到
 * {@link com.ruoyi.acls.listener.SkillCompletionStateDriver}，
 * 本技能不再持有 {@code DrugIndicationEvaluator}，构造器回退到 3 参。</p>
 *
 * <p>★ 2026-09-23（P0 修复）：{@link #buildExtraFlatWrites} 补写
 * {@code intervention.<drug>Given = true}，兼容旧 YAML 契约
 * （如 {@code intervention.adenosineGiven}），避免删除 case_specific
 * 层 {@code drug_push_adenosine} 声明后变量断写。</p>
 */
@Slf4j
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

        if (DrugEnum.fromCode(drug) == DrugEnum.ADENOSINE) {
            double doseValue = Drugs.parseDoseValue(dose);
            if (doseValue != 6 && doseValue != 12)
                return CommandResult.failure("腺苷剂量应为 6mg 或 12mg，当前为 " + dose);
        }
        return CommandResult.success("剂量校验通过");
    }

    /**
     * ★ P0 修复（2026-09-23）：补写 {@code intervention.<drug>Given = true}。
     *
     * <p>背景：{@code case_specific/stable_narrow/specific.yaml} 曾用
     * {@code type: standard} 声明同名技能 {@code drug_push_adenosine}，
     * 覆盖了 shared 层 {@code type: complex} → {@code adenosinePushSkill Bean}
     * 的注册。删除该覆盖声明后，原 {@code successVar: intervention.adenosineGiven}
     * 的写入责任转移到本方法。</p>
     *
     * <p>写法：使用 {@code "intervention." + drug + "Given"} 动态拼接，
     * 对 16 种药物统一生效（adenosineGiven、epinephrineGiven 等）。</p>
     */
    @Override protected Map<String, Object> buildExtraFlatWrites(Map<String, Object> slots) {
        String drug = resolveDrug(slots);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(InterventionKeys.INTERVENTION_LAST_DRUG,       drug);
        extra.put(InterventionKeys.INTERVENTION_LAST_DRUG_DOSE,  String.valueOf(slots.getOrDefault("dose", "")));
        extra.put(InterventionKeys.INTERVENTION_LAST_DRUG_ROUTE, String.valueOf(slots.getOrDefault("route", "IV")));
        extra.put(InterventionKeys.INTERVENTION_LAST_DRUG_TIME,  nowMs());

        // ★ 兼容旧 YAML 契约 successVar = intervention.<drug>Given
        // 例如 intervention.adenosineGiven = true
        extra.put("intervention." + drug + "Given", true);
        return extra;
    }

    /**
     * ★ 路径 A：effect 触发已统一在 {@code SkillCompletionStateDriver} 完成，
     * 此处不再调用 {@code effectManager.triggerEffect(...)}，避免重复触发。
     * 保留方法体仅为记录日志，便于观察药物技能执行。
     */
    @Override
    protected void afterSuccessfulExecution(CommandContext context, Map<String, Object> slots) {
        String drug = resolveDrug(slots);
        String dose = String.valueOf(slots.getOrDefault("dose", ""));
        log.debug("DrugPushSkill 完成: drug={}, dose={}（effect 由 Driver 统一触发）",
            drug, dose);
    }

    // ============ 辅助 ============

    private String resolveDrug(Map<String, Object> slots) {
        if (boundDrugCode != null) return boundDrugCode;
        return String.valueOf(slots.getOrDefault("drug", "unknown"));
    }
}

// ============================================================
// ⚠️ 自动生成代码 - 请勿手动编辑
// 源文件: _variables.json
// 生成时间: Thu Sep 24 16:26:36 CST 2026
// 修改后请执行 mvn generate-sources 重新生成
// ============================================================

package com.ruoyi.ems.constant;

/**
 * InterventionKeys
 * <p>包含 37 个变量常量
 * <p>使用示例: <code>String key = InterventionKeys.HEART_RATE;</code>
 */
public final class InterventionKeys {

    private InterventionKeys() {}

    /** status、lastTime、count、totalDose、lastDose、lastRoute、flushDone */
    public static final String INTERVENTION_ADENOSINE = "intervention.adenosine";
    /** 腺苷处于首剂还是次剂阶段（AHA 2025 阶梯：6mg → 12mg） */
    public static final String INTERVENTION_ADENOSINE_DOSE_SEQUENCE = "intervention.adenosineDoseSequence";
    /** 是否已推注腺苷（兼容旧 YAML 契约 successVar） */
    public static final String INTERVENTION_ADENOSINE_GIVEN = "intervention.adenosineGiven";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_AMIODARONE = "intervention.amiodarone";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_ASPIRIN = "intervention.aspirin";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_ATROPINE = "intervention.atropine";
    /** status、success、emergency、count、lastTime、lastEnergy */
    public static final String INTERVENTION_CARDIOVERSION = "intervention.cardioversion";
    /** status、count、startTime、stopTime */
    public static final String INTERVENTION_CPR = "intervention.cpr";
    /** status、count、lastTime、lastEnergy、lastSync */
    public static final String INTERVENTION_DEFIB = "intervention.defib";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_DILTIAZEM = "intervention.diltiazem";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_DOPAMINE = "intervention.dopamine";
    /** 最近一次药物的反应效果 */
    public static final String INTERVENTION_DRUG_RESPONSE = "intervention.drugResponse";
    /** status（given/failed/cancelled）、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_EPINEPHRINE = "intervention.epinephrine";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_ESMOLOL = "intervention.esmolol";
    /** bolusGiven、totalVolume、lastTime */
    public static final String INTERVENTION_FLUID = "intervention.fluid";
    /** status、count、lastTime */
    public static final String INTERVENTION_INTUBATION = "intervention.intubation";
    /** 最后推注的药物名称 */
    public static final String INTERVENTION_LAST_DRUG = "intervention.lastDrug";
    /** 最后推注的药物剂量 */
    public static final String INTERVENTION_LAST_DRUG_DOSE = "intervention.lastDrugDose";
    /** 最后给药的途径 */
    public static final String INTERVENTION_LAST_DRUG_ROUTE = "intervention.lastDrugRoute";
    /** 最后给药的时间戳 */
    public static final String INTERVENTION_LAST_DRUG_TIME = "intervention.lastDrugTime";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_LIDOCAINE = "intervention.lidocaine";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_MAGNESIUM = "intervention.magnesium";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_METOPROLOL = "intervention.metoprolol";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_NALOXONE = "intervention.naloxone";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_NITRO = "intervention.nitro";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_NOREPINEPHRINE = "intervention.norepinephrine";
    /** status、count、lastTime */
    public static final String INTERVENTION_PACING = "intervention.pacing";
    /** 确认的心律类型 */
    public static final String INTERVENTION_RHYTHM_CONFIRMED = "intervention.rhythmConfirmed";
    /** 经过治疗后心律是否转为窦性 */
    public static final String INTERVENTION_RHYTHM_CONVERTED = "intervention.rhythmConverted";
    /** 盐水冲管是否已完成 */
    public static final String INTERVENTION_SALINE_FLUSH_DONE = "intervention.salineFlushDone";
    /** 二线药物是否无效 */
    public static final String INTERVENTION_SECOND_LINE_FAILED = "intervention.secondLineFailed";
    /** status（given/failed/cancelled）、given、drug、dose、lastTime */
    public static final String INTERVENTION_SEDATION = "intervention.sedation";
    /** status、type、count、lastTime */
    public static final String INTERVENTION_SGA = "intervention.sga";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_SODIUM_BICARBONATE = "intervention.sodiumBicarbonate";
    /** status、targetTemp、lastTime */
    public static final String INTERVENTION_TTM = "intervention.ttm";
    /** status（attempted/performed/failed）、effect（pending/success/failure）、count、lastTime */
    public static final String INTERVENTION_VAGAL = "intervention.vagal";
    /** status、lastTime、count、totalDose、lastDose、lastRoute */
    public static final String INTERVENTION_VASOPRESSIN = "intervention.vasopressin";
}

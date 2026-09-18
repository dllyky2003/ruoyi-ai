package com.ruoyi.ems.domain;

import java.util.*;

/**
 * 药物枚举，作为所有药物元数据的单一数据源
 * <p>包含：标准编码、中文名、常用别名、剂量规则
 */
public enum DrugEnum {

    // ===== 心脏骤停核心药物 =====
    EPINEPHRINE("epinephrine", "肾上腺素", Arrays.asList("副肾"), 1.0, 0.01, null),
    AMIODARONE("amiodarone", "胺碘酮", Arrays.asList("可达龙", "乙胺碘呋酮"), 300.0, null, null),
    LIDOCAINE("lidocaine", "利多卡因", Arrays.asList("赛罗卡因"), 100.0, null, null),

    // ===== 抗心律失常药物 =====
    ADENOSINE("adenosine", "腺苷", Arrays.asList(), 12.0, null, null),
    ATROPINE("atropine", "阿托品", Arrays.asList(), 0.5, null, 3.0),
    DILTIAZEM("diltiazem", "地尔硫卓", Arrays.asList("合贝爽", "盐酸地尔硫卓"), 25.0, null, null), // 0.25mg/kg，封顶25mg
    METOPROLOL("metoprolol", "美托洛尔", Arrays.asList("倍他乐克", "酒石酸美托洛尔"), 5.0, null, null),
    ESMOLOL("esmolol", "艾司洛尔", Arrays.asList("爱络", "盐酸艾司洛尔"), 5.0, null, null),
    VERAPAMIL("verapamil", "维拉帕米", Arrays.asList(), 5.0, null, null),

    // ===== 血管活性药物 =====
    DOPAMINE("dopamine", "多巴胺", Arrays.asList(), null, null, null),
    NOREPINEPHRINE("norepinephrine", "去甲肾上腺素", Arrays.asList(), null, null, null),
    VASOPRESSIN("vasopressin", "血管加压素", Arrays.asList(), 40.0, null, null),
    PHENYLEPHRINE("phenylephrine", "去氧肾上腺素", Arrays.asList(), null, null, null),

    // ===== ACS/抗缺血药物 =====
    NITROGLYCERIN("nitroglycerin", "硝酸甘油", Arrays.asList("硝甘"), 0.4, null, null),
    ASPIRIN("aspirin", "阿司匹林", Arrays.asList("乙酰水杨酸"), 324.0, null, null),
    TICAGRELOR("ticagrelor", "替格瑞洛", Arrays.asList(), 180.0, null, null),
    CLOPIDOGREL("clopidogrel", "氯吡格雷", Arrays.asList(), 600.0, null, null),

    // ===== 抗凝药物 =====
    HEPARIN("heparin", "肝素", Arrays.asList(), null, null, null),
    ENOXAPARIN("enoxaparin", "依诺肝素", Arrays.asList("克赛"), null, null, null),

    // ===== 电解质/代谢药物 =====
    MAGNESIUM_SULFATE("magnesium_sulfate", "硫酸镁", Arrays.asList(), 2.0, null, null),
    SODIUM_BICARBONATE("sodium_bicarbonate", "碳酸氢钠", Arrays.asList("小苏打"), null, null, null),
    CALCIUM_CHLORIDE("calcium_chloride", "氯化钙", Arrays.asList(), 1.0, null, null),
    CALCIUM_GLUCONATE("calcium_gluconate", "葡萄糖酸钙", Arrays.asList(), 2.0, null, null),
    POTASSIUM_CHLORIDE("potassium_chloride", "氯化钾", Arrays.asList(), 20.0, null, null),
    DEXTROSE_50("dextrose_50", "50%葡萄糖", Arrays.asList(), 25.0, null, null),

    // ===== 解毒/拮抗药物 =====
    NALOXONE("naloxone", "纳洛酮", Arrays.asList(), 2.0, null, null),
    FLUMAZENIL("flumazenil", "氟马西尼", Arrays.asList(), 0.2, null, 3.0),

    // ===== 镇静/抗焦虑药物 =====
    MIDAZOLAM("midazolam", "咪达唑仑", Arrays.asList("力月西"), 5.0, null, null),
    DIAZEPAM("diazepam", "地西泮", Arrays.asList("安定"), 10.0, null, null),
    KETAMINE("ketamine", "氯胺酮", Arrays.asList("K粉"), null, null, null),
    ETOMIDATE("etomidate", "依托咪酯", Arrays.asList(), null, null, null),
    FENTANYL("fentanyl", "芬太尼", Arrays.asList(), null, null, null),

    // ===== 容量治疗 =====
    NORMAL_SALINE("normal_saline", "生理盐水", Arrays.asList("NS"), 1000.0, null, null),
    LACTATED_RINGERS("lactated_ringers", "乳酸林格液", Arrays.asList(), 1000.0, null, null),

    // ===== 支气管扩张剂 =====
    ALBUTEROL("albuterol", "沙丁胺醇", Arrays.asList("舒喘灵"), 2.5, null, null),
    IPRATROPIUM("ipratropium", "异丙托溴铵", Arrays.asList(), 0.5, null, null),

    // ===== 其他 =====
    SALINE("saline", "生理盐水", Arrays.asList("盐水"), null, null, null);

    // ---------- 字段 ----------
    public final String code;
    public final String chineseName;
    public final List<String> extraAliases;
    public final Double adultMaxDose;      // 成人单次最大剂量 (mg)
    public final Double pedMaxDose;        // 儿科单次最大剂量 (mg)
    public final Double totalMaxDose;      // 累计最大剂量 (mg)

    DrugEnum(String code, String chineseName, List<String> extraAliases,
             Double adultMaxDose, Double pedMaxDose, Double totalMaxDose) {
        this.code = code;
        this.chineseName = chineseName;
        this.extraAliases = Collections.unmodifiableList(extraAliases);
        this.adultMaxDose = adultMaxDose;
        this.pedMaxDose = pedMaxDose;
        this.totalMaxDose = totalMaxDose;
    }

    // ---------- 查询方法 ----------
    public static DrugEnum fromCode(String code) {
        for (DrugEnum drug : values()) {
            if (drug.code.equals(code)) {
                return drug;
            }
        }
        return null;
    }

    public static DrugEnum fromChineseName(String name) {
        for (DrugEnum drug : values()) {
            if (drug.chineseName.equals(name)) {
                return drug;
            }
            if (drug.extraAliases.contains(name)) {
                return drug;
            }
        }
        return null;
    }

    /**
     * 获取所有药物编码集合
     */
    public static Set<String> getAllCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (DrugEnum drug : values()) {
            codes.add(drug.code);
        }
        return Collections.unmodifiableSet(codes);
    }

    /**
     * 构建完整的别名映射 (包含英文、中文、额外别名)
     */
    public static Map<String, String> buildAliasMap() {
        Map<String, String> map = new HashMap<>();
        for (DrugEnum drug : values()) {
            // 英文名
            map.put(drug.code, drug.code);
            // 中文名
            map.put(drug.chineseName, drug.code);
            // 额外别名
            for (String alias : drug.extraAliases) {
                map.put(alias, drug.code);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * 返回按长度降序的别名列表（用于最长匹配）
     */
    public static List<String> getSortedAliases() {
        Map<String, String> aliasMap = buildAliasMap();
        List<String> aliases = new ArrayList<>(aliasMap.keySet());
        aliases.sort((a, b) -> b.length() - a.length()); // 长词优先
        return Collections.unmodifiableList(aliases);
    }

    /**
     * 根据别名匹配药物编码（最长匹配优先）
     */
    public static String matchDrugCode(String rawText) {
        if (rawText == null || rawText.isEmpty()) return null;
        for (String alias : getSortedAliases()) {
            if (rawText.contains(alias)) {
                return buildAliasMap().get(alias);
            }
        }
        return null;
    }
}

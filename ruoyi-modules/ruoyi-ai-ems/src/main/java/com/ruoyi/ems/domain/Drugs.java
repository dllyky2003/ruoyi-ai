package com.ruoyi.ems.domain;

import com.ruoyi.ems.constant.AllVarKeys;

import java.util.*;

/**
 * 药物常量与工具类
 * <p>内部使用 DrugEnum 作为数据源，对外保持旧 API 兼容
 *
 * <p><b>药物干预变量名的唯一出口</b>：{@link #getInterventionVarKey(String)}
 */
public final class Drugs {

    private Drugs() {}

    // ============================================================
    // ★ 药物干预 Map 变量名白名单（唯一映射源）
    // ============================================================
    /**
     * DrugEnum.code → intervention.* 变量名。
     *
     * <p>为什么不用"约定式推导"（"intervention." + code）？
     * 因为 3 处 code 与变量名后缀不一致：
     * <ul>
     *   <li>magnesium_sulfate  → intervention.magnesium</li>
     *   <li>sodium_bicarbonate → intervention.sodiumBicarbonate（驼峰）</li>
     *   <li>nitroglycerin      → intervention.nitro</li>
     * </ul>
     *
     * <p>DrugEnum 共 35 种药，只有下列 16 种具备干预 Map 变量；
     * 未列出的药物 {@link #getInterventionVarKey(String)} 返回 null。
     */
    private static final Map<String, String> INTERVENTION_VAR_KEYS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        // --- 15 种 code 与后缀一致 ---
        m.put("epinephrine",       "intervention.epinephrine");
        m.put("amiodarone",        "intervention.amiodarone");
        m.put("lidocaine",         "intervention.lidocaine");
        m.put("atropine",          "intervention.atropine");
        m.put("adenosine",         "intervention.adenosine");
        m.put("diltiazem",         "intervention.diltiazem");
        m.put("metoprolol",        "intervention.metoprolol");
        m.put("esmolol",           "intervention.esmolol");
        m.put("dopamine",          "intervention.dopamine");
        m.put("norepinephrine",    "intervention.norepinephrine");
        m.put("naloxone",          "intervention.naloxone");
        m.put("vasopressin",       "intervention.vasopressin");
        m.put("aspirin",           "intervention.aspirin");
        // --- 3 种 code 与后缀不一致的特例 ---
        m.put("magnesium_sulfate", "intervention.magnesium");
        m.put("sodium_bicarbonate","intervention.sodiumBicarbonate");
        m.put("nitroglycerin",     "intervention.nitro");
        INTERVENTION_VAR_KEYS = Collections.unmodifiableMap(m);
    }

    // ============================================================
    // 查询方法（优化版）
    // ============================================================

    public static Set<String> getAllDrugCodes() {
        return DrugEnum.getAllCodes();
    }

    public static List<String> getDrugCodesSorted() {
        List<String> codes = new ArrayList<>(DrugEnum.getAllCodes());
        Collections.sort(codes);
        return Collections.unmodifiableList(codes);
    }

    public static Map<String, String> getAliasMap() {
        return DrugEnum.buildAliasMap();
    }

    @Deprecated
    public static String getDrugCodeByAlias(String alias) {
        if (alias == null) return null;
        return getAliasMap().get(alias.trim());
    }

    public static String matchDrugCode(String rawText) {
        return DrugEnum.matchDrugCode(rawText);
    }

    // ============================================================
    // 剂量规则查询（基于枚举）
    // ============================================================

    public static Double getMaxDose(String drugCode, String caseType) {
        DrugEnum drug = DrugEnum.fromCode(drugCode);
        if (drug == null) return null;
        if ("pediatric".equalsIgnoreCase(caseType)) {
            return drug.pedMaxDose != null ? drug.pedMaxDose : drug.adultMaxDose;
        }
        return drug.adultMaxDose;
    }

    public static Double getAdultMaxDose(String drugCode) {
        DrugEnum drug = DrugEnum.fromCode(drugCode);
        return drug != null ? drug.adultMaxDose : null;
    }

    public static Double getPediatricMaxDose(String drugCode) {
        DrugEnum drug = DrugEnum.fromCode(drugCode);
        if (drug == null) return null;
        return drug.pedMaxDose != null ? drug.pedMaxDose : drug.adultMaxDose;
    }

    public static Double getTotalMaxDose(String drugCode) {
        DrugEnum drug = DrugEnum.fromCode(drugCode);
        return drug != null ? drug.totalMaxDose : null;
    }

    public static boolean isKnownDrug(String drugCode) {
        return DrugEnum.fromCode(drugCode) != null;
    }

    // ============================================================
    // 剂量校验
    // ============================================================

    public static String validateDose(String drugCode, String doseStr) {
        return validateDose(drugCode, doseStr, "adult");
    }

    public static String validateDose(String drugCode, String doseStr, String caseType) {
        DrugEnum drug = DrugEnum.fromCode(drugCode);
        if (drug == null) {
            return "未知药物: " + drugCode;
        }
        if (doseStr == null || doseStr.isEmpty()) {
            return "剂量为空";
        }

        double doseValue = parseDoseValue(doseStr);
        if (doseValue <= 0) {
            return "无效剂量: " + doseStr;
        }

        boolean isWeightBased = doseStr.contains("mg/kg");
        Double maxDose = getMaxDose(drugCode, caseType);
        Double totalMax = drug.totalMaxDose;

        if (totalMax != null && doseValue > totalMax) {
            return drug.chineseName + " 累计剂量 " + doseValue + "mg 超过最大累计剂量 " + totalMax + "mg";
        }

        if (isWeightBased) {
            if (maxDose != null && doseValue > maxDose) {
                return drug.chineseName + " 按体重计算的剂量 " + doseValue + "mg 超过绝对上限 " + maxDose + "mg";
            }
        } else {
            if (maxDose != null && doseValue > maxDose) {
                return drug.chineseName + " 剂量 " + doseValue + "mg 超过最大推荐剂量 " + maxDose + "mg";
            }
        }

        return null;
    }

    public static boolean isValidDose(String drugCode, String doseStr) {
        return validateDose(drugCode, doseStr) == null;
    }

    public static boolean isValidDose(String drugCode, String doseStr, String caseType) {
        return validateDose(drugCode, doseStr, caseType) == null;
    }

    // ============================================================
    // 基础工具
    // ============================================================

    public static String getChineseName(String drugCode) {
        DrugEnum drug = DrugEnum.fromCode(drugCode);
        return drug != null ? drug.chineseName : null;
    }

    public static double parseDoseValue(String dose) {
        if (dose == null) return 0;
        try {
            return Double.parseDouble(dose.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取药物的效果ID（用于追踪特定剂量下的药物效果）
     * <p>格式: {drugCode}_{normalizedDose}
     */
    /**
     * 获取药物的效果ID（用于追踪特定剂量下的药物效果）
     * <p>格式: {drugCode}_{normalizedDose}，均归一为小写、去空格。</p>
     * <p>示例：{@code adenosine + "6mg" → "adenosine_6mg"}；
     * {@code Midazolam + "2 MG" → "midazolam_2mg"}。</p>
     */
    public static String getEffectId(String drugCode, String dose) {
        if (drugCode == null) return null;
        String normalizedDrug = drugCode.trim().toLowerCase();
        String normalizedDose = dose != null
            ? dose.trim().replaceAll("\\s+", "").toLowerCase()
            : "";
        return normalizedDrug + "_" + normalizedDose;
    }

    // ============================================================
    // ★ 药物干预变量名的唯一出口
    // ============================================================

    /**
     * 取药物的干预 Map 变量名（如 "intervention.epinephrine"）。
     *
     * <p>通过内部 {@link #INTERVENTION_VAR_KEYS} 白名单查找。
     * 没有对应干预 Map 的药物（如 heparin / fentanyl / 各种晶体液）返回 null。
     *
     * <p>这是 Skill 层访问药物干预变量的<b>唯一入口</b>。
     * 不要在业务代码里 switch drugCode 或手拼字符串。
     *
     * @param drugCode DrugEnum 的 code（如 "epinephrine" / "sodium_bicarbonate"）
     * @return 变量名；未知药物或无干预 Map 的药物返回 null
     */
    public static String getInterventionVarKey(String drugCode) {
        if (drugCode == null) return null;
        // 校验药物本身是否合法（避免拼写错误静默返回 null）
        if (DrugEnum.fromCode(drugCode) == null) {
            return null;
        }
        return INTERVENTION_VAR_KEYS.get(drugCode);
    }

    /**
     * 判断某药物是否有干预 Map 变量。
     */
    public static boolean hasInterventionMap(String drugCode) {
        return getInterventionVarKey(drugCode) != null;
    }

    /**
     * 供启动期一致性校验使用。
     *
     * <p>纯函数：遍历白名单，检查每个变量名是否在 AllVarKeys 注册。
     * 不抛异常、不打日志；调用方决定是告警还是阻断。
     *
     * @return 不一致清单；空列表表示全部通过
     */
    public static List<String> findBadInterventionVarKeys() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> e : INTERVENTION_VAR_KEYS.entrySet()) {
            String code = e.getKey();
            String varKey = e.getValue();
            if (!AllVarKeys.isValid(varKey)) {
                bad.add(String.format("药物 '%s' → 变量 '%s' 未在 AllVarKeys 注册",
                    code, varKey));
            }
        }
        return bad;
    }

    /**
     * 取全部有干预 Map 的药物 code（供契约构建、文档生成等使用）。
     */
    public static Set<String> getInterventionDrugCodes() {
        return INTERVENTION_VAR_KEYS.keySet();
    }
}

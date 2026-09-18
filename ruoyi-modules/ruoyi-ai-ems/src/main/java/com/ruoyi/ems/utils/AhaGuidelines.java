package com.ruoyi.ems.utils;

import org.springframework.stereotype.Component;

/**
 * AHA 指南通用计算工具（支持参数化阈值）
 * 适用于 ACLS、Megacode、PALS 等不同场景
 */
@Component("aha")
public class AhaGuidelines {

    // ---------- 默认阈值（遵循 AHA 2020 指南） ----------
    public static final double DEFAULT_QRS_THRESHOLD = 0.12;      // 秒
    public static final int DEFAULT_SBP_THRESHOLD = 90;           // mmHg
    public static final int DEFAULT_RR_MIN = 10;
    public static final int DEFAULT_RR_MAX = 30;
    public static final double DEFAULT_RR_INTERVAL_JITTER = 0.12; // 秒

    // ---------- 1. QRS 宽度判定 ----------
    public String getQrsWidth(Double qrsDuration) {
        return getQrsWidth(qrsDuration, DEFAULT_QRS_THRESHOLD);
    }

    public String getQrsWidth(Double qrsDuration, Double threshold) {
        if (qrsDuration == null) return "wide";
        return qrsDuration <= threshold ? "narrow" : "wide";
    }

    // ---------- 2. 血流动力学稳定性判定 ----------
    public boolean isStable(Integer systolicBp, String consciousness, Integer respiratoryRate) {
        return isStable(systolicBp, consciousness, respiratoryRate,
            DEFAULT_SBP_THRESHOLD, DEFAULT_RR_MIN, DEFAULT_RR_MAX);
    }

    public boolean isStable(Integer systolicBp, String consciousness, Integer respiratoryRate,
                            Integer sbpThreshold, int rrMin, int rrMax) {
        return systolicBp != null && systolicBp >= sbpThreshold
            && "alert".equals(consciousness)
            && respiratoryRate != null && respiratoryRate >= rrMin && respiratoryRate <= rrMax;
    }

    // ---------- 3. 心律规则性判定 ----------
    public boolean isRhythmRegular(Double rrIntervalJitter) {
        return isRhythmRegular(rrIntervalJitter, DEFAULT_RR_INTERVAL_JITTER);
    }

    public boolean isRhythmRegular(Double rrIntervalJitter, Double jitterThreshold) {
        return rrIntervalJitter != null && rrIntervalJitter < jitterThreshold;
    }

    // ---------- 4. 可电击心律判定 ----------
    public boolean isShockable(String rhythm) {
        return "VF".equals(rhythm) || "VT".equals(rhythm) || "Torsades".equals(rhythm);
    }

    // ---------- 5. 药物剂量计算（支持儿童/成人） ----------
    public double getEpinephrineDose(double weightKg) {
        return getEpinephrineDose(weightKg, false); // 默认成人
    }

    public double getEpinephrineDose(double weightKg, boolean isPediatric) {
        // ACLS 成人: 0.01 mg/kg (1:10000 溶液)
        // PALS 儿童: 0.01 mg/kg (同成人，但最大 1mg)
        double dose = weightKg * 0.01;
        if (isPediatric) {
            dose = Math.min(dose, 1.0); // 儿童最大 1mg
        }
        return dose;
    }

    // 腺苷（仅示例）
    public double getAdenosineFirstDose(boolean isPediatric) {
        return isPediatric ? 0.1 : 6.0; // 儿童 0.1 mg/kg，成人 6mg
    }

    // ---------- 6. 通气评估（Megacode 常用） ----------
    public boolean isAdequateVentilation(Integer etco2, Integer tidalVolume) {
        return etco2 != null && etco2 >= 35 && etco2 <= 45
            && tidalVolume != null && tidalVolume >= 400 && tidalVolume <= 600;
    }

    // 可以继续增加其他方法……
}

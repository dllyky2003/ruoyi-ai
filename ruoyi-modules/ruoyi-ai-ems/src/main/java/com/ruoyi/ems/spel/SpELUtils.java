package com.ruoyi.ems.spel;

import com.ruoyi.ems.domain.CaseContext;
import org.springframework.stereotype.Component;

/**
 * SpEL 工具函数库
 * 在 YAML 的 condition/action 中通过 #utils.xxx() 调用
 */
@Component
public class SpELUtils {

    /**
     * 判断是否已推过首剂腺苷（≥1次）
     */
    public boolean hasFirstAdenosine(CaseContext ctx) {
        Integer count = ctx.getVar("adenosine_dose_count", Integer.class);
        return count != null && count >= 1;
    }

    /**
     * 判断是否已推过次剂腺苷（≥2次）
     */
    public boolean hasSecondAdenosine(CaseContext ctx) {
        Integer count = ctx.getVar("adenosine_dose_count", Integer.class);
        return count != null && count >= 2;
    }

    /**
     * 获取当前应使用的腺苷剂量（6mg 或 12mg）
     */
    public int getAdenosineDose(CaseContext ctx) {
        Integer count = ctx.getVar("adenosine_dose_count", Integer.class);
        return (count == null || count == 0) ? 6 : 12;
    }

    /**
     * 判断患者是否为窦性心律
     */
    public boolean isSinusRhythm(CaseContext ctx) {
        String rhythm = ctx.getVar("current_rhythm", String.class);
        return "Sinus".equals(rhythm);
    }

    /**
     * 判断患者血流动力学是否稳定
     */
    public boolean isStable(CaseContext ctx) {
        Integer systolic = ctx.getVar("systolic_bp", Integer.class);
        String consciousness = ctx.getVar("consciousness", String.class);
        return systolic != null && systolic >= 90 && "alert".equals(consciousness);
    }

    /**
     * 判断患者是否出现不稳定征象
     */
    public boolean isUnstable(CaseContext ctx) {
        return !isStable(ctx);
    }

    /**
     * 获取变量值，若不存在则返回默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(CaseContext ctx, String key, T defaultValue) {
        T value = ctx.getVar(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }
}

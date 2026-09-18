package com.ruoyi.ems.constant;

/**
 * 手写常量：intervention.* Map 型变量的内部字段名。
 *
 * 为什么不放进 VariableKeyGenerator？
 *   _variables.json 只描述到 "intervention.epinephrine" 这一级，
 *   Map 内的 status/lastTime/count/... 不是变量，扫不到。
 */
public final class InterventionFields {
    private InterventionFields() {}

    public static final String STATUS     = "status";
    public static final String LAST_TIME  = "lastTime";
    public static final String COUNT      = "count";
    public static final String TOTAL_DOSE = "totalDose";
    public static final String LAST_DOSE  = "lastDose";
    public static final String LAST_ROUTE = "lastRoute";
    public static final String EFFECT     = "effect";
}

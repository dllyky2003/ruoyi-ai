// ============================================================
// ⚠️ 自动生成代码 - 请勿手动编辑
// 源文件: _variables.json
// 生成时间: Thu Sep 24 16:26:36 CST 2026
// 修改后请执行 mvn generate-sources 重新生成
// ============================================================

package com.ruoyi.ems.constant;

/**
 * DerivedKeys
 * <p>包含 10 个变量常量
 * <p>使用示例: <code>String key = DerivedKeys.HEART_RATE;</code>
 */
public final class DerivedKeys {

    private DerivedKeys() {}

    /** 同步电复律启动条件是否全部就绪：不稳定窄QRS + 除颤仪同步模式 + 已充电 + 已清场 */
    public static final String DERIVED_CARDIOVERSION_READY = "derived.cardioversionReady";
    /** CPR质量是否达标 */
    public static final String DERIVED_CPR_QUALITY_GOOD = "derived.cprQualityGood";
    /** 是否满足稳定+窄QRS+规则节律 */
    public static final String DERIVED_IS_NARROW_REGULAR_STABLE = "derived.isNarrowRegularStable";
    /** 是否窦性心律 */
    public static final String DERIVED_IS_SINUS_RHYTHM = "derived.isSinusRhythm";
    /** 是否满足血流动力学不稳定 + 窄QRS，是进入紧急同步电复律分支的核心条件 */
    public static final String DERIVED_IS_UNSTABLE_NARROW_QRS = "derived.isUnstableNarrowQrs";
    /** 由客观心律推导的QRS宽度 */
    public static final String DERIVED_QRS_WIDTH = "derived.qrsWidth";
    /** 由客观心律推导的节律规则性 */
    public static final String DERIVED_RHYTHM_REGULAR = "derived.rhythmRegular";
    /** 当前心律是否可电击 */
    public static final String DERIVED_SHOCKABLE_RHYTHM = "derived.shockableRhythm";
    /** 由 5 个不稳定征象推导的血流动力学稳定性 */
    public static final String DERIVED_STABLE = "derived.stable";
    /** 由 5 个不稳定征象派生的反向标记，等价于 derived.stable == false */
    public static final String DERIVED_UNSTABLE = "derived.unstable";
}

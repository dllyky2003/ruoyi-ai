package com.ruoyi.ems.effect;

/**
 * 效果分支（螺旋 3）。
 *
 * <p>与 acls-engine 的 {@code StudentResponseBranch} 语义一致，
 * 但刻意分属不同模块以避免 ems → acls-engine 的反向依赖：
 * acls-engine 提供 {@code StudentResponseBranch}，ems 内部用本枚举承接。</p>
 *
 * <p>每个分支带一个 {@link #getDefaultCondition() defaultCondition}，
 * 用于在调用
 * 时把三态映射到现有 YAML 的 {@code effectKey__condition} 命名约定：</p>
 * <ul>
 *   <li>NORMAL → {@code "correct"}（但 NORMAL 分支实际会走 indicationEvaluator，
 *       本默认仅作兜底）</li>
 *   <li>ERROR → {@code "wrong_critical"}</li>
 *   <li>DELAYED → {@code "delayed"}</li>
 * </ul>
 */
public enum EffectBranch {

    /** 正常操作。 */
    NORMAL("correct"),

    /** 错误操作。 */
    ERROR("wrong_critical"),

    /** 延时操作。 */
    DELAYED("delayed");

    private final String defaultCondition;

    EffectBranch(String defaultCondition) {
        this.defaultCondition = defaultCondition;
    }

    public String getDefaultCondition() {
        return defaultCondition;
    }

    public static EffectBranch from(String s) {
        if (s == null) {
            return null;
        }
        for (EffectBranch b : values()) {
            if (b.name().equalsIgnoreCase(s)) {
                return b;
            }
        }
        return null;
    }
}

package com.ruoyi.ems.npc;

import java.util.Arrays;

/**
 * 团队规模枚举。
 * <p>
 * 客户端在 start_case_request 里传的 teamSize 字段对应此枚举。
 * </p>
 * <p>
 * 注意：teamSize 是模板编号，不是实际成员数。
 * <ul>
 *   <li>{@code TEAM_3MAN} 实际 4 个 slot（组长 + 医生 + 护士 + 司机）</li>
 *   <li>{@code TEAM_4MAN} 实际 5 个 slot</li>
 *   <li>{@code TEAM_5MAN} 实际 6 个 slot</li>
 * </ul>
 * 差异原因：3MAN 命名是历史包袱，每个模板都含组长 slot。
 * </p>
 */
public enum TeamSize {

    TEAM_3MAN("3MAN", 3, "标准三人组（含组长）"),
    TEAM_4MAN("4MAN", 4, "标准四人组（含组长）"),
    TEAM_5MAN("5MAN", 5, "大型团队五人组（含组长）");

    private final String code;
    private final int value;
    private final String description;

    TeamSize(String code, int value, String description) {
        this.code = code;
        this.value = value;
        this.description = description;
    }

    /** 模板 ID，如 "3MAN" */
    public String getCode() {
        return code;
    }

    /** 客户端传的数字，如 3 / 4 / 5 */
    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 从 code 字符串解析。解析失败返回 null。
     */
    public static TeamSize fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        return Arrays.stream(values())
            .filter(t -> t.code.equalsIgnoreCase(code))
            .findFirst()
            .orElse(null);
    }

    /**
     * 从 value 数字解析。解析失败返回 null。
     */
    public static TeamSize fromValue(Integer value) {
        if (value == null) return null;
        return Arrays.stream(values())
            .filter(t -> t.value == value)
            .findFirst()
            .orElse(null);
    }

    /**
     * 默认团队规模。
     */
    public static TeamSize defaultSize() {
        return TEAM_3MAN;
    }
}

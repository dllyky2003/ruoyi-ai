package com.ruoyi.ems.room.model;


import lombok.Getter;

import java.util.Arrays;
import java.util.Set;

/**
 * 房间内客户端的角色。
 * REQUIRED_WHEN_ACTIVE 表示房间进入 ACTIVE 前必须至少有一个连接的角色。
 */
@Getter
public enum Role {
    ENGINE("engine", true),
    SPEAKER("speaker", true),
    VIEWER("viewer", false),
    INSTRUCTOR("instructor", false);

    private final String code;
    private final boolean requiredForActive;

    Role(String code, boolean requiredForActive) {
        this.code = code;
        this.requiredForActive = requiredForActive;
    }

    public static Role fromCode(String code) {
        if (code == null) return null;
        return Arrays.stream(values())
            .filter(r -> r.code.equalsIgnoreCase(code))
            .findFirst()
            .orElse(null);
    }

    public static Set<Role> requiredRoles() {
        return Arrays.stream(values())
            .filter(Role::isRequiredForActive)
            .collect(java.util.stream.Collectors.toSet());
    }
}

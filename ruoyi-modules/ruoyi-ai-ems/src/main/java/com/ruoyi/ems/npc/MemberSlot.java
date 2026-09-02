package com.ruoyi.ems.npc;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 团队成员配置模型（一个成员可以承担多个职责）
 */
@Data
@Builder
public class MemberSlot {
    /** 成员角色名称（如：医生、护士、司机、担架员） */
    private String title;
    /** 成员名字（可随机生成或预设） */
    private String name;
    /** 该成员承担的 ACLS 职责列表 */
    private Set<Duty> duties;
}


package com.ruoyi.ems.npc;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 团队配置模板
 * 用于定义不同人数和角色组合的虚拟团队
 */
@Data
@Builder
public class TeamTemplate {

    /** 模板ID（如 "3MAN", "4MAN"） */
    private String templateId;

    /** 模板描述 */
    private String description;

    /** 团队成员槽位列表 */
    private List<MemberSlot> slots;

    // ==================== 预置国内常见配置 ====================

    /**
     * 标准三人组（医生 + 护士 + 司机）
     */
    public static TeamTemplate china3Man() {
        return TeamTemplate.builder()
            .templateId("3MAN")
            .description("国内标准三人组：医生（指挥+气道+评估）、护士（药物+监护+记录）、司机（按压+辅助）")
            .slots(List.of(
                MemberSlot.builder().title("医生").name("张伟").duties(Set.of(Duty.CPR, Duty.AIRWAY, Duty.ASSESS)).build(),
                MemberSlot.builder().title("护士").name("王芳").duties(Set.of(Duty.IV_MED, Duty.MONITOR_DEFIB, Duty.RECORD)).build(),
                MemberSlot.builder().title("司机").name("刘洋").duties(Set.of(Duty.CPR, Duty.ASSIST)).build()
            ))
            .build();
    }

    /**
     * 标准四人组（医生 + 护士 + 司机 + 担架员）
     */
    public static TeamTemplate china4Man() {
        return TeamTemplate.builder()
            .templateId("4MAN")
            .description("国内标准四人组：医生（指挥+气道）、护士（药物+记录）、司机（按压+辅助）、担架员（监护+辅助）")
            .slots(List.of(
                MemberSlot.builder().title("医生").name("张伟").duties(Set.of(Duty.ASSESS, Duty.AIRWAY)).build(),
                MemberSlot.builder().title("护士").name("王芳").duties(Set.of(Duty.IV_MED, Duty.RECORD)).build(),
                MemberSlot.builder().title("司机").name("刘洋").duties(Set.of(Duty.CPR, Duty.ASSIST)).build(),
                MemberSlot.builder().title("担架员").name("陈明").duties(Set.of(Duty.MONITOR_DEFIB, Duty.ASSIST)).build()
            ))
            .build();
    }

    /**
     * 大型团队五人组（医生 + 护士 + 司机 + 担架员 + 备用）
     */
    public static TeamTemplate china5Man() {
        return TeamTemplate.builder()
            .templateId("5MAN")
            .description("大型团队五人组：角色明确，按压专注")
            .slots(List.of(
                MemberSlot.builder().title("医生").name("张伟").duties(Set.of(Duty.ASSESS, Duty.AIRWAY)).build(),
                MemberSlot.builder().title("护士").name("王芳").duties(Set.of(Duty.IV_MED, Duty.RECORD)).build(),
                MemberSlot.builder().title("司机").name("刘洋").duties(Set.of(Duty.CPR)).build(),
                MemberSlot.builder().title("担架员1").name("陈明").duties(Set.of(Duty.MONITOR_DEFIB)).build(),
                MemberSlot.builder().title("担架员2").name("赵强").duties(Set.of(Duty.ASSIST)).build()
            ))
            .build();
    }


}

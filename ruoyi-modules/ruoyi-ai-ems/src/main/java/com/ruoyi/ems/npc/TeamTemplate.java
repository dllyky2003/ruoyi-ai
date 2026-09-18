package com.ruoyi.ems.npc;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@Builder
public class TeamTemplate {
    private String id;
    private String description;
    private List<MemberSlot> slots;

    // ============ 标准三人组（实际配置后变为4人：组长+医生+护士+司机） ============
    public static TeamTemplate china3Man() {
        return TeamTemplate.builder()
            .id("3MAN")
            .description("标准三人组（已增加组长岗位）")
            .slots(List.of(
                MemberSlot.builder().title("组长").name("体验者").duties(List.of(Duty.TEAM_LEADER)).build(),
                MemberSlot.builder().title("医生").name("张伟").duties(List.of(Duty.CPR, Duty.AIRWAY, Duty.ASSESS)).build(),
                MemberSlot.builder().title("护士").name("王芳").duties(List.of(Duty.IV_MED, Duty.MONITOR_DEFIB, Duty.RECORD)).build(),
                MemberSlot.builder().title("司机").name("刘洋").duties(List.of(Duty.CPR, Duty.ASSIST)).build()
            ))
            .build();
    }

    // ============ 标准四人组（实际配置后变为5人：组长+医生+护士+司机+担架员） ============
    public static TeamTemplate china4Man() {
        return TeamTemplate.builder()
            .id("4MAN")
            .description("标准四人组（已增加组长岗位）")
            .slots(List.of(
                MemberSlot.builder().title("组长").name("体验者").duties(List.of(Duty.TEAM_LEADER)).build(),
                MemberSlot.builder().title("医生").name("张伟").duties(List.of(Duty.ASSESS, Duty.AIRWAY)).build(),
                MemberSlot.builder().title("护士").name("王芳").duties(List.of(Duty.IV_MED, Duty.RECORD)).build(),
                MemberSlot.builder().title("司机").name("刘洋").duties(List.of(Duty.CPR, Duty.ASSIST)).build(),
                MemberSlot.builder().title("担架员").name("陈明").duties(List.of(Duty.MONITOR_DEFIB, Duty.ASSIST)).build()
            ))
            .build();
    }

    // ============ 大型团队五人组（实际配置后变为6人：组长+医生+护士+司机+担架员1+担架员2） ============
    public static TeamTemplate china5Man() {
        return TeamTemplate.builder()
            .id("5MAN")
            .description("大型团队五人组（已增加组长岗位）")
            .slots(List.of(
                MemberSlot.builder().title("组长").name("体验者").duties(List.of(Duty.TEAM_LEADER)).build(),
                MemberSlot.builder().title("医生").name("张伟").duties(List.of(Duty.ASSESS, Duty.AIRWAY)).build(),
                MemberSlot.builder().title("护士").name("王芳").duties(List.of(Duty.IV_MED, Duty.RECORD)).build(),
                MemberSlot.builder().title("司机").name("刘洋").duties(List.of(Duty.CPR)).build(),
                MemberSlot.builder().title("担架员1").name("陈明").duties(List.of(Duty.MONITOR_DEFIB)).build(),
                MemberSlot.builder().title("担架员2").name("赵强").duties(List.of(Duty.ASSIST)).build()
            ))
            .build();
    }
}

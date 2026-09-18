package com.ruoyi.ems.room.dto;

import lombok.Data;

import java.util.List;

/**
 * 团队成员 DTO。
 * <p>
 * 由服务端 TeamManager 的 TeamMember 转换而来，随 case_config 下发到 Unity，
 * Unity 据此创建 TeamNpcAgent。
 * </p>
 * <p>
 * 字段命名与 Unity 端 C# DTO 保持一致，Jackson / Newtonsoft 默认序列化不改名。
 * </p>
 */
@Data
public class TeamMemberDto {

    /** 成员唯一 ID，对应 TeamMember.id。Unity 用它作为 npcId */
    private String memberId;

    /** 岗位名：组长 / 医生 / 护士 / 司机 / 担架员 */
    private String title;

    /** 角色名：体验者 / 张伟 / 王芳 / 刘洋 */
    private String name;

    /**
     * 职责标签。
     * 取值：TEAM_LEADER / ASSESS / AIRWAY / CPR / IV_MED / MONITOR_DEFIB / RECORD / ASSIST
     */
    private List<String> duties;

    /**
     * 是否真人。
     * true 表示组长（TEAM_LEADER），Unity 不创建 NPC agent，不派动作。
     */
    private boolean human;

    /** UI 颜色，如 #1E88E5 */
    private String color;

    /** TTS 音色，如 zm_098 / zf_047 / zm_089 */
    private String ttsVoice;
}

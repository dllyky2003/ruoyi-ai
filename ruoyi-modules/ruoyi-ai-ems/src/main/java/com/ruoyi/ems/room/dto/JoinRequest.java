package com.ruoyi.ems.room.dto;

import lombok.Data;

@Data
public class JoinRequest {

    /** 想要加入的角色 */
    private String role;

    /** 可选：用户标识 */
    private String userId;
}

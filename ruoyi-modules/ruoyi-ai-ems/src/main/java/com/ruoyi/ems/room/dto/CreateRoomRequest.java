package com.ruoyi.ems.room.dto;

import lombok.Data;

/**
 * 创建房间请求。
 * creatorRole 决定创建者拿到的 token 是什么角色。
 */
@Data
public class CreateRoomRequest {

    /** 案例 ID，如 stable_narrow */
    private String caseId;

    /** 团队模板，如 3MAN，可为 null */
    private String templateId;

    /** 创建者角色：engine / speaker / instructor，默认 instructor */
    private String creatorRole;

    /** 可选：客户端自定义的 sessionId，不传由服务端生成 */
    private String sessionId;

    /** 可选：创建者用户标识 */
    private String userId;
}

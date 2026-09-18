package com.ruoyi.ems.room.model;

/**
 * 房间生命周期状态。
 *
 * CREATED   刚创建，还没有客户端连接
 * WAITING   有客户端连接，但必需角色未到齐
 * ACTIVE    所有必需角色已到齐，训练进行中
 * PAUSED    必需角色中有人断开，等待重连
 * COMPLETED 训练正常结束
 * EXPIRED   PAUSED 超时未重连，房间失效
 */
public enum RoomState {
    CREATED,
    WAITING,
    ACTIVE,
    PAUSED,
    COMPLETED,
    EXPIRED
}

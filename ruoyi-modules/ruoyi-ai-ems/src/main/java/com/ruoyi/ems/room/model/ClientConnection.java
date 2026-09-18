package com.ruoyi.ems.room.model;

import jakarta.websocket.Session;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 房间内的一个客户端连接。只持有 Session 的引用，不拥有它。
 * 不负责关闭 Session，只标记状态。
 */
@Getter
public class ClientConnection {

    private final String clientId;
    private final Role role;
    private final String userId;          // 可选，来自 token
    private final Session session;        // 可能为 null（测试用 Fake）
    private final long joinedAt;
    private final AtomicLong lastHeartbeatAt;

    @Setter
    private volatile boolean open = true;

    public ClientConnection(String clientId, Role role, String userId, Session session) {
        this.clientId = clientId;
        this.role = role;
        this.userId = userId;
        this.session = session;
        this.joinedAt = System.currentTimeMillis();
        this.lastHeartbeatAt = new AtomicLong(this.joinedAt);
    }

    public void touch() {
        lastHeartbeatAt.set(System.currentTimeMillis());
    }

    public long getLastHeartbeatAt() {
        return lastHeartbeatAt.get();
    }

    /** 供日志和调试 */
    public String describe() {
        return role.getCode() + ":" + clientId + (userId != null ? "(" + userId + ")" : "");
    }
}

package com.ruoyi.ems.room.ws;

import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.token.WsTokenService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 房间 WebSocket 端点。
 * <p>
 * 握手 URL: /ws/session/{sessionId}?role=xxx&token=xxx
 * <ul>
 *   <li>role=engine    Unity 生理引擎 / NPC</li>
 *   <li>role=speaker   学员主控</li>
 *   <li>role=viewer    观察者</li>
 *   <li>role=instructor 导师</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@ServerEndpoint("/ws/session/{sessionId}")
public class SessionWebSocketHandler {

    // ---------- 静态依赖注入 ----------
    private static RoomRegistry roomRegistry;
    private static WsSessionRegistry wsRegistry;
    private static WsTokenService tokenService;
    private static RoomBroadcaster broadcaster;
    private static WsMessageRouter messageRouter;

    @Autowired
    public void injectBeans(RoomRegistry rr,
                            WsSessionRegistry wr,
                            WsTokenService ts,
                            RoomBroadcaster rb,
                            WsMessageRouter mr) {
        SessionWebSocketHandler.roomRegistry = rr;
        SessionWebSocketHandler.wsRegistry = wr;
        SessionWebSocketHandler.tokenService = ts;
        SessionWebSocketHandler.broadcaster = rb;
        SessionWebSocketHandler.messageRouter = mr;
        log.info("✅ SessionWebSocketHandler 依赖注入完成");
    }

    private static final String ATTR_SESSION_ID = "sessionId";
    private static final String ATTR_CLIENT_ID  = "clientId";
    private static final String ATTR_ROLE       = "role";
    private static final String ATTR_USER_ID    = "userId";

    // ---------- 生命周期 ----------

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId) {
        log.info("WS 连接请求: sessionId={}, clientId={}, uri={}",
            sessionId, session.getId(), session.getRequestURI());

        // 1. 依赖是否就绪
        if (roomRegistry == null || wsRegistry == null || tokenService == null) {
            log.error("❌ WebSocket 依赖未注入，拒绝连接");
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "服务未就绪");
            return;
        }

        // 2. 解析握手参数
        Role role = WsHandshake.getRole(session);
        String token = WsHandshake.getToken(session);

        if (role == null) {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "缺少或非法 role");
            return;
        }
        if (token == null) {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "缺少 token");
            return;
        }

        // 3. 校验 token（一次性消费）
        WsTokenService.TokenEntry entry = tokenService.consume(token, sessionId, role);
        if (entry == null) {
            log.warn("token 校验失败: sessionId={}, role={}", sessionId, role);
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "token 无效或已过期");
            return;
        }

        // 4. 房间必须存在
        Optional<SessionRoom> roomOpt = roomRegistry.get(sessionId);
        if (roomOpt.isEmpty()) {
            log.warn("房间不存在: sessionId={}", sessionId);
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "房间不存在");
            return;
        }

        SessionRoom room = roomOpt.get();
        if (room.isTerminal()) {
            log.warn("房间已结束: sessionId={}, state={}", sessionId, room.getState());
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "房间已结束");
            return;
        }

        // ★ 5. 单例角色：WebSocket 真连上来时，先清掉 create 时插的"占位连接"
        //    ENGINE / SPEAKER 只允许一个连接，占位不算真实物理连接，必须在真连接之前清掉
        if (role == Role.ENGINE || role == Role.SPEAKER) {
            if (room.hasRole(role)) {
                room.leaveAllOfRole(role);
                log.info("🔁 移除占位连接: sessionId={}, role={}", sessionId, role.getCode());
            }
        }

        // 6. 生成 clientId，创建 ClientConnection
        String clientId = UUID.randomUUID().toString().replace("-", "");
        ClientConnection conn = new ClientConnection(
            clientId, role, entry.userId(), session);

        if (!room.join(conn)) {
            log.warn("加入房间失败（重复 clientId）: sessionId={}", sessionId);
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "加入房间失败");
            return;
        }

        // 7. 写入 Session 属性
        session.getUserProperties().put(ATTR_SESSION_ID, sessionId);
        session.getUserProperties().put(ATTR_CLIENT_ID, clientId);
        session.getUserProperties().put(ATTR_ROLE, role);
        session.getUserProperties().put(ATTR_USER_ID, entry.userId());

        // 8. 注册物理连接
        wsRegistry.register(sessionId, clientId, session);

        log.info("✅ WS 连接建立: sessionId={}, clientId={}, role={}, roomState={}, clients={}",
            sessionId, clientId, role.getCode(), room.getState(), room.size());

        // 9. 通知本连接：连接成功
        broadcaster.sendToClient(sessionId, clientId,
            RoomBroadcaster.envelope("connected", Map.of(
                "sessionId", sessionId,
                "clientId", clientId,
                "role", role.getCode(),
                "roomState", room.getState().name()
            )));

        // 10. 通知房间内其他连接：成员变更
        broadcaster.broadcastExcept(sessionId, null,
            RoomBroadcaster.envelope("room_member_joined", Map.of(
                "clientId", clientId,
                "role", role.getCode(),
                "roomState", room.getState().name(),
                "clientCount", room.size()
            )));
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String sessionId = (String) session.getUserProperties().get(ATTR_SESSION_ID);
        String clientId = (String) session.getUserProperties().get(ATTR_CLIENT_ID);
        Role role = (Role) session.getUserProperties().get(ATTR_ROLE);

        if (sessionId == null || clientId == null || role == null) {
            log.warn("收到未初始化连接的消息，已忽略");
            return;
        }

        // 处理 ping
        if (message != null && message.contains("\"ping\"")) {
            broadcaster.sendToClient(sessionId, clientId,
                RoomBroadcaster.envelope("pong", Map.of()));
            return;
        }

        if (messageRouter == null) {
            log.warn("WsMessageRouter 未注入，消息被丢弃: {}", message);
            return;
        }

        messageRouter.route(sessionId, clientId, role, message);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String sessionId = (String) session.getUserProperties().get(ATTR_SESSION_ID);
        String clientId = (String) session.getUserProperties().get(ATTR_CLIENT_ID);
        Role role = (Role) session.getUserProperties().get(ATTR_ROLE);

        log.info("WS 连接关闭: sessionId={}, clientId={}, role={}, reason={}",
            sessionId, clientId, role, reason);

        if (sessionId == null || clientId == null) return;

        // 1. 从物理连接表移除
        wsRegistry.unregister(sessionId, clientId);

        // 2. 从业务房间移除
        Optional<SessionRoom> roomOpt = roomRegistry.get(sessionId);
        if (roomOpt.isEmpty()) return;

        SessionRoom room = roomOpt.get();
        RoomState before = room.getState();

        boolean removed = room.leave(clientId);

        // ★ 单例角色（ENGINE / SPEAKER）：清理残留占位连接
        // create 时插的"创建者占位"也要一起清掉，否则之后没人能 join 该角色
        if (role == Role.ENGINE || role == Role.SPEAKER) {
            room.leaveAllOfRole(role);
            removed = true;    // 至少清掉了占位
        }

        RoomState after = room.getState();

        if (!removed) return;

        log.info("房间成员离开: sessionId={}, clientId={}, role={}, {} → {}",
            sessionId, clientId, role, before, after);

        // 3. 状态变化广播
        if (before != after) {
            roomRegistry.notifyStateChange(room, before, after);
            broadcaster.broadcast(sessionId,
                RoomBroadcaster.envelope("room_state_changed", Map.of(
                    "from", before.name(),
                    "to", after.name(),
                    "roomState", after.name(),
                    "clientCount", room.size()
                )));
        }

        // 4. 成员离开广播
        broadcaster.broadcast(sessionId,
            RoomBroadcaster.envelope("room_member_left", Map.of(
                "clientId", clientId,
                "role", role != null ? role.getCode() : "unknown",
                "roomState", after.name(),
                "clientCount", room.size()
            )));
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String sessionId = (String) session.getUserProperties().get(ATTR_SESSION_ID);
        String clientId = (String) session.getUserProperties().get(ATTR_CLIENT_ID);
        log.error("WS 错误: sessionId={}, clientId={}", sessionId, clientId, error);
    }

    // ---------- 辅助 ----------

    private static void closeQuietly(Session session, CloseReason.CloseCode code, String reason) {
        if (session == null || !session.isOpen()) return;
        try {
            session.close(new CloseReason(code, reason));
        } catch (IOException ignored) {}
    }

    // ---------- 静态访问（供业务模块使用） ----------

    public static boolean isClientOnline(String sessionId, Role role) {
        Optional<SessionRoom> roomOpt = roomRegistry.get(sessionId);
        if (roomOpt.isEmpty()) return false;
        return roomOpt.get().hasRole(role);
    }



// ★ 新增：业务模块（如 NpcUnityBridgeService）广播入口
    /**
     * 向指定房间所有连接广播一条消息。
     *
     * @param sessionId 房间会话 ID（即 /ws/session/{sessionId} 里的那个）
     * @param envelope  已封好的 {type,timestamp,data} 信封
     */
    public static void broadcastToSession(String sessionId, Map<String, Object> envelope) {
        if (broadcaster == null) {
            log.warn("⚠️ broadcaster 未注入，广播丢弃: sessionId={}, type={}",
                sessionId, envelope == null ? null : envelope.get("type"));
            return;
        }
        broadcaster.broadcast(sessionId, envelope);
    }

    /**
     * 向指定房间广播，但排除某个 clientId。
     */
    public static void broadcastToSessionExcept(String sessionId, String excludeClientId,
                                                Map<String, Object> envelope) {
        if (broadcaster == null) {
            log.warn("⚠️ broadcaster 未注入，广播丢弃: sessionId={}", sessionId);
            return;
        }
        broadcaster.broadcastExceptClient(sessionId, excludeClientId, envelope);
    }
}

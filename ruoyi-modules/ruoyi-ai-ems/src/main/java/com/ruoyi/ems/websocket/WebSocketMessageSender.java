package com.ruoyi.ems.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.ws.RoomBroadcaster;
import com.ruoyi.ems.room.ws.WsSessionRegistry;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 下行消息发送器。
 *
 * <p>★ 2026-09-22 改造（L-11 推送通道打通）：</p>
 * <ul>
 *   <li>注入 {@link WsSessionRegistry}，{@link #resolveRoomId(String)} 优先走
 *       {@link WsSessionRegistry#resolveToRoomSessionId(String)}（别名表），
 *       RoomRegistry 作为兜底。</li>
 *   <li>原因：`RoomRegistry.getByEmsSessionId` 未建立实时索引；而别名在
 *       start_case 时通过 `WsSessionRegistry.registerAlias` 实时写入，
 *       是唯一可靠的 emsSessionId → roomSessionId 映射来源。</li>
 * </ul>
 *
 * <p>★ 2026-09-23 诊断 patch（P1 切换后残留 WARN 定位）：</p>
 * <ul>
 *   <li>三处 {@code send*} 的 {@code 消息丢弃} WARN 加上 {@code [send-Object]} /
 *       {@code [send-type]} / {@code [sendToEngine]} 标签 + 调用栈</li>
 *   <li>{@link #sendTranscript(Session, String, boolean)} 和
 *       {@link #sendMessage(Session, String, Object)} 的 {@code 消息丢弃} WARN 加上
 *       {@code [sendTranscript]} / {@code [sendMessage]} 标签 + 调用栈</li>
 *   <li>目的：区分「sessionId 版失败」与「Session 版失败」，定位真正的调用方</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessageSender {

    private final ObjectMapper objectMapper;
    private final RoomRegistry roomRegistry;
    private final RoomBroadcaster roomBroadcaster;

    // ★ 新增：别名解析入口
    private final WsSessionRegistry wsSessionRegistry;

    // ==================== 广播（默认） ====================

    /**
     * 通用下行（默认广播）。
     *
     * <p>★ 修改：若 data 已是完整 envelope（含 "type" 字段），直接透传，
     * 否则包装为 engine_message。</p>
     */
    public void send(String sessionId, Object data) {
        String roomId = resolveRoomId(sessionId);
        if (roomId == null) {
            log.warn("会话 {} 不存在或已关闭，消息丢弃 [send-Object] sessionId='{}'",
                sessionId, sessionId, new Throwable("stack"));
            return;
        }

        if (data instanceof Map<?, ?> map && map.containsKey("type")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> env = (Map<String, Object>) map;
            log.debug("WS 发送(透传): type={}, room={}", env.get("type"), roomId);
            roomBroadcaster.broadcast(roomId, env);
        } else {
            Map<String, Object> env = RoomBroadcaster.envelope("engine_message", data);
            log.debug("WS 发送(wrap): type=engine_message, room={}", roomId);
            roomBroadcaster.broadcast(roomId, env);
        }
    }

    /**
     * 指定 type 的下行。语义化 type 供 Unity 端 switch 分派。
     */
    public void send(String sessionId, String type, Object data) {
        String roomId = resolveRoomId(sessionId);
        if (roomId == null) {
            log.warn("会话 {} 不存在或已关闭，消息丢弃 [send-type] sessionId='{}', type={}",
                sessionId, sessionId, type, new Throwable("stack"));
            return;
        }
        Map<String, Object> env = RoomBroadcaster.envelope(type, data);
        log.debug("WS 发送: type={}, 入参={}, room={}", type, sessionId, roomId);
        roomBroadcaster.broadcast(roomId, env);
    }

    /**
     * 只给引擎端（Unity 学员端）。状态变更、上下文更新用这个。
     */
    public void sendToEngine(String sessionId, String type, Object data) {
        String roomId = resolveRoomId(sessionId);
        if (roomId == null) {
            log.warn("会话 {} 不存在或已关闭，消息丢弃 [sendToEngine] sessionId='{}', type={}",
                sessionId, sessionId, type, new Throwable("stack"));
            return;
        }
        Map<String, Object> env = RoomBroadcaster.envelope(type, data);
        log.debug("WS 发送(engine): type={}, 入参={}, room={}", type, sessionId, roomId);
        roomBroadcaster.sendToRole(roomId, Role.ENGINE, env);
    }

    // ==================== ★ 解析入口（改造点） ====================

    /**
     * 把任意 key（emsSessionId 或 roomSessionId）解析为 roomSessionId。
     *
     * <p>解析顺序：</p>
     * <ol>
     *   <li><b>WsSessionRegistry 别名表</b>——start_case 时注册的
     *       emsSessionId → roomSessionId，实时可靠。</li>
     *   <li><b>RoomRegistry.exists</b>——传入的本身就是 roomSessionId 的兜底。</li>
     *   <li><b>RoomRegistry.getByEmsSessionId</b>——最后的兜底（索引可能滞后，仅作双保险）。</li>
     * </ol>
     *
     * <p>★ 诊断日志标记：</p>
     * <ul>
     *   <li>{@code [alias]}——别名表命中</li>
     *   <li>{@code [direct]}——传入的本身是 roomSessionId</li>
     *   <li>{@code [ems-index]}——通过 RoomRegistry 的 emsSessionId 索引反查命中</li>
     *   <li>{@code [miss]}——全部未命中，返回 null</li>
     * </ul>
     */
    private String resolveRoomId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.info("🔍 resolveRoomId[miss] 入参为空: sessionId='{}'", sessionId);
            return null;
        }

        // ★ 1. 优先走 WsSessionRegistry 的别名解析（start_case 时注册，最可靠）
        String resolved = wsSessionRegistry.resolveToRoomSessionId(sessionId);
        if (resolved != null) {
            log.info("🔍 resolveRoomId[alias] in={}, room={}", sessionId, resolved);
            return resolved;
        }

        // 2. 兜底：RoomRegistry 直接命中（传入的本身就是 roomSessionId）
        if (roomRegistry.exists(sessionId)) {
            log.info("🔍 resolveRoomId[direct] in={}", sessionId);
            return sessionId;
        }

        // 3. 最后兜底：RoomRegistry 按 emsSessionId 反查（索引可能滞后）
        Optional<SessionRoom> byEms = roomRegistry.getByEmsSessionId(sessionId);
        if (byEms.isPresent()) {
            String roomId = byEms.get().getSessionId();
            log.info("🔍 resolveRoomId[ems-index] in={}, room={}", sessionId, roomId);
            return roomId;
        }

        // 4. 全未命中：记录上下文辅助排查
        log.warn("❌ resolveRoomId[miss] in={}, aliasExists={}, roomExists={}",
            sessionId,
            wsSessionRegistry.resolveToRoomSessionId(sessionId),
            roomRegistry.exists(sessionId));
        return null;
    }

    // ==================== 直接接收 Session 的方法（诊断 patch） ====================

    public void sendTranscript(Session session, String text, boolean isFinal) {
        if (session == null || !session.isOpen()) {
            log.warn("会话不存在或已关闭，消息丢弃 [sendTranscript] sessionId={}, text='{}'",
                session == null ? "null-session" : session.getId(),
                text,
                new Throwable("stack"));
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                "type", "transcript",
                "text", text,
                "isFinal", isFinal,
                "timestamp", System.currentTimeMillis()
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(payload));
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败", e);
        }
    }

    public void sendMessage(Session session, String type, Object data) {
        if (session == null || !session.isOpen()) {
            log.warn("会话不存在或已关闭，消息丢弃 [sendMessage] sessionId={}, type={}",
                session == null ? "null-session" : session.getId(),
                type,
                new Throwable("stack"));
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                "type", type,
                "data", data != null ? data : "",
                "timestamp", System.currentTimeMillis()
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(payload));
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败", e);
        }
    }

    /**
     * ★ 替代 {@link #sendTranscript(Session, String, boolean)}。
     *
     * <p>用 emsSessionId 走 RoomBroadcaster 广播通道（P1 之后 Unity 端的唯一入口）。</p>
     *
     * <p>调用方：SessionService 应传 emsSessionId，不要传 jakarta Session 对象。</p>
     */
    public void sendTranscript(String emsSessionId, String text, boolean isFinal) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("text", text);
        payload.put("isFinal", isFinal);
        payload.put("timestamp", System.currentTimeMillis());
        send(emsSessionId, "transcript", payload);
    }
}
